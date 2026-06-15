package com.shiliuai.service.feishu;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FeishuActionResponseAdapter {
    public Map<String, Object> toast(String type, String content) {
        return Map.of("toast", Map.of("type", type, "content", content));
    }

    public Map<String, Object> card(Map<String, Object> card) {
        return Map.of("card", card);
    }

    public Map<String, Object> toastAndCard(String type, String content, Map<String, Object> card) {
        return Map.of("toast", Map.of("type", type, "content", content), "card", card);
    }
}
