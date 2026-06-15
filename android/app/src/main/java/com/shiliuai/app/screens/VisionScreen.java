package com.shiliuai.app.screens;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.shiliuai.app.ShellHost;
import com.shiliuai.app.net.Json;
import com.shiliuai.app.net.Poller;
import com.shiliuai.app.ui.Components;
import com.shiliuai.app.ui.MotionUtil;
import com.shiliuai.app.ui.Theme;

import org.json.JSONObject;

import java.io.IOException;

/**
 * 识图屏：选择图片 → 上传 → OCR → 结构化抽取 → 任务/日报操作。
 */
public final class VisionScreen implements Screen {

    private ShellHost host;
    private View root;

    private TextView phaseUpload, phaseOcr, phaseExtract;
    private View chevron;
    private TextView pickBtn, reextractBtn, saveTasksBtn, makeReportBtn;
    private TextView rawOcr, structured, tasksLine, materialsLine;

    private String currentTraceId;
    private Poller poller;

    @Override public String title() { return "识图工作台"; }

    @Override
    public View onCreate(Context c, ShellHost host) {
        this.host = host;

        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);

        LinearLayout col = Components.column(c);

        col.addView(Components.sectionHeader(c, "图片输入"));
        LinearLayout pickCard = Components.angularCard(c);
        TextView pickHint = Theme.body(c, "选择手机相册图片后，会依次走上传、OCR、结构化抽取三个阶段。失败时显示原始返回，便于现场定位。");
        pickCard.addView(pickHint);
        pickBtn = Components.primaryButton(c, "选择图片并开始识别", this::pickImage);
        pickCard.addView(pickBtn);
        col.addView(pickCard);

        col.addView(Components.sectionHeader(c, "流水线阶段"));
        LinearLayout phaseCard = Components.angularCard(c);

        phaseUpload = phaseLine(c, "上传");
        phaseOcr = phaseLine(c, "OCR");
        phaseExtract = phaseLine(c, "结构化");
        phaseCard.addView(phaseUpload);
        phaseCard.addView(phaseOcr);
        phaseCard.addView(phaseExtract);

        chevron = Components.chevronStrip(c);
        phaseCard.addView(chevron, Components.matchHeight(Theme.dp(c, 6)));
        col.addView(phaseCard);

        col.addView(Components.sectionHeader(c, "OCR 原文"));
        rawOcr = Components.monoBlock(c, "等待图片");
        col.addView(rawOcr);

        col.addView(Components.sectionHeader(c, "结构化结果"));
        LinearLayout structuredCard = Components.angularCard(c);
        structured = Theme.body(c, "等待 OCR 完成");
        structuredCard.addView(structured);
        tasksLine = Theme.meta(c, "任务候选：—");
        structuredCard.addView(tasksLine);
        materialsLine = Theme.meta(c, "日报素材：—");
        structuredCard.addView(materialsLine);
        col.addView(structuredCard);

        col.addView(Components.sectionHeader(c, "结果操作"));
        LinearLayout opsRow = Components.angularCard(c, Theme.alert(c), true);
        saveTasksBtn = Components.ghostButton(c, "保存任务候选", this::saveTasks);
        makeReportBtn = Components.ghostButton(c, "生成日报素材", this::makeReport);
        reextractBtn = Components.ghostButton(c, "用 LLM 重新抽取", this::reextract);
        opsRow.addView(saveTasksBtn);
        opsRow.addView(makeReportBtn);
        opsRow.addView(reextractBtn);
        setOpsEnabled(false);
        col.addView(opsRow);

