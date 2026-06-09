package com.shiliuai.controller;

import com.shiliuai.dto.BotCallbackConfigUpdateRequest;
import com.shiliuai.dto.BotHealthResponse;
import com.shiliuai.dto.BotRegisterRequest;
import com.shiliuai.dto.BotRegisterResponse;
import com.shiliuai.service.bot.BotConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bots")
public class BotAdminController {
    private final BotConfigService botConfigService;

    public BotAdminController(BotConfigService botConfigService) {
        this.botConfigService = botConfigService;
    }

    @PostMapping("/register")
    public BotRegisterResponse register(@Valid @RequestBody BotRegisterRequest request) {
        return botConfigService.register(request);
    }

    @GetMapping("/{botId}/health")
    public BotHealthResponse health(@PathVariable String botId) {
        return botConfigService.health(botId);
    }

    @PatchMapping("/{botId}/callback-config")
    public BotHealthResponse updateCallbackConfig(@PathVariable String botId,
                                                  @Valid @RequestBody BotCallbackConfigUpdateRequest request) {
        return botConfigService.updateCallbackConfig(botId, request);
    }
}
