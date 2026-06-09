package com.shiliuai.service.extract;

import com.shiliuai.dto.ExtractRequest;
import com.shiliuai.dto.ExtractResult;
import com.shiliuai.dto.LinkCandidateDto;
import com.shiliuai.dto.OcrBlock;
import com.shiliuai.dto.RiskFlagDto;
import com.shiliuai.dto.SummaryDto;
import com.shiliuai.dto.TaskCandidateDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuleBasedExtractService implements ExtractService {
    private static final List<String> TASK_TRIGGERS = List.of("整理", "完成", "提交", "确认", "跟进", "处理", "发我", "输出", "实现");
    private static final List<String> TIME_WORDS = List.of("今天", "明天", "下班前", "本周", "周五", "月底");
    private static final List<String> HIGH_PRIORITY_WORDS = List.of("紧急", "尽快", "必须", "高优");
    private static final Pattern URL = Pattern.compile("https?://[^\\s，。)）]+");

    @Override
    public ExtractResult extract(ExtractRequest request) {
        ExtractResult result = new ExtractResult();
        result.traceId = request.traceId;
        List<String> lines = normalizedLines(request.plainText);
        result.summary = buildSummary(lines);
        result.links = extractLinks(request.plainText);
        result.tasks = extractTasks(lines, request);
        result.dailyReportMaterials = buildDailyReport(result.summary, result.tasks);
        for (TaskCandidateDto task : result.tasks) {
            if (containsAny(task.dueText, List.of("今天", "明天", "下班前", "本周", "周五", "月底"))) {
                result.riskFlags.add(new RiskFlagDto(
                        "low_due_time_precision",
                        "识别到相对时间“" + task.dueText + "”，需要用户确认具体日期。"
                ));
            }
        }
        return result;
    }

    private static SummaryDto buildSummary(List<String> lines) {
        SummaryDto summary = new SummaryDto();
        if (lines.isEmpty()) {
            summary.title = "未识别到明确事项";
            summary.bullets = List.of("图片中没有明显任务或链接。");
            summary.confidence = 0.4;
            return summary;
        }
        String joined = String.join(" ", lines);
        if (joined.contains("Android") || joined.contains("飞书")) {
            summary.title = "Android + 飞书机器人实现方案讨论";
        } else {
            summary.title = "图片内容整理";
        }
        summary.bullets = lines.stream()
                .filter(line -> !line.startsWith("http://") && !line.startsWith("https://"))
                .limit(3)
                .map(RuleBasedExtractService::asBullet)
                .toList();
        if (summary.bullets.isEmpty()) {
            summary.bullets = List.of("识别到链接或零散文本，可进入 OCR 原文查看。");
        }
        summary.confidence = 0.82;
        return summary;
    }

    private static List<TaskCandidateDto> extractTasks(List<String> lines, ExtractRequest request) {
        List<TaskCandidateDto> tasks = new ArrayList<>();
        int index = 1;
        for (String line : lines) {
            if (!containsAny(line, TASK_TRIGGERS)) {
                continue;
            }
            TaskCandidateDto task = new TaskCandidateDto();
            task.tempId = "task_tmp_" + index++;
            task.title = cleanupTaskTitle(line);
            task.owner = "未指定";
            task.dueText = firstMatch(line, TIME_WORDS);
            task.dueAt = null;
            task.priority = containsAny(line, HIGH_PRIORITY_WORDS) || task.dueText != null ? "high" : "medium";
            task.sourceBlockIds = sourceBlockIds(line, request);
            task.confidence = 0.87;
            task.status = "pending_confirm";
            tasks.add(task);
        }
        return tasks;
    }

    private static List<LinkCandidateDto> extractLinks(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return List.of();
        }
        Matcher matcher = URL.matcher(plainText);
        Set<String> urls = new LinkedHashSet<>();
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        List<LinkCandidateDto> links = new ArrayList<>();
        for (String url : urls) {
            LinkCandidateDto link = new LinkCandidateDto();
            link.url = url;
            link.title = "识别链接";
            link.confidence = 0.95;
            links.add(link);
        }
        return links;
    }

    private static List<String> buildDailyReport(SummaryDto summary, List<TaskCandidateDto> tasks) {
        List<String> materials = new ArrayList<>();
        if (summary != null && summary.title != null && !"未识别到明确事项".equals(summary.title)) {
            materials.add("整理图片内容：" + summary.title + "。");
        }
        for (TaskCandidateDto task : tasks) {
            materials.add("跟进事项：" + task.title + "。");
        }
        return materials;
    }

    private static List<String> normalizedLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R+"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static String asBullet(String line) {
        if (line.endsWith("。") || line.endsWith(".") || line.endsWith("！") || line.endsWith("!")) {
            return line;
        }
        return line + "。";
    }

    private static String cleanupTaskTitle(String line) {
        String cleaned = line.replaceAll("^请", "").trim();
        if (cleaned.length() > 80) {
            return cleaned.substring(0, 80);
        }
        return cleaned;
    }

    private static String firstMatch(String value, List<String> candidates) {
        if (value == null) {
            return null;
        }
        return candidates.stream().filter(value::contains).findFirst().orElse(null);
    }

    private static boolean containsAny(String value, List<String> candidates) {
        return value != null && candidates.stream().anyMatch(value::contains);
    }

    private static List<String> sourceBlockIds(String line, ExtractRequest request) {
        if (request.ocrResult == null || request.ocrResult.blocks == null) {
            return List.of();
        }
        return request.ocrResult.blocks.stream()
                .filter(block -> blockMatches(line, block))
                .map(block -> block.id)
                .toList();
    }

    private static boolean blockMatches(String line, OcrBlock block) {
        return block != null && block.text != null && (line.contains(block.text) || block.text.contains(line));
    }
}
