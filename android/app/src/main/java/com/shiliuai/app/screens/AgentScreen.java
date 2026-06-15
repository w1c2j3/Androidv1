package com.shiliuai.app.screens;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.shiliuai.app.ShellHost;
import com.shiliuai.app.net.Poller;
import com.shiliuai.app.ui.Components;
import com.shiliuai.app.ui.Theme;

import org.json.JSONObject;

import java.io.IOException;

/**
 * Agent · Codex 控制台。
 *
 * 真实调用后端 POST /api/v1/codex/run，后端 shell out 到 `codex exec --json`。
 *
 * 设计目标：
 *  - 让团队成员在手机上把项目级 prompt 真实交给 codex（read-only 默认）。
 *  - 显示运行时间、状态、输出。
 *  - 写权限通过 "允许写文件" 开关切换为 workspace-write，避免误触。
 */
public final class AgentScreen implements Screen {

    private ShellHost host;
    private View root;
    /**
     * Cloudflare quick tunnel 的硬性响应窗口约 100 秒，所以同步等 codex 必然 524。
     * 这里走 submit + poll：submit 1~2 秒返回，poll 间隔 3 秒，最多 120 次 ≈ 6 分钟。
     */
    private static final long POLL_INTERVAL_MS = 3_000L;
    private static final int POLL_MAX_ATTEMPTS = 120;

    private EditText promptInput;
    private TextView runBtn;
    private TextView writeToggle;
    private boolean writeMode = false;

    private LinearLayout resultHolder;
    private TextView statusLine;
    private Poller poller;
    private String currentRunId;
    private long currentRunStartedAtMs;

    @Override public String title() { return "Codex Agent · 命令台"; }

    @Override
    public View onCreate(Context c, ShellHost host) {
        this.host = host;
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);

        LinearLayout col = Components.column(c);

        // ===== 说明 =====
        col.addView(Components.sectionHeader(c, "Codex 真实调用"));
        LinearLayout intro = Components.angularCard(c);
        intro.addView(Theme.body(c,
                "把指令交给真实的 codex CLI。后端会执行 `codex exec --json` 并把模型输出实时收集回来。"));
        intro.addView(Components.metaLine(c,
                "默认 read-only 沙箱：只读分析、问答、风险扫描，不改文件。\n"
                        + "如需让 codex 修改项目文件，请打开下方「允许写文件」。"));
        col.addView(intro);

        // ===== 输入 =====
        col.addView(Components.sectionHeader(c, "指令"));
        LinearLayout inputCard = Components.angularCard(c);
        promptInput = Components.input(c,
                "例如：扫描 backend/src/main/java/com/shiliuai/service/feishu 目录，找出可能的并发安全问题");
        promptInput.setMinLines(4);
        inputCard.addView(promptInput);

        LinearLayout row = Components.row(c);
        writeToggle = Components.ghostButton(c, "允许写文件：关", this::toggleWrite);
        row.addView(writeToggle);
        runBtn = Components.primaryButton(c, "运行 Codex", this::runCodex);
        row.addView(runBtn);
        inputCard.addView(row);

        col.addView(inputCard);

