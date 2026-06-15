package com.shiliuai.app.net;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 小工具。
 *
 * - 美化输出。
 * - 安全读取。
 * - 任务/论文专用格式化。
 *
 * 屏幕层从原 MainActivity 巨型方法搬到这里，去掉 UI 耦合。
 */
public final class Json {

    private Json() {}

    public static String pretty(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        try {
            if (trimmed.startsWith("[")) return new JSONArray(trimmed).toString(2);
            if (trimmed.startsWith("{")) return new JSONObject(trimmed).toString(2);
        } catch (JSONException ignored) {
            return raw;
        }
        return raw;
    }

    public static boolean isTerminal(String status) {
        if (status == null) return false;
        String s = status.toLowerCase();
        // 统一识别后端三条产物的终止状态：
        // - Vision/Trace: done / error
        // - Agent: done / no_results / data_source_error / needs_data_source
        // - Codex: done / failed / timeout / disabled
        // 旧实现漏了 timeout / no_results 等，导致前端轮询永远在 running，被 maxAttempts 截断。
        return s.equals("done")
                || s.equals("error")
                || s.equals("failed")
                || s.equals("timeout")
                || s.equals("disabled")
                || s.equals("no_results")
                || s.equals("data_source_error")
                || s.equals("needs_data_source")
                || s.equals("completed")
                || s.equals("success");
    }

    public static String string(JSONObject o, String key, String fallback) {
        if (o == null) return fallback;
        String v = o.optString(key, "");
        return v == null || v.isEmpty() ? fallback : v;
    }

    public static String nonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public static boolean hasItems(JSONObject o, String key) {
        if (o == null) return false;
        JSONArray arr = o.optJSONArray(key);
        return arr != null && arr.length() > 0;
    }

    public static String taskList(JSONObject root) {
        JSONArray items = root == null ? null : root.optJSONArray("items");
        if (items == null || items.length() == 0) return "当前没有任务。";

        StringBuilder b = new StringBuilder();
        b.append("任务共 ").append(items.length()).append(" 条\n\n");
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            b.append(i + 1).append(". ").append(string(it, "title", "未命名任务")).append('\n');
            b.append("状态：").append(string(it, "status", "unknown"))
                    .append("｜优先级：").append(string(it, "priority", "medium"))
                    .append("｜负责人：").append(string(it, "owner", "未指定")).append('\n');
            String src = string(it, "sourceType", it.optString("source", ""));
            String srcId = it.optString("sourceId", it.optString("traceId", ""));
            if (!src.isEmpty() || !srcId.isEmpty()) {
                b.append("来源：").append(nonBlank(src, "unknown"));
                if (!srcId.isEmpty()) b.append(" · ").append(srcId);
                b.append('\n');
            }
            String ev = it.optString("evidenceText", "");
            if (!ev.isEmpty()) b.append("证据：").append(ev).append('\n');
            b.append('\n');
        }
        return b.toString().trim();
    }

    public static String paperList(JSONObject root) {
        JSONArray papers = root == null ? null : root.optJSONArray("papers");
        if (papers == null || papers.length() == 0) return "没有返回论文候选。";

        StringBuilder b = new StringBuilder();
        b.append("论文候选 ").append(papers.length()).append(" 篇\n\n");
        for (int i = 0; i < papers.length(); i++) {
            JSONObject p = papers.optJSONObject(i);
            if (p == null) continue;
            b.append(i + 1).append(". ").append(string(p, "title", "未命名论文")).append('\n');
            List<String> meta = new ArrayList<>();
            String year = p.optString("year", "");
            String venue = p.optString("venue", "");
            if (!year.isEmpty()) meta.add(year);
            if (!venue.isEmpty()) meta.add(venue);
            if (!meta.isEmpty()) b.append(String.join("｜", meta)).append('\n');
            String url = p.optString("url", "");
            if (!url.isEmpty()) b.append(url).append('\n');
            String why = p.optString("whyRelevant", "");
            if (!why.isEmpty()) b.append("相关性：").append(why).append('\n');
            b.append('\n');
        }
        if (hasItems(root, "tasks")) b.append("已生成阅读任务候选，可在结果返回后保存。");
        return b.toString().trim();
    }

    public static String ocrPlainText(JSONObject root) {
        JSONObject ocr = root == null ? null : root.optJSONObject("ocr");
        return ocr == null ? "" : ocr.optString("plainText", "");
    }

    public static String dailyReport(JSONObject root) {
        List<String> completed = new ArrayList<>();
        JSONArray materials = root.optJSONArray("dailyReportMaterials");
        if (materials != null) {
            for (int i = 0; i < materials.length(); i++) {
                String s = materials.optString(i, "").trim();
                if (!s.isEmpty()) completed.add(s);
            }
        }
        if (completed.isEmpty()) {
            JSONObject summary = root.optJSONObject("summary");
            if (summary != null) {
                String title = summary.optString("title", "").trim();
                if (!title.isEmpty()) completed.add("整理图片内容：" + title + "。");
                JSONArray bullets = summary.optJSONArray("bullets");
                if (bullets != null) {
                    for (int i = 0; i < Math.min(3, bullets.length()); i++) {
                        String s = bullets.optString(i, "").trim();
                        if (!s.isEmpty()) completed.add(s.endsWith("。") ? s : s + "。");
                    }
                }
            }
        }

        List<String> followUps = new ArrayList<>();
        JSONArray tasks = root.optJSONArray("tasks");
        if (tasks != null) {
            for (int i = 0; i < Math.min(6, tasks.length()); i++) {
                JSONObject t = tasks.optJSONObject(i);
                if (t == null) continue;
                String title = t.optString("title", "").trim();
                if (title.isEmpty()) continue;
                StringBuilder line = new StringBuilder(title);
                String owner = t.optString("owner", "").trim();
                String due = t.optString("dueText", "").trim();
                if (!owner.isEmpty()) line.append("｜负责人：").append(owner);
                if (!due.isEmpty()) line.append("｜截止：").append(due);
                followUps.add(line.toString());
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("今日完成\n");
        if (completed.isEmpty()) b.append("- 当前 OCR 结果没有生成日报素材。\n");
        else for (String s : completed) b.append("- ").append(s).append('\n');
        b.append("\n待跟进\n");
        if (followUps.isEmpty()) b.append("- 暂无明确任务候选。\n");
        else for (String s : followUps) b.append("- ").append(s).append('\n');
        return b.toString().trim();
    }
}
