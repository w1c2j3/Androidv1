package com.shiliuai.app.screens;

import android.content.Context;
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
 * 总览屏：后端 / OCR / LLM / 飞书四类状态、当前后端地址、立即体检。
 */
public final class HomeScreen implements Screen {

    private ShellHost host;
    private View root;

    private TextView backendUrlView;
    private TextView backendChip;
    private TextView ocrChip;
    private TextView llmChip;
    private TextView feishuChip;
    private TextView todayTasksView;
    private TextView todayTracesView;
    private TextView recentErrorView;
    private TextView runBtn;
    private View chevron;
    private long lastRunAt = 0L;

    private Poller poller;

    @Override public String title() { return "视流总览"; }

    @Override
    public View onCreate(Context c, ShellHost host) {
        this.host = host;

        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);

        LinearLayout col = Components.column(c);

        col.addView(Components.sectionHeader(c, "系统当前位置"));

        LinearLayout heroCard = Components.angularCard(c);
        TextView hero = Theme.display(c, "视流 AI · 移动工作台");
        heroCard.addView(hero);
        TextView heroSub = Theme.body(c, "OCR 截图 · 任务 · 论文 · 飞书 全链路调度。点右下角立即体检确认后端联通。");
        heroCard.addView(heroSub);

        backendUrlView = Components.monoBlock(c, host.backend().baseUrl());
        heroCard.addView(backendUrlView);

        LinearLayout chipsRow = Components.row(c);
        backendChip = Components.statusChip(c, "后端", Components.ChipState.READY);
        ocrChip = Components.statusChip(c, "OCR", Components.ChipState.READY);
        llmChip = Components.statusChip(c, "LLM", Components.ChipState.READY);
        feishuChip = Components.statusChip(c, "飞书", Components.ChipState.READY);
        chipsRow.addView(backendChip);
        chipsRow.addView(ocrChip);
        chipsRow.addView(llmChip);
        chipsRow.addView(feishuChip);
        heroCard.addView(chipsRow);

        col.addView(heroCard);

        col.addView(Components.sectionHeader(c, "今日数据"));
        LinearLayout kpiRow = Components.row(c);
        kpiRow.addView(kpiCard(c, "任务", "—", v -> todayTasksView = v));
        kpiRow.addView(kpiCard(c, "OCR", "—", v -> todayTracesView = v));
        kpiRow.addView(kpiCard(c, "异常", "—", v -> recentErrorView = v));
        col.addView(kpiRow);

        col.addView(Components.sectionHeader(c, "体检控制台"));
        LinearLayout opsCard = Components.angularCard(c);

        chevron = Components.chevronStrip(c);
        opsCard.addView(chevron, Components.matchHeight(Theme.dp(c, 6)));

        runBtn = Components.primaryButton(c, "立即体检后端", this::runDiagnostic);
        opsCard.addView(runBtn);

        TextView hint = Theme.meta(c, "失败时此处显示 HTTP 状态码和后端返回原文，不再用 Toast 一闪而过。");
        opsCard.addView(hint);

        col.addView(opsCard);

        scroll.addView(col);
        root = scroll;

        MotionUtil.slideIn(heroCard, 0);
        MotionUtil.slideIn(kpiRow, 40);
        MotionUtil.slideIn(opsCard, 80);

