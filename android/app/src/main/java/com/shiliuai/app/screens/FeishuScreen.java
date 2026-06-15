package com.shiliuai.app.screens;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.shiliuai.app.BuildConfig;
import com.shiliuai.app.ShellHost;
import com.shiliuai.app.net.Json;
import com.shiliuai.app.ui.Components;
import com.shiliuai.app.ui.Theme;

import org.json.JSONObject;

/**
 * 飞书屏：bot 信息、回调地址、健康检查、最近卡片点击。
 */
public final class FeishuScreen implements Screen {

    private ShellHost host;
    private View root;
    private TextView botInfo;
    private TextView healthChip;
    private TextView checkBtn;
    private LinearLayout recentList;

    @Override public String title() { return "飞书工作台"; }

    @Override
    public View onCreate(Context c, ShellHost host) {
        this.host = host;
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);

        LinearLayout col = Components.column(c);

        col.addView(Components.sectionHeader(c, "当前机器人"));
        LinearLayout botCard = Components.angularCard(c);
        botInfo = Components.monoBlock(c, botSummary());
        botCard.addView(botInfo);
        healthChip = Components.statusChip(c, "状态 待命", Components.ChipState.READY);
        botCard.addView(healthChip);
        checkBtn = Components.primaryButton(c, "检查机器人状态", this::checkHealth);
        botCard.addView(checkBtn);
        col.addView(botCard);

        col.addView(Components.sectionHeader(c, "回调地址"));
        LinearLayout urlCard = Components.angularCard(c);
        String base = host.backend().baseUrl();
        String botId = Json.nonBlank(BuildConfig.SHILIU_DEFAULT_BOT_ID, "<botId>");

        urlCard.addView(Theme.meta(c, "事件订阅 URL"));
        TextView eventUrl = Components.monoBlock(c, base + "/feishu/events/" + botId);
        urlCard.addView(eventUrl);
        urlCard.addView(Components.ghostButton(c, "复制 事件订阅 URL", () -> copy(eventUrl.getText().toString())));

        urlCard.addView(Theme.meta(c, "卡片回调 URL"));
        TextView cardUrl = Components.monoBlock(c, base + "/feishu/card-callback/" + botId);
        urlCard.addView(cardUrl);
        urlCard.addView(Components.ghostButton(c, "复制 卡片回调 URL", () -> copy(cardUrl.getText().toString())));

        urlCard.addView(Theme.body(c, "Cloudflare Quick Tunnel 每次重启都会变化；变化后需重新打包并安装新 APK。"));

        col.addView(urlCard);

        col.addView(Components.sectionHeader(c, "最近卡片点击"));
        recentList = Components.column(c);
        recentList.setPadding(0, 0, 0, 0);
        col.addView(recentList);
        col.addView(Components.ghostButton(c, "刷新最近卡片点击", this::loadRecent));

        scroll.addView(col);
        root = scroll;
        return root;
    }

    @Override public void onResume() { checkHealth(); loadRecent(); }

    private String botSummary() {
        String name = Json.nonBlank(BuildConfig.SHILIU_DEFAULT_BOT_NAME, "bot");
        String id = Json.nonBlank(BuildConfig.SHILIU_DEFAULT_BOT_ID, "未配置 botId");
        return "名称：" + name + "\n编号：" + id;
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) host.activity().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("视流 AI", text));
        Toast.makeText(host.activity(), "已复制", Toast.LENGTH_SHORT).show();
    }

    private void checkHealth() {
        if (BuildConfig.SHILIU_DEFAULT_BOT_ID == null || BuildConfig.SHILIU_DEFAULT_BOT_ID.isEmpty()) {
            host.setStatus("APK 未内置 botId", Components.ChipState.ERROR);
            updateChip("状态 未配置", Components.ChipState.ERROR);
            return;
        }
        Components.setBusy(checkBtn, true, "检查中…");
        host.executor().execute(() -> {
            try {
                String raw = host.backend().request("GET",
                        "/api/v1/bots/" + BuildConfig.SHILIU_DEFAULT_BOT_ID + "/health", null);
                JSONObject o = new JSONObject(raw);
                String status = o.optString("status", "");
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(checkBtn, false, null);
                    boolean ok = "ok".equalsIgnoreCase(status) || "online".equalsIgnoreCase(status);
                    updateChip(ok ? "状态" : "状态 " + status, ok ? Components.ChipState.OK : Components.ChipState.ERROR);
                    host.setStatus(ok ? "飞书" : "飞书 " + status, ok ? Components.ChipState.OK : Components.ChipState.ERROR);
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(checkBtn, false, null);
                    updateChip("状态 异常", Components.ChipState.ERROR);
                    host.setStatus("飞书检查失败", Components.ChipState.ERROR);
                });
            }
        });
    }

    private void loadRecent() {
        host.executor().execute(() -> {
            try {
                String raw = host.backend().request("GET", "/api/v1/workbench/overview", null);
                JSONObject o = new JSONObject(raw);
                org.json.JSONArray arr = o.optJSONArray("recentCardActions");
                host.activity().runOnUiThread(() -> {
                    recentList.removeAllViews();
                    Context c = recentList.getContext();
                    if (arr == null || arr.length() == 0) {
                        recentList.addView(Theme.body(c, "暂无卡片点击记录。"));
                        return;
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.optJSONObject(i);
                        if (row == null) continue;
                        LinearLayout card = Components.angularCard(c);
                        card.addView(Theme.title(c, Json.string(row, "action", "未知 action")));
                        card.addView(Theme.meta(c, "trace " + row.optString("traceId", "—")
                                + " · " + row.optString("createdAt", "")));
                        String status = row.optString("status", "");
                        Components.ChipState st = "ok".equalsIgnoreCase(status)
                                ? Components.ChipState.OK
                                : Components.ChipState.ERROR;
                        card.addView(Components.statusChip(c, status, st));
                        String err = row.optString("errorMessage", "");
                        if (!err.isEmpty()) card.addView(Components.monoBlock(c, err));
                        recentList.addView(card);
                    }
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() -> {
                    recentList.removeAllViews();
                    recentList.addView(Components.monoBlock(recentList.getContext(),
                            "读取卡片点击记录失败：" + e.getMessage()));
                });
            }
        });
    }

    private void updateChip(CharSequence label, Components.ChipState state) {
        TextView fresh = Components.statusChip(healthChip.getContext(), label, state);
        healthChip.setText(fresh.getText());
        healthChip.setTextColor(fresh.getCurrentTextColor());
        healthChip.setBackground(fresh.getBackground());
    }
}