        scroll.addView(col);
        root = scroll;
        MotionUtil.slideIn(pickCard, 0);
        MotionUtil.slideIn(phaseCard, 40);
        return root;
    }

    private TextView phaseLine(Context c, String label) {
        TextView v = Theme.body(c, "○ " + label + " · 待命");
        v.setTextColor(Theme.textMute(c));
        v.setTypeface(Theme.fontDisplay());
        return v;
    }

    @Override
    public void onPause() {
        if (poller != null) poller.cancel();
        Components.setChevronRunning(chevron, false);
    }

    @Override public void onDestroy() { if (poller != null) poller.cancel(); }

    private void pickImage() {
        host.pickImage(this::onImagePicked);
    }

    private void onImagePicked(Uri uri) {
        if (poller != null) poller.cancel();
        currentTraceId = null;
        setOpsEnabled(false);
        rawOcr.setText("等待上传");
        structured.setText("等待 OCR");
        tasksLine.setText("任务候选：—");
        materialsLine.setText("日报素材：—");

        markPhase(phaseUpload, "上传", PhaseState.RUNNING);
        markPhase(phaseOcr, "OCR", PhaseState.WAITING);
        markPhase(phaseExtract, "结构化", PhaseState.WAITING);
        Components.setChevronRunning(chevron, true);
        Components.setBusy(pickBtn, true, "处理中…");
        host.setStatus("识图 运行中", Components.ChipState.RUNNING);

        host.executor().execute(() -> {
            try {
                String uploadRaw = host.backend().uploadMultipart(host.activity(), "/api/v1/vision/upload?source=android_app&sceneHint=auto", uri);
                JSONObject upload = new JSONObject(uploadRaw);
                String traceId = upload.optString("traceId", "");
                host.activity().runOnUiThread(() -> {
                    markPhase(phaseUpload, "上传", PhaseState.DONE);
                    if (traceId.isEmpty()) {
                        Components.setBusy(pickBtn, false, null);
                        Components.setChevronRunning(chevron, false);
                        host.setStatus("识图 异常 · 未返回 traceId", Components.ChipState.ERROR);
                        rawOcr.setText(Json.pretty(uploadRaw));
                        return;
                    }
                    currentTraceId = traceId;
                    markPhase(phaseOcr, "OCR " + traceId, PhaseState.RUNNING);
                    startPollVision(traceId);
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(pickBtn, false, null);
                    Components.setChevronRunning(chevron, false);
                    markPhase(phaseUpload, "上传 异常", PhaseState.ERROR);
                    host.setStatus("识图 上传失败", Components.ChipState.ERROR);
                    rawOcr.setText(e.getMessage());
                });
            }
        });
    }

    private void startPollVision(String traceId) {
        // OCR 后端处理可能 30-90 秒；旧版 maxAttempts=8、间隔 1.2s 仅约 10 秒就强制终止，
        // 触发"卡在 processing"的假象。改为 1.5s × 120 = 3 分钟窗口，且每次轮询把
        // status + 已等待秒数 写回 UI，让用户看到前端确实在动。
        final long startedAt = System.currentTimeMillis();
        poller = new Poller(host.executor(), 1500L, 120);
        poller.start(new Poller.PollStep<String>() {
            @Override public String fetch() throws IOException {
                return host.backend().request("GET", "/api/v1/vision/results/" + traceId, null);
            }
            @Override public boolean isTerminal(String raw) {
                try { return Json.isTerminal(new JSONObject(raw).optString("status", "")); }
                catch (Exception e) { return false; }
            }
            @Override public void onProgress(String raw, int attempt) {
                long elapsed = (System.currentTimeMillis() - startedAt) / 1000L;
                String status = "processing";
                try { status = new JSONObject(raw).optString("status", "processing"); }
                catch (Exception ignored) {}
                markPhase(phaseOcr, "OCR · " + status + " · " + elapsed + "s · #" + (attempt + 1), PhaseState.RUNNING);
                // 同步把当前原始返回写到 OCR 原文区，避免"前端没动"
                rawOcr.setText(Json.pretty(raw));
            }
            @Override public void onTerminal(String raw) {
                // 区分真正 done/error 与 maxAttempts 超时：超时仍把当前 raw 作为最后状态展示
                try {
                    String status = new JSONObject(raw).optString("status", "");
                    if (!Json.isTerminal(status)) {
                        Components.setBusy(pickBtn, false, null);
                        Components.setChevronRunning(chevron, false);
                        markPhase(phaseOcr, "OCR · 超时 · " + status, PhaseState.ERROR);
                        host.setStatus("识图 超时 · 后端仍在处理，可稍后重试", Components.ChipState.ERROR);
                        rawOcr.setText(Json.pretty(raw));
                        return;
                    }
                } catch (Exception ignored) {}
                applyVisionResult(raw);
            }
            @Override public void onError(IOException e) {
                Components.setBusy(pickBtn, false, null);
                Components.setChevronRunning(chevron, false);
                markPhase(phaseOcr, "OCR 异常", PhaseState.ERROR);
                host.setStatus("识图 OCR 失败", Components.ChipState.ERROR);
                rawOcr.setText(e.getMessage());
            }
        });
    }

    private void applyVisionResult(String raw) {
        Components.setBusy(pickBtn, false, null);
        Components.setChevronRunning(chevron, false);
        try {
            JSONObject o = new JSONObject(raw);
            String status = o.optString("status", "");
            // 后端 VisionPipelineService 终止状态只有 done / error 两种；
            // 旧代码里的 success 是历史残留，永远不会触发，直接删掉避免误导阅读者。
            boolean ok = "done".equalsIgnoreCase(status);
            markPhase(phaseOcr, "OCR · " + status, ok ? PhaseState.DONE : PhaseState.ERROR);
            markPhase(phaseExtract, "结构化 · " + status, ok ? PhaseState.DONE : PhaseState.ERROR);
            host.setStatus(ok ? "识图 完成" : "识图 异常 · " + status, ok ? Components.ChipState.OK : Components.ChipState.ERROR);
            rawOcr.setText(Json.ocrPlainText(o));
            JSONObject summary = o.optJSONObject("summary");
            if (summary != null) {
                structured.setText(summary.optString("title", "") + "\n" + summary.optJSONArray("bullets"));
            } else {
                structured.setText(Json.pretty(raw));
            }
            int tasksCount = o.optJSONArray("tasks") == null ? 0 : o.optJSONArray("tasks").length();
            int matCount = o.optJSONArray("dailyReportMaterials") == null ? 0 : o.optJSONArray("dailyReportMaterials").length();
            tasksLine.setText("任务候选：" + tasksCount + " 条");
            materialsLine.setText("日报素材：" + matCount + " 条");
            setOpsEnabled(ok);
        } catch (Exception e) {
            rawOcr.setText(raw);
            structured.setText("解析失败：" + e.getMessage());
            host.setStatus("识图 解析失败", Components.ChipState.ERROR);
        }
    }

    private void saveTasks() {
        if (currentTraceId == null) return;
        Components.setBusy(saveTasksBtn, true, "保存中…");
        host.executor().execute(() -> {
            try {
                String raw = host.backend().request("POST", "/api/v1/tasks/from-trace/" + currentTraceId, "{}");
                JSONObject o = new JSONObject(raw);
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(saveTasksBtn, false, null);
                    int savedCount = o.optInt("savedCount", 0);
                    String skippedReason = o.optString("skippedReason", "");
                    if (savedCount == 0 && !skippedReason.isEmpty()) {
                        // 后端拒绝保存空候选时（OCR 失败 / 没识别到任务），明确告诉用户，
                        // 不再像旧版那样静默"已保存 0 个任务"，配合不再创建占位任务的后端策略。
                        host.setStatus("未创建任务 · " + skippedReason, Components.ChipState.READY);
                    } else {
                        host.setStatus("已保存 " + savedCount + " 个任务", Components.ChipState.OK);
                    }
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(saveTasksBtn, false, null);
                    host.setStatus("保存任务失败", Components.ChipState.ERROR);
                    structured.setText(e.getMessage());
                });
            }
        });
    }

    private void makeReport() {
        if (currentTraceId == null) return;
        Components.setBusy(makeReportBtn, true, "生成中…");
        host.executor().execute(() -> {
            try {
                String raw = host.backend().request("GET", "/api/v1/vision/results/" + currentTraceId, null);
                String report = Json.dailyReport(new JSONObject(raw));
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(makeReportBtn, false, null);
                    structured.setText(report);
                    host.setStatus("日报素材已生成", Components.ChipState.OK);
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(makeReportBtn, false, null);
                    host.setStatus("生成日报失败", Components.ChipState.ERROR);
                });
            }
        });
    }

    private void reextract() {
        if (currentTraceId == null) return;
        Components.setBusy(reextractBtn, true, "重抽取…");
        // 改走专用 reextract 端点：后端直接基于持久化的 OCR JSON 重跑 ExtractService（@Primary 是 LLM 抽取），
        // 不再借道 /agent/runs + /digest。旧路径把上一轮错误 summary 也喂进 LLM，模型会把 "Codex 524"
        // 之类的错误文本当成正文继续总结，输出彻底跑偏。新端点输入只有 OCR 文本，结果整洁。
        host.executor().execute(() -> {
            try {
                String raw = host.backend().request("POST",
                        "/api/v1/vision/results/" + currentTraceId + "/reextract", "{}");
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(reextractBtn, false, null);
                    applyVisionResult(raw);
                    host.setStatus("LLM 重抽取完成", Components.ChipState.OK);
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(reextractBtn, false, null);
                    host.setStatus("LLM 抽取失败", Components.ChipState.ERROR);
                    structured.setText(e.getMessage());
                });
            }
        });
    }

    private void setOpsEnabled(boolean enabled) {
        saveTasksBtn.setEnabled(enabled);
        makeReportBtn.setEnabled(enabled);
        reextractBtn.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.4f;
        saveTasksBtn.setAlpha(alpha);
        makeReportBtn.setAlpha(alpha);
        reextractBtn.setAlpha(alpha);
    }

    private enum PhaseState { WAITING, RUNNING, DONE, ERROR }

    private void markPhase(TextView line, String label, PhaseState state) {
        Context c = line.getContext();
        switch (state) {
            case WAITING:
                line.setText("○ " + label + " · 待命");
                line.setTextColor(Theme.textMute(c));
                break;
            case RUNNING:
                line.setText("◐ " + label);
                line.setTextColor(Theme.alert(c));
                break;
            case DONE:
                line.setText("● " + label);
                line.setTextColor(Theme.ok(c));
                break;
            case ERROR:
                line.setText("✕ " + label);
                line.setTextColor(Theme.danger(c));
                break;
        }
    }
}
