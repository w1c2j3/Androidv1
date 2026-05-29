package com.shiliuai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.shiliuai.service.feishu.FeishuCardCallbackService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeishuCardCallbackController {
    private final FeishuCardCallbackService feishuCardCallbackService;

    public FeishuCardCallbackController(FeishuCardCallbackService feishuCardCallbackService) {
        this.feishuCardCallbackService = feishuCardCallbackService;
    }

    @PostMapping("/feishu/card-callback/{botId}")
    public Object callback(@PathVariable String botId, @RequestBody JsonNode body) {
        return feishuCardCallbackService.handleCallback(botId, body);
    }
}