        // ===== 状态 + 结果 =====
        LinearLayout headerRow = Components.row(c);
        headerRow.addView(Theme.section(c, "运行结果"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusLine = Components.statusChip(c, "待运行", Components.ChipState.READY);
        headerRow.addView(statusLine);
        col.addView(headerRow);

        resultHolder = Components.column(c);
        resultHolder.setPadding(0, 0, 0, 0);
        resultHolder.addView(emptyCard(c, "尚未运行。在上方输入指令后点击「运行 Codex」。"));
        col.addView(resultHolder);

        scroll.addView(col);
        root = scroll;
        return root;
    }

    private LinearLayout emptyCard(Context c, String text) {
        LinearLayout card = Components.angularCard(c);
        card.addView(Theme.body(c, text));
        return card;
    }

    private void toggleWrite() {
        writeMode = !writeMode;
        writeToggle.setText(writeMode ? "允许写文件：开" : "允许写文件：关");
        host.setStatus(writeMode ? "Codex 切换至 workspace-write" : "Codex 切换至 read-only",
                writeMode ? Components.ChipState.RUNNING : Components.ChipState.READY);
    }

    private void runCodex() {
        String prompt = promptInput.getText().toString().trim();
        if (prompt.isEmpty()) {
            host.setStatus("请先输入指令", Components.ChipState.ERROR);
            return;
        }
        if (poller != null) poller.cancel();
        Components.setBusy(runBtn, true, "运行中…");
        updateStatus("提交中", Components.ChipState.RUNNING);

        Context c = root.getContext();
        resultHolder.removeAllViews();
        resultHolder.addView(emptyCard(c, "Codex 提交到后台…\nCloudflare quick tunnel 单次响应窗口约 100 秒，因此这里走 submit + poll，不会再触发 524。"));

        host.executor().execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("prompt", prompt);
                body.put("sandbox", writeMode ? "workspace-write" : "read-only");
                body.put("source", "android");
                String raw = host.backend().request("POST", "/api/v1/codex/submit", body.toString());
                JSONObject submitResponse = new JSONObject(raw);
                String runId = submitResponse.optString("runId", "");
                if (runId.isEmpty()) {
                    host.activity().runOnUiThread(() -> {
                        Components.setBusy(runBtn, false, null);
                        updateStatus("提交失败", Components.ChipState.ERROR);
                        resultHolder.removeAllViews();
                        LinearLayout card = Components.angularCard(c);
                        card.addView(Theme.body(c, "后端未返回 runId：\n" + raw));
                        resultHolder.addView(card);
                    });
                    return;
                }
                host.activity().runOnUiThread(() -> {
                    currentRunId = runId;
                    currentRunStartedAtMs = System.currentTimeMillis();
                    updateStatus("running", Components.ChipState.RUNNING);
                    pollRun(c, runId);
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(runBtn, false, null);
                    updateStatus("提交失败", Components.ChipState.ERROR);
                    resultHolder.removeAllViews();
                    LinearLayout card = Components.angularCard(c);
                    card.addView(Theme.body(c, "Codex 提交失败：" + e.getMessage()));
                    resultHolder.addView(card);
                    host.setStatus("Codex 调用失败", Components.ChipState.ERROR);
                });
            }
        });
    }

    private void pollRun(Context c, String runId) {
        poller = new Poller(host.executor(), POLL_INTERVAL_MS, POLL_MAX_ATTEMPTS);
        poller.start(new Poller.PollStep<String>() {
            @Override public String fetch() throws IOException {
                return host.backend().request("GET", "/api/v1/codex/runs/" + runId, null);
            }
            @Override public boolean isTerminal(String raw) {
                try {
                    String status = new JSONObject(raw).optString("status", "");
                    return !"running".equalsIgnoreCase(status);
                } catch (Exception e) { return false; }
            }
            @Override public void onProgress(String raw, int attempt) {
                long elapsed = (System.currentTimeMillis() - currentRunStartedAtMs) / 1000;
                updateStatus("running · " + elapsed + "s", Components.ChipState.RUNNING);
            }
            @Override public void onTerminal(String raw) {
                Components.setBusy(runBtn, false, null);
                try {
                    bindResult(c, new JSONObject(raw));
                } catch (Exception e) {
                    updateStatus("解析失败", Components.ChipState.ERROR);
                    resultHolder.removeAllViews();
                    LinearLayout card = Components.angularCard(c);
                    card.addView(Theme.body(c, "Codex 结果解析失败：" + e.getMessage()));
                    resultHolder.addView(card);
                }
            }
            @Override public void onError(IOException e) {
                Components.setBusy(runBtn, false, null);
                updateStatus("轮询失败", Components.ChipState.ERROR);
                resultHolder.removeAllViews();
                LinearLayout card = Components.angularCard(c);
                card.addView(Theme.body(c, "Codex 状态查询失败：" + e.getMessage()
                        + "\n可以稍后重试或在飞书上跑 /codex。"));
                resultHolder.addView(card);
                host.setStatus("Codex 调用失败", Components.ChipState.ERROR);
            }
        });
    }

    @Override
    public void onPause() { if (poller != null) poller.cancel(); }

    @Override
    public void onDestroy() { if (poller != null) poller.cancel(); }

    private void bindResult(Context c, JSONObject o) {
        String status = o.optString("status", "unknown");
        String runId = o.optString("runId", "");
        String summary = o.optString("summary", "(无输出)");
        long durationMs = o.optLong("durationMs", 0L);
        int exitCode = o.optInt("exitCode", -1);
        String stderrTail = o.optString("stderrTail", "");

        Components.ChipState chip;
        switch (status) {
            case "done":     chip = Components.ChipState.OK; break;
            case "failed":   chip = Components.ChipState.ERROR; break;
            case "timeout":  chip = Components.ChipState.ERROR; break;
            case "disabled": chip = Components.ChipState.READY; break;
            default:         chip = Components.ChipState.RUNNING;
        }
        updateStatus(status, chip);

        resultHolder.removeAllViews();

        // Meta card：runId / 耗时 / 退出码 / sandbox
        LinearLayout metaCard = Components.angularCard(c);
        metaCard.addView(Components.titleWithBadge(c, "运行编号", Components.ownerBadge(c,
                runId.isEmpty() ? "?" : runId.substring(runId.length() - 1).toUpperCase())));
        metaCard.addView(Components.monoBlock(c, runId.isEmpty() ? "(空)" : runId));
        metaCard.addView(Components.hairline(c), Components.hairlineLp(c));
        StringBuilder meta = new StringBuilder();
        meta.append("状态 ").append(status);
        if (durationMs > 0) meta.append("  ｜  耗时 ").append(durationMs).append(" ms");
        if (exitCode != -1) meta.append("  ｜  exit ").append(exitCode);
        meta.append("  ｜  sandbox ").append(o.optString("sandbox", ""));
        metaCard.addView(Components.metaLine(c, meta.toString()));
        resultHolder.addView(metaCard);

        // Summary card
        LinearLayout summaryCard = Components.angularCard(c);
        summaryCard.addView(Theme.section(c, "Codex 输出"));
        summaryCard.addView(Components.monoBlock(c, summary));
        resultHolder.addView(summaryCard);

        // stderr only when failure
        if (!stderrTail.isEmpty() && !"done".equals(status)) {
            LinearLayout errCard = Components.angularCard(c);
            errCard.addView(Theme.section(c, "stderr 尾部"));
            errCard.addView(Components.monoBlock(c, stderrTail));
            resultHolder.addView(errCard);
        }

        host.setStatus("Codex · " + status, chip);
    }

    private void updateStatus(String label, Components.ChipState state) {
        TextView fresh = Components.statusChip(statusLine.getContext(), label, state);
        statusLine.setText(fresh.getText());
        statusLine.setTextColor(fresh.getCurrentTextColor());
        statusLine.setBackground(fresh.getBackground());
    }
}
