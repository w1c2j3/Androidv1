package com.shiliuai.service.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.dto.ExtractRequest;
import com.shiliuai.dto.ExtractResult;
import com.shiliuai.dto.OcrBlock;
import com.shiliuai.dto.OcrResult;
import com.shiliuai.dto.SummaryDto;
import com.shiliuai.dto.TaskCandidateDto;
import com.shiliuai.service.llm.LlmChatService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Primary
@Service
public class LlmStructuredExtractService implements ExtractService {
    private static final String SYSTEM_PROMPT = """
            你是视流 AI 的截图结构化抽取器。只返回一个严格 JSON 对象，不要 Markdown，不要解释。
            JSON 字段必须兼容：
            traceId, scene, summary{title,bullets,confidence}, tasks[], links[], dailyReportMaterials[], riskFlags[]。
            tasks 每项字段：tempId,title,owner,dueText,dueAt,priority,evidence,sourceBlockIds,confidence,status。
            links 每项字段：url,title,confidence。riskFlags 每项字段：type,message。
            如果 OCR 原文证据不足，明确写入 riskFlags，不要编造任务。
            """;

    private final LlmChatService llmChatService;
    private final RuleBasedExtractService fallbackService;
    private final ObjectMapper objectMapper;

    public LlmStructuredExtractService(LlmChatService llmChatService,
                                       RuleBasedExtractService fallbackService,
                                       ObjectMapper objectMapper) {
        this.llmChatService = llmChatService;
        this.fallbackService = fallbackService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExtractResult extract(ExtractRequest request) {
        if (!hasOcrText(request)) {
            return fallback(request, "OCR 原文为空，未调用 LLM；结果来自规则兜底。");
        }
        if (!llmChatService.isAvailable()) {
            return fallback(request, "LLM 未配置或未启用；结果来自规则兜底。");
        }

        String rawJson = null;
        try {
            rawJson = llmChatService.chatJson(SYSTEM_PROMPT, buildPrompt(request));
            return parseResult(rawJson, request, "llm", null);
        } catch (Exception firstError) {
            if (StringUtils.hasText(rawJson)) {
                try {
                    String repaired = llmChatService.chatJson(SYSTEM_PROMPT, buildRepairPrompt(rawJson, firstError));
                    return parseResult(repaired, request, "llm_repaired", "首次 JSON 解析失败，已由 LLM 修复一次。");
                } catch (Exception repairError) {
                    return fallback(request, "LLM JSON 解析失败，修复一次后仍失败：" + safeMessage(repairError));
                }
            }
            return fallback(request, "LLM 调用失败：" + safeMessage(firstError));
        }
    }

    private ExtractResult parseResult(String rawJson,
                                      ExtractRequest request,
                                      String mode,
                                      String warning) throws Exception {
        JsonNode node = objectMapper.readTree(extractJsonObject(rawJson));
        ExtractResult result = objectMapper.treeToValue(node, ExtractResult.class);
        normalize(result, request, mode, rawJson, warning);
        return result;
    }

    private ExtractResult fallback(ExtractRequest request, String reason) {
        ExtractResult result = fallbackService.extract(request);
        normalize(result, request, "rule_fallback", null, reason);
        return result;
    }

    private void normalize(ExtractResult result,
                           ExtractRequest request,
                           String mode,
                           String rawJson,
                           String error) {
        if (result.traceId == null) {
            result.traceId = request.traceId;
        }
        result.scene = StringUtils.hasText(result.scene) ? result.scene : request.scene;
        result.extractMode = mode;
        result.llmModel = llmChatService.modelName();
        result.ocrConfidence = ocrConfidence(request.ocrResult);
        result.rawModelJson = rawJson;
        result.extractError = error;
        if (result.summary == null) {
            result.summary = emptySummary(error);
        }
        if (result.tasks == null) {
            result.tasks = new ArrayList<>();
        }
        if (result.links == null) {
            result.links = new ArrayList<>();
        }
        if (result.dailyReportMaterials == null) {
            result.dailyReportMaterials = new ArrayList<>();
        }
        if (result.riskFlags == null) {
            result.riskFlags = new ArrayList<>();
        }
        int index = 1;
        for (TaskCandidateDto task : result.tasks) {
            if (!StringUtils.hasText(task.tempId)) {
                task.tempId = "task_tmp_" + index;
            }
            if (!StringUtils.hasText(task.owner)) {
                task.owner = "未指定";
            }
            if (!StringUtils.hasText(task.priority)) {
                task.priority = "medium";
            }
            if (!StringUtils.hasText(task.status)) {
                task.status = "pending_confirm";
            }
            if (!StringUtils.hasText(task.evidence)) {
                task.evidence = firstEvidence(request.ocrResult);
            }
            index++;
        }
        result.extractConfidence = extractConfidence(result);
    }

    private static SummaryDto emptySummary(String reason) {
        SummaryDto summary = new SummaryDto();
        summary.title = "未生成结构化摘要";
        summary.bullets = List.of(StringUtils.hasText(reason) ? reason : "没有可用抽取结果。");
        summary.confidence = 0.0;
        return summary;
    }

    private static boolean hasOcrText(ExtractRequest request) {
        if (request == null) {
            return false;
        }
        if (StringUtils.hasText(request.plainText)) {
            return true;
        }
        OcrResult ocr = request.ocrResult;
        return ocr != null && ocr.blocks != null && ocr.blocks.stream().anyMatch(block -> StringUtils.hasText(block.text));
    }

    private static String buildPrompt(ExtractRequest request) {
        OcrResult ocr = request.ocrResult;
        StringBuilder builder = new StringBuilder();
        builder.append("traceId: ").append(request.traceId).append('\n');
        builder.append("scene: ").append(blankAs(request.scene, "unknown")).append('\n');
        builder.append("ocrConfidence: ").append(ocrConfidence(ocr)).append('\n');
        builder.append("plainText:\n").append(blankAs(request.plainText, "")).append("\n\n");
        builder.append("blocks:\n");
        if (ocr != null && ocr.blocks != null) {
            for (OcrBlock block : ocr.blocks.stream().limit(80).toList()) {
                builder.append("- id=").append(blankAs(block.id, ""))
                        .append(", confidence=").append(block.confidence)
                        .append(", text=").append(blankAs(block.text, ""))
                        .append('\n');
            }
        }
        builder.append("""

                输出要求：
                - 用中文整理摘要、任务、链接、日报素材、风险。
                - 任务必须来自 OCR 证据；不确定就降低 confidence 并写 evidence。
                - tempId 使用 task_tmp_1, task_tmp_2 递增。
                - sourceBlockIds 尽量填入对应 OCR block id。
                """);
        return builder.toString();
    }

    private static String buildRepairPrompt(String rawJson, Exception error) {
        return """
                下面内容不是合法 JSON，错误是：%s
                请只返回修复后的单个 JSON 对象，不要 Markdown。

                原始内容：
                %s
                """.formatted(safeMessage(error), rawJson);
    }

    private static String extractJsonObject(String value) {
        String trimmed = value == null ? "" : value.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return trimmed;
        }
        return trimmed.substring(start, end + 1);
    }

    private static Double ocrConfidence(OcrResult ocr) {
        if (ocr == null) {
            return null;
        }
        if (ocr.averageConfidence != null) {
            return ocr.averageConfidence;
        }
        if (ocr.blocks == null || ocr.blocks.isEmpty()) {
            return null;
        }
        return ocr.blocks.stream().mapToDouble(block -> block.confidence).average().orElse(0.0);
    }

    private static Double extractConfidence(ExtractResult result) {
        if (result.summary != null && result.summary.confidence > 0.0) {
            return result.summary.confidence;
        }
        if (result.tasks != null && !result.tasks.isEmpty()) {
            return result.tasks.stream().mapToDouble(task -> task.confidence).average().orElse(0.0);
        }
        return 0.0;
    }

    private static String firstEvidence(OcrResult ocr) {
        if (ocr == null || ocr.blocks == null) {
            return "";
        }
        return ocr.blocks.stream()
                .map(block -> block.text)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private static String blankAs(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message) ? message : exception.getClass().getSimpleName();
    }
}
