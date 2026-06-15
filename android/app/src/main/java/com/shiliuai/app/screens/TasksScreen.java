package com.shiliuai.app.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.shiliuai.app.ShellHost;
import com.shiliuai.app.net.Json;
import com.shiliuai.app.ui.Components;
import com.shiliuai.app.ui.Theme;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 团队化任务工作台。
 *
 * 设计目标：
 * - 新建任务时可指定 owner。
 * - 顶部双行筛选条：状态 chip + 负责人输入框。
 * - 每张任务卡左侧色条 + 右上角 owner 徽章 + 右下角"改派"按钮。
 *
 * 不再展示 JSON 原文；失败时通过 Toast/状态条 + 错误卡呈现。
 */
public final class TasksScreen implements Screen {

    private ShellHost host;
    private View root;

    private EditText titleInput;
    private EditText ownerInput;
    private TextView newBtn;

    private EditText ownerFilterInput;
    private TextView applyFilterBtn;
    private LinearLayout activeFilterRow;
    private TextView refreshBtn;

    private LinearLayout list;
    private TextView countView;

    private String currentStatus = null;
    private String currentOwner = null;

    @Override public String title() { return "任务工作台 · 团队"; }

    @Override
    public View onCreate(Context c, ShellHost host) {
        this.host = host;
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);

        LinearLayout col = Components.column(c);

        // ===== 新建任务 =====
        col.addView(Components.sectionHeader(c, "新建任务"));
        LinearLayout newCard = Components.angularCard(c);
        titleInput = Components.input(c, "输入任务标题，例如：整理本周 OCR 异常");
        newCard.addView(titleInput);
        ownerInput = Components.singleLineInput(c, "指定负责人，例如：陈思（可留空）");
        newCard.addView(ownerInput);
        newBtn = Components.primaryButton(c, "提交任务", this::createTask);
        newCard.addView(newBtn);
        col.addView(newCard);

        // ===== 状态筛选 =====
        col.addView(Components.sectionHeader(c, "筛选"));
        LinearLayout filterCard = Components.angularCard(c);

        TextView statusLabel = Theme.meta(c, "按状态");
        statusLabel.setTextColor(Theme.textDim(c));
        filterCard.addView(statusLabel);

        HorizontalScrollView statusScroll = new HorizontalScrollView(c);
        statusScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout statusRow = Components.row(c);
        statusRow.addView(statusChipButton(c, "全部", null));
        statusRow.addView(statusChipButton(c, "待办", "todo"));
        statusRow.addView(statusChipButton(c, "进行中", "in_progress"));
        statusRow.addView(statusChipButton(c, "已完成", "done"));
        statusScroll.addView(statusRow);
        filterCard.addView(statusScroll);

        // ===== 负责人筛选 =====
        TextView ownerLabel = Theme.meta(c, "按负责人");
        ownerLabel.setTextColor(Theme.textDim(c));
        filterCard.addView(ownerLabel);

        LinearLayout ownerFilterRow = Components.row(c);
        ownerFilterInput = Components.singleLineInput(c, "输入负责人名字，例如：陈思");
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ownerFilterRow.addView(ownerFilterInput, inLp);
        applyFilterBtn = Components.ghostButton(c, "应用", this::applyOwnerFilter);
        ownerFilterRow.addView(applyFilterBtn);
        filterCard.addView(ownerFilterRow);

        activeFilterRow = Components.row(c);
        filterCard.addView(activeFilterRow);
        refreshActiveFilterRow();

        col.addView(filterCard);

        refreshBtn = Components.ghostButton(c, "刷新列表", this::refresh);
        col.addView(refreshBtn);

        // ===== 列表 =====
        LinearLayout headerRow = Components.row(c);
        TextView listHeader = Theme.section(c, "任务列表");
        headerRow.addView(listHeader,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        countView = Components.statusChip(c, "0 条", Components.ChipState.READY);
        headerRow.addView(countView);
        col.addView(headerRow);

        list = Components.column(c);
        list.setPadding(0, 0, 0, 0);
        col.addView(list);

        scroll.addView(col);
        root = scroll;
        return root;
    }

    private TextView statusChipButton(Context c, String label, String filter) {
        return Components.slimChipButton(c, label, () -> {
            currentStatus = filter;
            refresh();
        });
    }

    @Override public void onResume() { refresh(); }

    // ===== 新建 =====

