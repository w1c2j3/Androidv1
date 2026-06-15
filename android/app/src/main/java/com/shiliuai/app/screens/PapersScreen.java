package com.shiliuai.app.screens;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.shiliuai.app.BuildConfig;
import com.shiliuai.app.ShellHost;
import com.shiliuai.app.net.Json;
import com.shiliuai.app.net.Poller;
import com.shiliuai.app.ui.Components;
import com.shiliuai.app.ui.Theme;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 论文屏：独立入口，调 /paper 命令。
 */
public final class PapersScreen implements Screen {

    private ShellHost host;
    private View root;
    private EditText topicInput;
    private TextView searchBtn;
    private LinearLayout results;
    private Poller poller;

    @Override public String title() { return "论文工作台"; }

    @Override
    public View onCreate(Context c, ShellHost host) {
        this.host = host;
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);

        LinearLayout col = Components.column(c);

        col.addView(Components.sectionHeader(c, "研究主题"));
        LinearLayout searchCard = Components.angularCard(c);
        topicInput = Components.input(c, "输入研究主题，例如：benchmark 评估大模型");
        searchCard.addView(topicInput);
        searchBtn = Components.primaryButton(c, "检索论文", this::search);
        searchCard.addView(searchBtn);
        col.addView(searchCard);

        col.addView(Components.sectionHeader(c, "论文候选"));
        results = Components.column(c);
        results.setPadding(0, 0, 0, 0);
        col.addView(results);

