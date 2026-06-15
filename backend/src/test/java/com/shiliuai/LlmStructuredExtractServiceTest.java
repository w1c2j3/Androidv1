package com.shiliuai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.dto.ExtractRequest;
import com.shiliuai.dto.ExtractResult;
import com.shiliuai.dto.OcrBlock;
import com.shiliuai.dto.OcrResult;
import com.shiliuai.service.extract.LlmStructuredExtractService;
import com.shiliuai.service.extract.RuleBasedExtractService;
import com.shiliuai.service.llm.LlmChatService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmStructuredExtractServiceTest {
    @Test
    void llmJsonSuccessProducesStructuredResult() {
        FakeLlm llm = new FakeLlm(true, """
                {
                  "summary":{"title":"权限方案","bullets":["整理权限方案。"],"confidence":0.91},
                  "tasks":[{"title":"整理权限方案","priority":"high","confidence":0.88,"evidence":"明天下班前把权限方案整理一下"}],
                  "dailyReportMaterials":["完成权限方案梳理"],
                  "links":[],
                  "riskFlags":[]
                }
                """);
        ExtractResult result = service(llm).extract(request());

        assertThat(result.extractMode).isEqualTo("llm");
        assertThat(result.summary.title).isEqualTo("权限方案");
        assertThat(result.tasks).hasSize(1);
        assertThat(result.tasks.get(0).tempId).isEqualTo("task_tmp_1");
        assertThat(result.tasks.get(0).evidence).contains("明天下班前");
    }

    @Test
    void malformedJsonIsRepairedOnce() {
        FakeLlm llm = new FakeLlm(true,
                "```json\n{\"summary\": bad\n```",
                """
                        {
                          "summary":{"title":"修复后摘要","bullets":["已修复。"],"confidence":0.7},
                          "tasks":[],
                          "links":[],
                          "dailyReportMaterials":[],
                          "riskFlags":[]
                        }
                        """);
        ExtractResult result = service(llm).extract(request());

        assertThat(result.extractMode).isEqualTo("llm_repaired");
        assertThat(result.summary.title).isEqualTo("修复后摘要");
        assertThat(result.extractError).contains("首次 JSON 解析失败");
    }

    @Test
    void unavailableLlmFallsBackToRules() {
        ExtractResult result = service(new FakeLlm(false)).extract(request());

        assertThat(result.extractMode).isEqualTo("rule_fallback");
        assertThat(result.extractError).contains("LLM 未配置");
        assertThat(result.tasks).isNotEmpty();
    }

    private static LlmStructuredExtractService service(FakeLlm llm) {
        return new LlmStructuredExtractService(llm, new RuleBasedExtractService(), new ObjectMapper());
    }

    private static ExtractRequest request() {
        OcrBlock block = new OcrBlock();
        block.id = "b1";
        block.text = "明天下班前把权限方案整理一下";
        block.confidence = 0.94;

        OcrResult ocr = new OcrResult();
        ocr.traceId = "trace_test";
        ocr.plainText = block.text;
        ocr.blocks = List.of(block);
        ocr.averageConfidence = 0.94;

        ExtractRequest request = new ExtractRequest();
        request.traceId = "trace_test";
        request.scene = "chat_screenshot";
        request.plainText = ocr.plainText;
        request.ocrResult = ocr;
        return request;
    }

    private static class FakeLlm implements LlmChatService {
        private final boolean available;
        private final ArrayDeque<String> responses = new ArrayDeque<>();

        FakeLlm(boolean available, String... responses) {
            this.available = available;
            this.responses.addAll(List.of(responses));
        }

        @Override
        public String answerText(String userText) {
            return "";
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String modelName() {
            return "test-model";
        }

        @Override
        public String chatJson(String systemPrompt, String userPrompt) {
            if (!available) {
                throw new IllegalStateException("disabled");
            }
            return responses.removeFirst();
        }

        @Override
        public boolean ping() {
            return available;
        }
    }
}
