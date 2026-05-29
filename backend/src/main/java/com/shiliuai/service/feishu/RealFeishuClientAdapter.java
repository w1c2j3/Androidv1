package com.shiliuai.service.feishu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.config.ShiliuProperties;
import com.shiliuai.entity.BotConfigEntity;
import com.shiliuai.util.UrlStrings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RealFeishuClientAdapter implements FeishuClientAdapter {
    private final RestClient restClient;
    private final FeishuTokenProvider tokenService;
    private final ObjectMapper objectMapper;

    public RealFeishuClientAdapter(RestClient.Builder builder,
                                   ShiliuProperties properties,
                                   FeishuTokenProvider tokenService,
                                   ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(UrlStrings.trimTrailingSlash(properties.getFeishu().getApiBaseUrl(),
                "https://open.feishu.cn/open-apis")).build();
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String sendText(BotConfigEntity bot, String chatId, String text) {
        return send(bot, chatId, "text", Map.of("text", text));
    }

    @Override
    public String sendCard(BotConfigEntity bot, String chatId, Map<String, Object> card) {
        return send(bot, chatId, "interactive", card);
    }

    @Override
    public void updateCard(BotConfigEntity bot, String messageId, Map<String, Object> card) {
        String contentJson = toJson(card);
        JsonNode response = restClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/im/v1/messages/{messageId}")
                        .build(messageId))
                .header("Authorization", "Bearer " + tokenService.tenantAccessToken(bot))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", contentJson))
                .retrieve()
                .body(JsonNode.class);
        assertOk(response, "更新飞书消息卡片失败");
    }

    @Override
    public List<String> listRecentTextMessages(BotConfigEntity bot, String chatId, int limit) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/im/v1/messages")
                        .queryParam("container_id_type", "chat")
                        .queryParam("container_id", chatId)
                        .queryParam("sort_type", "ByCreateTimeDesc")
                        .queryParam("page_size", Math.max(1, Math.min(limit, 50)))
                        .build())
                .header("Authorization", "Bearer " + tokenService.tenantAccessToken(bot))
                .retrieve()
                .body(JsonNode.class);
        assertOk(response, "读取飞书历史消息失败");
        List<String> messages = new ArrayList<>();
        JsonNode items = response == null ? objectMapper.createArrayNode() : response.path("data").path("items");
        if (!items.isArray()) {
            return messages;
        }
        for (JsonNode item : items) {
            if (!"text".equals(item.path("msg_type").asText(item.path("message_type").asText("")))) {
                continue;
            }
            String content = item.path("body").path("content").asText(item.path("content").asText(""));
            String text = parseTextContent(content);
            if (!text.isBlank()) {
                messages.add(text);
            }
        }
        return messages;
    }

    private String send(BotConfigEntity bot, String chatId, String messageType, Object content) {
        String contentJson = toJson(content);
        JsonNode response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/im/v1/messages")
                        .queryParam("receive_id_type", "chat_id")
                        .build())
                .header("Authorization", "Bearer " + tokenService.tenantAccessToken(bot))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "receive_id", chatId,
                        "msg_type", messageType,
                        "content", contentJson,
                        "uuid", UUID.randomUUID().toString()
                ))
                .retrieve()
                .body(JsonNode.class);
        assertOk(response, "发送飞书消息失败");
        return response == null ? "" : response.path("data").path("message_id").asText("");
    }

    private String toJson(Object content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new FeishuApiException("序列化飞书消息失败", exception);
        }
    }

    private String parseTextContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(content);
            return node.path("text").asText("").trim();
        } catch (JsonProcessingException ignored) {
            return content.trim();
        }
    }

    private static void assertOk(JsonNode response, String message) {
        if (response == null || response.path("code").asInt(-1) != 0) {
            throw new FeishuApiException(message + "：" + (response == null ? "empty response" : response.toString()));
        }
    }
}
