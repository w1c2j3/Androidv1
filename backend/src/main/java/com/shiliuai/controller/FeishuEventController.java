package com.shiliuai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.shiliuai.service.feishu.FeishuEventService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeishuEventController {
    private final FeishuEventService feishuEventService;

    public FeishuEventController(FeishuEventService feishuEventService) {
        this.feishuEventService = feishuEventService;
    }

    @PostMapping("/feishu/events/{botId}")
    public Object receive(@PathVariable String botId, @RequestBody JsonNode body) {
        return feishuEventService.handleEvent(botId, body);
    }
}
