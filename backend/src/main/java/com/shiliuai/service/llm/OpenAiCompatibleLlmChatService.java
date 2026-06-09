package com.shiliuai.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.shiliuai.config.ShiliuProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiCompatibleLlmChatService implements LlmChatService {
    private static final String SYSTEM_PROMPT = """
            你是视流 AI 的飞书助手。你可以：
            1. 回答项目、任务、截图整理相关问题；
            2. 提醒用户发送图片以触发 OCR 识别；
            3. 帮用户把文字整理成摘要、待办、风险和下一步。
            回复要简洁、直接，适合在飞书群里阅读。
            """;

    private final RestClient restClient;
    private final ShiliuProperties properties;

    public OpenAiCompatibleLlmChatService(RestClient.Builder builder, ShiliuProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(18));
        this.restClient = builder.requestFactory(factory).build();
        this.properties = properties;
    }

    @Override
    public String answerText(String userText) {
        ShiliuProperties.Llm llm = properties.getLlm();
        if (!llm.isEnabled() || !StringUtils.hasText(llm.getApiBaseUrl()) || !StringUtils.hasText(llm.getApiKey())) {
            return "我已收到消息。当前还没有配置大模型接口；发送图片可以直接触发 OCR 整理。";
        }
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(chatCompletionsUrl(llm.getApiBaseUrl()))
                    .header("Authorization", "Bearer " + llm.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", StringUtils.hasText(llm.getModel()) ? llm.getModel() : "deepseek-v3.2",
                            "messages", List.of(
                                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                                    Map.of("role", "user", "content", userText)
                            ),
                            "temperature", 0.4,
                            "max_tokens", 600
                    ))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException exception) {
            return "我已收到消息，但模型接口暂时不可用。演示保护已启用，可以继续使用 OCR、任务和项目分析。";
        }
        String answer = response == null
                ? ""
                : response.at("/choices/0/message/content").asText("");
        if (!StringUtils.hasText(answer)) {
            return "模型没有返回有效内容。你可以直接发送图片，我会先做 OCR 整理。";
        }
        return truncate(answer.trim(), 1400);
    }

    private static String chatCompletionsUrl(String apiBaseUrl) {
        String value = apiBaseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/v1")) {
            return value + "/chat/completions";
        }
        return value + "/v1/chat/completions";
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}