        return root;
    }

    private LinearLayout kpiCard(Context c, String label, String value, java.util.function.Consumer<TextView> bind) {
        LinearLayout card = Components.angularCard(c, Theme.signal(c), false);
        TextView meta = Theme.meta(c, label);
        meta.setTextColor(Theme.textDim(c));
        TextView big = Theme.display(c, value);
        big.setTextColor(Theme.signal(c));
        card.addView(meta);
        card.addView(big);
        bind.accept(big);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        card.setLayoutParams(lp);
        return card;
    }

    @Override
    public void onResume() {
        long now = System.currentTimeMillis();
        if (now - lastRunAt > 30_000L) {
            runDiagnostic();
        }
    }

    @Override
    public void onPause() {
        if (poller != null) poller.cancel();
        Components.setChevronRunning(chevron, false);
    }

    @Override
    public void onDestroy() {
        if (poller != null) poller.cancel();
    }

    private void runDiagnostic() {
        if (poller != null) poller.cancel();
        lastRunAt = System.currentTimeMillis();
        Components.setBusy(runBtn, true, "体检中…");
        Components.setChevronRunning(chevron, true);
        host.setStatus("后端体检 运行中", Components.ChipState.RUNNING);
        setChip(backendChip, "后端", Components.ChipState.RUNNING);
        setChip(ocrChip, "OCR", Components.ChipState.RUNNING);
        setChip(llmChip, "LLM", Components.ChipState.RUNNING);
        setChip(feishuChip, "飞书", Components.ChipState.RUNNING);

        poller = new Poller(host.executor(), 0L, 0);
        poller.start(new Poller.PollStep<DiagnosticResult>() {
            @Override public DiagnosticResult fetch() throws IOException {
                DiagnosticResult r = new DiagnosticResult();
                r.health = host.backend().request("GET", "/api/v1/health", null);
                r.readiness = host.backend().request("GET", "/api/v1/setup/readiness", null);
                r.queues = host.backend().request("GET", "/api/v1/setup/queues", null);
                r.overview = host.backend().request("GET", "/api/v1/workbench/overview", null);
                return r;
            }
            @Override public boolean isTerminal(DiagnosticResult result) { return true; }
            @Override public void onProgress(DiagnosticResult partial, int attempt) {}
            @Override public void onTerminal(DiagnosticResult r) { applyDiagnostic(r); }
            @Override public void onError(IOException e) {
                Components.setBusy(runBtn, false, null);
                Components.setChevronRunning(chevron, false);
                host.setStatus("后端体检 异常", Components.ChipState.ERROR);
                setChip(backendChip, "后端", Components.ChipState.ERROR);
                setChip(ocrChip, "OCR", Components.ChipState.READY);
                setChip(llmChip, "LLM", Components.ChipState.READY);
                setChip(feishuChip, "飞书", Components.ChipState.READY);
                recentErrorView.setText("ERR");
                recentErrorView.setTextColor(Theme.danger(recentErrorView.getContext()));
                backendUrlView.setText(host.backend().baseUrl() + "\n\n" + e.getMessage());
            }
        });
    }

    private void applyDiagnostic(DiagnosticResult r) {
        Components.setBusy(runBtn, false, null);
        Components.setChevronRunning(chevron, false);
        host.setStatus("后端体检 完成", Components.ChipState.OK);
        setChip(backendChip, "后端", Components.ChipState.OK);

        try {
            JSONObject readiness = new JSONObject(r.readiness);
            setHealthChip(ocrChip, "OCR", readiness.optBoolean("ocrHealthy", false), readiness.optBoolean("ocrConfigured", false));
            setHealthChip(llmChip, "LLM", readiness.optBoolean("llmHealthy", false), readiness.optBoolean("llmConfigured", false));
            // 修复旧 Bug：飞书 chip 「健康」字段错用了 botRegistered，导致只要注册过就一直显示绿色。
            // 真实健康定义 = tokenValid（App ID/Secret 能换 tenant_access_token）
            //               && eventCallbackVerified（飞书事件回调地址已通过验证）。
            // botRegistered 只决定是否「已配置」，对应灰色 vs 绿色。
            boolean feishuConfigured = readiness.optBoolean("botRegistered", false);
            boolean feishuHealthy = readiness.optBoolean("tokenValid", false)
                    && readiness.optBoolean("eventCallbackVerified", false);
            setHealthChip(feishuChip, "飞书", feishuHealthy, feishuConfigured);
        } catch (Exception e) {
            setChip(ocrChip, "OCR", Components.ChipState.READY);
            setChip(llmChip, "LLM", Components.ChipState.READY);
            setChip(feishuChip, "飞书", Components.ChipState.READY);
        }

        try {
            JSONObject overview = new JSONObject(r.overview);
            todayTasksView.setText(String.valueOf(overview.optLong("todayCreatedTaskCount", 0)));
            todayTracesView.setText(String.valueOf(overview.optLong("todayTraceCount", 0)));
            long errors = overview.optLong("todayErrorTraceCount", 0);
            recentErrorView.setText(String.valueOf(errors));
            recentErrorView.setTextColor(errors == 0 ? Theme.ok(recentErrorView.getContext()) : Theme.danger(recentErrorView.getContext()));
        } catch (Exception e) {
            todayTasksView.setText("—");
            todayTracesView.setText("—");
            recentErrorView.setText("—");
        }
    }

    private void setHealthChip(TextView chip, String label, boolean healthy, boolean configured) {
        if (!configured) {
            setChip(chip, label, Components.ChipState.READY);
            return;
        }
        setChip(chip, label, healthy ? Components.ChipState.OK : Components.ChipState.ERROR);
    }

    private void setChip(TextView chip, CharSequence label, Components.ChipState state) {
        TextView fresh = Components.statusChip(chip.getContext(), label, state);
        chip.setText(fresh.getText());
        chip.setTextColor(fresh.getCurrentTextColor());
        chip.setBackground(fresh.getBackground());
    }

    private static final class DiagnosticResult {
        String health, readiness, queues, overview;
    }
}
