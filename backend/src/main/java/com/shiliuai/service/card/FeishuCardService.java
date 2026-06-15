package com.shiliuai.service.card;

import com.shiliuai.dto.ExtractResult;
import com.shiliuai.dto.LinkCandidateDto;
import com.shiliuai.dto.RiskFlagDto;
import com.shiliuai.dto.SaveTasksResponse;
import com.shiliuai.dto.SummaryDto;
import com.shiliuai.dto.TaskCandidateDto;
import com.shiliuai.dto.TaskDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeishuCardService {
    public Map<String, Object> buildProcessingCard(String traceId) {
        return buildProcessingCard(traceId, "resource_downloading", 10, "正在读取飞书消息中的图片资源，随后进入 OCR 与结构化整理。");
    }

    public Map<String, Object> buildProcessingCard(String traceId, String stage, int progress, String message) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", cardConfig());
        card.put("header", Map.of(
                "title", Map.of("tag", "plain_text", "content", "正在识别图片"),
                "template", "blue"
        ));
        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(markdown("""
                **当前阶段：%s**
                处理编号：%s
                进度：%s%%
                状态：%s
                """.formatted(stageName(stage), traceId, progress, emptyAs(message, "处理中"))));
        elements.add(note("系统会在完成后发送结果总览卡。"));
        elements.add(action(List.of(
                button("取消", "default", Map.of("action", "ignore", "traceId", traceId)),
                button("仅自己可见", "default", Map.of("action", "private_result", "traceId", traceId))
        )));
        card.put("elements", elements);
        return card;
    }

    public Map<String, Object> buildResultCard(ExtractResult extractResult) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", cardConfig());
        card.put("header", Map.of(
                "title", Map.of("tag", "plain_text", "content", "视流 AI 已整理完成"),
                "template", "blue"
        ));
        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(markdown(resultMetaLine(extractResult)));
        elements.add(markdown(summaryMarkdown(extractResult.summary, 3)));
        elements.add(Map.of("tag", "hr"));
        elements.add(markdown(resultStatsMarkdown(extractResult)));
        elements.add(markdown(highPriorityPreview(extractResult.tasks)));
        elements.add(action(List.of(
                button("保存任务", "primary", Map.of("action", "open_task_confirm", "traceId", extractResult.traceId)),
                button("生成日报素材", "default", Map.of("action", "create_report_material", "traceId", extractResult.traceId)),
                button("忽略", "default", Map.of("action", "ignore", "traceId", extractResult.traceId))
        )));
        card.put("elements", elements);
        return card;
    }

    public Map<String, Object> buildTaskConfirmCard(ExtractResult extractResult) {
        Map<String, Object> card = new LinkedHashMap<>();
        int taskCount = extractResult.tasks == null ? 0 : extractResult.tasks.size();
        card.put("config", cardConfig());
        card.put("header", Map.of(
                "title", Map.of("tag", "plain_text", "content", "识别到 " + taskCount + " 条任务，请确认"),
                "template", "wathet"
        ));
        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(markdown(taskConfirmationMarkdown(extractResult.tasks)));
        elements.add(action(List.of(
                button("全部保存", "primary", Map.of("action", "save_tasks", "traceId", extractResult.traceId)),
                button("返回结果", "default", Map.of("action", "ignore", "traceId", extractResult.traceId)),
                button("忽略", "default", Map.of("action", "ignore", "traceId", extractResult.traceId))
        )));
        card.put("elements", elements);
        return card;
    }

    public Map<String, Object> buildSaveSuccessCard(SaveTasksResponse response, String traceId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", cardConfig());
        card.put("header", Map.of(
                "title", Map.of("tag", "plain_text", "content", "任务保存成功"),
                "template", "green"
        ));
        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(markdown(savedTasksMarkdown(response)));
        elements.add(action(List.of(
                button("生成日报素材", "default", Map.of("action", "create_report_material", "traceId", traceId)),
                button("设置提醒", "default", Map.of("action", "set_reminder", "traceId", traceId))
        )));
        card.put("elements", elements);
        return card;
    }

    public Map<String, Object> buildDailyReportCard(ExtractResult extractResult) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", cardConfig());
        card.put("header", Map.of(
                "title", Map.of("tag", "plain_text", "content", "日报素材已生成"),
                "template", "purple"
        ));
        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(markdown(dailyReportMarkdown(extractResult)));
        elements.add(action(List.of(
                button("返回结果", "default", Map.of("action", "open_task_confirm", "traceId", extractResult.traceId))
        )));
        card.put("elements", elements);
        return card;
    }

    public Map<String, Object> buildErrorCard(String title, String message, String traceId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", cardConfig());
        card.put("header", Map.of(
                "title", Map.of("tag", "plain_text", "content", title),
                "template", "red"
        ));
        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(markdown("**错误原因**\n" + emptyAs(message, "处理失败")));
        card.put("elements", elements);
        return card;
    }

    private static Map<String, Object> button(String label, String type, Map<String, Object> value) {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("tag", "button");
        button.put("text", Map.of("tag", "plain_text", "content", label));
        if (!"default".equals(type)) {
            button.put("type", type);
        }
        button.put("value", value);
        return button;
    }

    private static Map<String, Object> action(List<Map<String, Object>> actions) {
        return Map.of("tag", "action", "actions", actions);
    }

    private static Map<String, Object> cardConfig() {
        return Map.of(
                "wide_screen_mode", true,
                "update_multi", true
        );
    }

    private static Map<String, Object> markdown(String content) {
        return Map.of("tag", "div", "text", Map.of("tag", "lark_md", "content", content.strip()));
    }

    private static Map<String, Object> note(String content) {
        return Map.of("tag", "note", "elements", List.of(Map.of("tag", "plain_text", "content", content)));
    }

    private static String summaryMarkdown(SummaryDto summary, int maxBullets) {
        if (summary == null) {
            return "**摘要**\n- 未生成摘要";
        }
        StringBuilder builder = new StringBuilder("**摘要**\n");
        int count = 0;
        for (String bullet : summary.bullets) {
            if (count++ >= maxBullets) {
                break;
            }
            builder.append("- ").append(bullet).append('\n');
        }
        return builder.toString().trim();
    }

    private static String taskConfirmationMarkdown(List<TaskCandidateDto> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "**未识别到任务**";
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (TaskCandidateDto task : tasks) {
            builder.append(index++).append(". **").append(emptyAs(task.title, "未命名任务")).append("**\n")
                    .append("负责人：").append(emptyAs(task.owner, "未指定"))
                    .append("｜截止：").append(emptyAs(task.dueText, "未设置"))
                    .append("｜优先级：").append(emptyAs(task.priority, "medium"))
                    .append("｜置信度：").append(percent(task.confidence))
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private static String resultStatsMarkdown(ExtractResult extractResult) {
        int taskCount = extractResult.tasks == null ? 0 : extractResult.tasks.size();
        int linkCount = extractResult.links == null ? 0 : extractResult.links.size();
        int riskCount = extractResult.riskFlags == null ? 0 : extractResult.riskFlags.size();
        StringBuilder builder = new StringBuilder("**识别结果**\n");
        builder.append("任务 ").append(taskCount).append(" 条｜链接 ").append(linkCount).append(" 条｜风险 ").append(riskCount).append(" 条");
        if (extractResult.links != null && !extractResult.links.isEmpty()) {
            builder.append("\n");
            for (LinkCandidateDto link : extractResult.links.stream().limit(2).toList()) {
                builder.append("- ").append(emptyAs(link.title, link.url)).append('\n');
            }
        }
        if (extractResult.riskFlags != null && !extractResult.riskFlags.isEmpty()) {
            builder.append("\n");
            for (RiskFlagDto risk : extractResult.riskFlags.stream().limit(2).toList()) {
                builder.append("- 风险：").append(risk.message).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private static String highPriorityPreview(List<TaskCandidateDto> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "**高优先级任务**\n未识别到待保存任务。";
        }
        StringBuilder builder = new StringBuilder("**高优先级任务预览**\n");
        tasks.stream()
                .filter(task -> "high".equalsIgnoreCase(task.priority) || task.confidence >= 0.85)
                .limit(3)
                .forEach(task -> builder.append("- ")
                        .append(emptyAs(task.title, "未命名任务"))
                        .append("｜").append(emptyAs(task.owner, "未指定"))
                        .append('\n'));
        if (builder.toString().equals("**高优先级任务预览**\n")) {
            builder.append("- ").append(emptyAs(tasks.get(0).title, "未命名任务"));
        }
        return builder.toString().trim();
    }

    private static String savedTasksMarkdown(SaveTasksResponse response) {
        if (response == null || response.tasks == null || response.tasks.isEmpty()) {
            return "**已保存 0 个任务**";
        }
        StringBuilder builder = new StringBuilder("**已保存 ").append(response.savedCount).append(" 个任务**\n");
        for (TaskDto task : response.tasks.stream().limit(6).toList()) {
            builder.append("- ").append(task.title)
                    .append("｜").append(emptyAs(task.owner, "未指定"))
                    .append("｜").append(emptyAs(task.priority, "medium"))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private static String dailyReportMarkdown(ExtractResult extractResult) {
        if (extractResult.dailyReportMaterials == null || extractResult.dailyReportMaterials.isEmpty()) {
            return "**日报素材**\n- 当前识别结果没有生成日报素材。";
        }
        StringBuilder builder = new StringBuilder("**今日完成**\n");
        for (String material : extractResult.dailyReportMaterials.stream().limit(5).toList()) {
            builder.append("- ").append(material).append('\n');
        }
        return builder.toString().trim();
    }

    private static String sceneName(ExtractResult extractResult) {
        return emptyAs(extractResult.scene, "结构化结果");
    }

    private static String resultMetaLine(ExtractResult extractResult) {
        return "图片类型：" + sceneName(extractResult)
                + "｜抽取方式：" + extractModeName(extractResult.extractMode)
                + "｜OCR 置信度 " + percent(nullable(extractResult.ocrConfidence))
                + "｜抽取置信度 " + percent(nullable(extractResult.extractConfidence, summaryConfidence(extractResult.summary)));
    }

    private static String extractModeName(String mode) {
        return switch (emptyAs(mode, "unknown")) {
            case "llm" -> "LLM";
            case "llm_repaired" -> "LLM 修复 JSON";
            case "rule_fallback" -> "规则兜底";
            case "failed" -> "失败";
            default -> "未知";
        };
    }

    private static String stageName(String stage) {
        return switch (emptyAs(stage, "processing")) {
            case "received" -> "已接收";
            case "resource_downloading" -> "读取飞书资源";
            case "resource_downloaded" -> "图片已保存";
            case "ocr" -> "OCR 识别";
            case "ocr_done" -> "版面与场景判断";
            case "structuring" -> "结构化整理";
            case "done" -> "整理完成";
            case "error" -> "处理失败";
            default -> stage;
        };
    }

    private static double summaryConfidence(SummaryDto summary) {
        return summary == null ? 0.0 : summary.confidence;
    }

    private static double nullable(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double nullable(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private static String emptyAs(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