        scroll.addView(col);
        root = scroll;
        return root;
    }

    @Override
    public void onPause() {
        if (poller != null) poller.cancel();
    }

    private void search() {
        String topic = topicInput.getText().toString().trim();
        if (topic.isEmpty()) {
            host.setStatus("请输入研究主题", Components.ChipState.ERROR);
            return;
        }
        if (poller != null) poller.cancel();
        Components.setBusy(searchBtn, true, "检索中…");
        host.setStatus("论文检索 运行中", Components.ChipState.RUNNING);
        results.removeAllViews();
        results.addView(Theme.body(results.getContext(), "正在派发 /paper 指令…"));

        host.executor().execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("command", "/paper " + topic);
                body.put("projectPath", BuildConfig.SHILIU_DEFAULT_PROJECT_PATH);
                body.put("source", "android_app");
                body.put("saveTasks", false);
                // 走异步 submit：手机 → Cloudflare quick tunnel → 后端的 100s 响应窗口扛不住
                // 5 分钟级别的 LLM/论文检索。submit 立刻返回 runId，后续走 poll 拿结果。
                String runRaw = host.backend().request("POST", "/api/v1/agent/runs/submit", body.toString());
                JSONObject run = new JSONObject(runRaw);
                String runId = run.optString("runId", "");
                // 派发返回若已经包含 papers（同步完成），直接渲染。
                if (runId.isEmpty() || run.optJSONArray("papers") != null) {
                    host.activity().runOnUiThread(() -> {
                        Components.setBusy(searchBtn, false, null);
                        bindRaw(runRaw);
                    });
                    return;
                }
                final long startedAt = System.currentTimeMillis();
                host.activity().runOnUiThread(() -> {
                    // 把"正在派发"换成"已派发"，让用户立刻看到前端在动。
                    results.removeAllViews();
                    results.addView(Theme.body(results.getContext(),
                            "已派发 runId=" + runId + "，正在等待结果…"));
                    startPoll(runId, startedAt);
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(searchBtn, false, null);
                    host.setStatus("论文检索失败", Components.ChipState.ERROR);
                    results.removeAllViews();
                    results.addView(Components.monoBlock(results.getContext(), e.getMessage()));
                });
            }
        });
    }

    private void startPoll(String runId, long startedAt) {
        // 旧版 maxAttempts=8 仅 10 秒强制终止，导致 /paper 长任务永远停在"正在派发"。
        // 改为 1.5s × 120 = 3 分钟，并在 onProgress 把 status + 已等待秒数实时写回 UI。
        poller = new Poller(host.executor(), 1500L, 120);
        poller.start(new Poller.PollStep<String>() {
            @Override public String fetch() throws java.io.IOException {
                return host.backend().request("GET", "/api/v1/agent/runs/" + runId, null);
            }
            @Override public boolean isTerminal(String raw) {
                try {
                    JSONObject o = new JSONObject(raw);
                    if (Json.isTerminal(o.optString("status", ""))) return true;
                    // 后端可能返回 papers 但 status 仍是 running；只要拿到列表就视为终止。
                    return o.optJSONArray("papers") != null;
                } catch (Exception e) { return false; }
            }
            @Override public void onProgress(String raw, int attempt) {
                long elapsed = (System.currentTimeMillis() - startedAt) / 1000L;
                String status = "running";
                try { status = new JSONObject(raw).optString("status", "running"); }
                catch (Exception ignored) {}
                host.setStatus("论文检索 · " + status + " · " + elapsed + "s", Components.ChipState.RUNNING);
                // 同步刷新结果区，避免"完全没有任何更新"的体验。
                results.removeAllViews();
                results.addView(Theme.body(results.getContext(),
                        "runId=" + runId + " · " + status + " · 已等 " + elapsed + "s · 第 " + (attempt + 1) + " 次轮询"));
            }
            @Override public void onTerminal(String raw) {
                Components.setBusy(searchBtn, false, null);
                try {
                    JSONObject o = new JSONObject(raw);
                    String status = o.optString("status", "");
                    if (!Json.isTerminal(status) && o.optJSONArray("papers") == null) {
                        // maxAttempts 用尽但仍未完成：明确提示超时。
                        host.setStatus("论文检索 超时 · " + status, Components.ChipState.ERROR);
                        results.removeAllViews();
                        results.addView(Components.monoBlock(results.getContext(),
                                "等待超时（>180s）。runId=" + runId + " 仍处于 " + status + "。"
                                        + "\n可点击 \"检索论文\" 重试或稍后再来。"));
                        return;
                    }
                } catch (Exception ignored) {}
                bindRaw(raw);
            }
            @Override public void onError(java.io.IOException e) {
                Components.setBusy(searchBtn, false, null);
                host.setStatus("论文检索 异常", Components.ChipState.ERROR);
                results.removeAllViews();
                results.addView(Components.monoBlock(results.getContext(), e.getMessage()));
            }
        });
    }

    private void bindRaw(String raw) {
        results.removeAllViews();
        Context c = results.getContext();
        try {
            JSONObject o = new JSONObject(raw);
            JSONArray papers = o.optJSONArray("papers");
            if (papers == null || papers.length() == 0) {
                results.addView(Theme.body(c, "没有返回论文候选。"));
                host.setStatus("论文检索 空", Components.ChipState.READY);
                return;
            }
            for (int i = 0; i < papers.length(); i++) {
                JSONObject p = papers.optJSONObject(i);
                if (p == null) continue;
                LinearLayout card = Components.angularCard(c);
                card.addView(Theme.title(c, Json.string(p, "title", "未命名论文")));
                String year = p.optString("year", "");
                String venue = p.optString("venue", "");
                if (!year.isEmpty() || !venue.isEmpty()) {
                    card.addView(Theme.meta(c, year + (year.isEmpty() || venue.isEmpty() ? "" : " · ") + venue));
                }
                String url = p.optString("url", "");
                if (!url.isEmpty()) card.addView(Components.monoBlock(c, url));
                String why = p.optString("whyRelevant", "");
                if (!why.isEmpty()) card.addView(Theme.body(c, "相关性：" + why));
                results.addView(card);
            }
            host.setStatus("论文检索 " + papers.length() + " 篇", Components.ChipState.OK);
        } catch (Exception e) {
            results.addView(Components.monoBlock(c, Json.pretty(raw)));
            host.setStatus("论文解析失败", Components.ChipState.ERROR);
        }
    }
}