    private void createTask() {
        String t = titleInput.getText().toString().trim();
        if (t.isEmpty()) {
            host.setStatus("请先输入任务标题", Components.ChipState.ERROR);
            return;
        }
        String owner = ownerInput.getText().toString().trim();
        Components.setBusy(newBtn, true, "提交中…");
        host.executor().execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("title", t);
                body.put("source", "android_app");
                if (!owner.isEmpty()) body.put("owner", owner);
                host.backend().request("POST", "/api/v1/tasks", body.toString());
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(newBtn, false, null);
                    titleInput.setText("");
                    ownerInput.setText("");
                    host.setStatus("任务已创建" + (owner.isEmpty() ? "" : " · " + owner), Components.ChipState.OK);
                    refresh();
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(newBtn, false, null);
                    host.setStatus("创建任务失败：" + e.getMessage(), Components.ChipState.ERROR);
                });
            }
        });
    }

    // ===== 筛选 =====

    private void applyOwnerFilter() {
        String text = ownerFilterInput.getText().toString().trim();
        currentOwner = text.isEmpty() ? null : text;
        ownerFilterInput.setText("");
        refresh();
    }

    private void clearOwnerFilter() {
        currentOwner = null;
        refresh();
    }

    private void clearStatusFilter() {
        currentStatus = null;
        refresh();
    }

    private void refreshActiveFilterRow() {
        activeFilterRow.removeAllViews();
        Context c = activeFilterRow.getContext();
        boolean any = false;
        if (currentStatus != null) {
            activeFilterRow.addView(Components.activeFilterChip(c,
                    "状态：" + statusLabel(currentStatus), this::clearStatusFilter));
            any = true;
        }
        if (currentOwner != null) {
            activeFilterRow.addView(Components.activeFilterChip(c,
                    "负责人：" + currentOwner, this::clearOwnerFilter));
            any = true;
        }
        activeFilterRow.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    private static String statusLabel(String key) {
        switch (key) {
            case "todo": return "待办";
            case "in_progress": return "进行中";
            case "done": return "已完成";
            case "ignored": return "已忽略";
            default: return key;
        }
    }

    // ===== 列表刷新 =====

    private void refresh() {
        refreshActiveFilterRow();
        Components.setBusy(refreshBtn, true, "刷新中…");
        StringBuilder path = new StringBuilder("/api/v1/tasks");
        boolean hasQuery = false;
        if (currentStatus != null) {
            path.append("?status=").append(currentStatus);
            hasQuery = true;
        }
        if (currentOwner != null) {
            path.append(hasQuery ? "&" : "?");
            path.append("owner=").append(URLEncoder.encode(currentOwner, StandardCharsets.UTF_8));
        }
        host.executor().execute(() -> {
            try {
                String raw = host.backend().request("GET", path.toString(), null);
                JSONObject o = new JSONObject(raw);
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(refreshBtn, false, null);
                    bindList(o);
                });
            } catch (IOException e) {
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(refreshBtn, false, null);
                    host.setStatus("读取任务失败", Components.ChipState.ERROR);
                    list.removeAllViews();
                    list.addView(Components.monoBlock(list.getContext(), e.getMessage()));
                    updateCount(0);
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() -> {
                    Components.setBusy(refreshBtn, false, null);
                    host.setStatus("任务解析失败", Components.ChipState.ERROR);
                });
            }
        });
    }

    private void updateCount(int count) {
        TextView fresh = Components.statusChip(countView.getContext(),
                count + " 条",
                count == 0 ? Components.ChipState.READY : Components.ChipState.OK);
        countView.setText(fresh.getText());
        countView.setTextColor(fresh.getCurrentTextColor());
        countView.setBackground(fresh.getBackground());
    }

    private void bindList(JSONObject root) {
        list.removeAllViews();
        JSONArray items = root.optJSONArray("items");
        int count = items == null ? 0 : items.length();
        updateCount(count);
        if (count == 0) {
            Context c = list.getContext();
            LinearLayout empty = Components.angularCard(c);
            empty.addView(Theme.body(c, emptyMessage()));
            list.addView(empty);
            host.setStatus("任务列表 空", Components.ChipState.READY);
            return;
        }
        Context c = list.getContext();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            list.addView(buildTaskCard(c, it));
        }
        host.setStatus("任务列表 " + count + " 条", Components.ChipState.OK);
    }

    private String emptyMessage() {
        if (currentStatus == null && currentOwner == null) {
            return "当前没有任务。可以在上方「新建任务」创建第一条。";
        }
        StringBuilder sb = new StringBuilder("没有匹配的任务");
        if (currentStatus != null) sb.append("｜状态：").append(statusLabel(currentStatus));
        if (currentOwner != null) sb.append("｜负责人：").append(currentOwner);
        sb.append("。可以清除筛选查看全部。");
        return sb.toString();
    }

    // ===== 单个卡片 =====

    private LinearLayout buildTaskCard(Context c, JSONObject it) {
        String status = Json.string(it, "status", "todo");
        String owner = Json.string(it, "owner", "未指定");
        String title = Json.string(it, "title", "未命名任务");
        String taskId = it.optString("id", "");

        LinearLayout card = Components.taskCard(c, stateOf(it));
        LinearLayout body = Components.taskCardBody(card);

        // 标题 + 负责人徽章
        body.addView(Components.titleWithBadge(c, title, Components.ownerBadge(c, owner)));

        // 第一行 chips：状态 / 优先级
        LinearLayout chipRow = Components.row(c);
        chipRow.addView(Components.statusChip(c, statusLabel(status), stateOf(it)));
        String priority = Json.string(it, "priority", "medium");
        chipRow.addView(Components.statusChip(c, "优先级 " + priority, Components.ChipState.READY));
        body.addView(chipRow);

        body.addView(Components.hairline(c), Components.hairlineLp(c));

        // 元数据：负责人 | 来源 | 截止
        StringBuilder meta = new StringBuilder();
        meta.append("负责人 ").append(owner);
        String src = Json.string(it, "sourceType", it.optString("source", ""));
        if (!src.isEmpty() && !src.equals("unknown")) {
            meta.append("  ｜  来源 ").append(src);
        }
        String dueText = it.optString("dueText", "");
        if (!dueText.isEmpty()) {
            meta.append("  ｜  截止 ").append(dueText);
        }
        body.addView(Components.metaLine(c, meta.toString()));

        // 证据文本（若有）
        String ev = it.optString("evidenceText", "");
        if (!ev.isEmpty()) {
            body.addView(Components.monoBlock(c, ev));
        }

        // 卡内动作行：状态切换 + 忽略 + 改派
        LinearLayout actionRow = Components.row(c);
        TextView statusBtn = Components.cardActionButton(c,
                nextStatusLabel(status),
                () -> cycleStatus(taskId, status));
        actionRow.addView(statusBtn);
        if (!"ignored".equals(status)) {
            TextView ignoreBtn = Components.cardActionButton(c, "忽略",
                    () -> updateTaskStatus(taskId, "ignored"));
            actionRow.addView(ignoreBtn);
        }
        // 占位让"改派"按钮靠右
        View spacer = new View(c);
        actionRow.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
        TextView reassignBtn = Components.cardActionButton(c, "改派",
                () -> showReassignDialog(c, taskId, owner));
        actionRow.addView(reassignBtn);
        body.addView(actionRow);

        return card;
    }

    /**
     * 任务状态推进顺序：todo → in_progress → done → todo（done 回滚便于演示）。
     * ignored 通过卡片里的"忽略"按钮进入。
     */
    private static String nextStatus(String current) {
        if (current == null) return "in_progress";
        switch (current) {
            case "todo": return "in_progress";
            case "in_progress": return "done";
            case "done": return "todo";
            case "ignored": return "todo";
            default: return "in_progress";
        }
    }

    private static String nextStatusLabel(String current) {
        return "→ " + statusLabel(nextStatus(current));
    }

    private void cycleStatus(String taskId, String currentStatus) {
        if (taskId == null || taskId.isEmpty()) return;
        String target = nextStatus(currentStatus);
        updateTaskStatus(taskId, target);
    }

    private void updateTaskStatus(String taskId, String target) {
        if (taskId == null || taskId.isEmpty()) return;
        host.setStatus("更新状态 → " + statusLabel(target) + "…", Components.ChipState.RUNNING);
        host.executor().execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", target);
                // 后端用 POST 镜像 PATCH，避开 Android HttpURLConnection 不支持 PATCH 的限制。
                host.backend().request("POST",
                        "/api/v1/tasks/" + taskId + "/status",
                        body.toString());
                host.activity().runOnUiThread(() -> {
                    host.setStatus("状态已更新 · " + statusLabel(target), Components.ChipState.OK);
                    refresh();
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() ->
                        host.setStatus("更新状态失败：" + e.getMessage(), Components.ChipState.ERROR));
            }
        });
    }

    private Components.ChipState stateOf(JSONObject it) {
        String s = it.optString("status", "").toLowerCase();
        if (s.equals("done") || s.equals("completed")) return Components.ChipState.OK;
        if (s.equals("in_progress")) return Components.ChipState.RUNNING;
        if (s.equals("error") || s.equals("failed")) return Components.ChipState.ERROR;
        return Components.ChipState.READY;
    }

    // ===== 改派对话框 =====

    private void showReassignDialog(Context c, String taskId, String currentOwner) {
        if (taskId == null || taskId.isEmpty()) return;
        EditText input = Components.singleLineInput(c, "新负责人，例如：李伟");
        input.setText(currentOwner == null || "未指定".equals(currentOwner) ? "" : currentOwner);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        AlertDialog dialog = new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("改派任务负责人")
                .setMessage("当前：" + (currentOwner == null ? "未指定" : currentOwner))
                .setView(wrapDialogView(c, input))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> doReassign(taskId, input.getText().toString().trim()))
                .create();
        dialog.show();
    }

    private View wrapDialogView(Context c, View child) {
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(c, 16);
        wrap.setPadding(pad, pad, pad, 0);
        wrap.addView(child, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private void doReassign(String taskId, String newOwner) {
        host.setStatus("改派中…", Components.ChipState.RUNNING);
        host.executor().execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("owner", newOwner);
                host.backend().request("POST",
                        "/api/v1/tasks/" + taskId + "/owner",
                        body.toString());
                host.activity().runOnUiThread(() -> {
                    host.setStatus("已改派 · " + (newOwner.isEmpty() ? "未指定" : newOwner),
                            Components.ChipState.OK);
                    refresh();
                });
            } catch (Exception e) {
                host.activity().runOnUiThread(() ->
                        host.setStatus("改派失败：" + e.getMessage(), Components.ChipState.ERROR));
            }
        });
    }
}
