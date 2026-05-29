package com.shiliuai.service.setup;

import com.shiliuai.config.ShiliuProperties;
import com.shiliuai.dto.BotHealthResponse;
import com.shiliuai.dto.SetupReadinessResponse;
import com.shiliuai.entity.BotConfigEntity;
import com.shiliuai.repository.BotConfigRepository;
import com.shiliuai.service.bot.BotConfigService;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

@Service
public class SetupReadinessService {
    private final ShiliuProperties properties;
    private final BotConfigRepository botConfigRepository;
    private final BotConfigService botConfigService;
    private final RestClient restClient;

    public SetupReadinessService(ShiliuProperties properties,
                                 BotConfigRepository botConfigRepository,
                                 BotConfigService botConfigService) {
        this.properties = properties;
        this.botConfigRepository = botConfigRepository;
        this.botConfigService = botConfigService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(800));
        factory.setReadTimeout(Duration.ofMillis(800));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public SetupReadinessResponse readiness() {
        SetupReadinessResponse response = new SetupReadinessResponse();
        response.backendOk = true;
        response.ocrEndpoint = properties.getOcr().getHttpEndpoint();
        response.ocrConfigured = StringUtils.hasText(response.ocrEndpoint);
        response.ocrHealthy = response.ocrConfigured && isOcrHealthy(response.ocrEndpoint);

        botConfigRepository.findFirstByOrderByCreatedAtDesc().ifPresent(bot -> fillBotStatus(response, bot));

        response.ready = response.backendOk
                && response.ocrHealthy
                && response.botRegistered
                && response.tokenValid
                && response.eventCallbackVerified;
        response.nextStep = nextStep(response);
        return response;
    }

    private void fillBotStatus(SetupReadinessResponse response, BotConfigEntity bot) {
        response.botRegistered = true;
        response.botId = bot.getId();
        response.callbackUrl = baseUrl() + "/feishu/events/" + bot.getId();
        response.cardCallbackUrl = baseUrl() + "/feishu/card-callback/" + bot.getId();
        BotHealthResponse health = botConfigService.health(bot.getId());
        response.botStatus = health.status;
        response.tokenValid = health.tokenValid;
        response.eventCallbackVerified = health.eventCallbackVerified;
    }

    private boolean isOcrHealthy(String ocrEndpoint) {
        try {
            URI healthUri = healthUri(ocrEndpoint);
            restClient.get().uri(healthUri).retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static URI healthUri(String ocrEndpoint) {
        URI uri = URI.create(ocrEndpoint);
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            path = "/health";
        } else {
            int slash = path.lastIndexOf('/');
            path = (slash <= 0 ? "" : path.substring(0, slash)) + "/health";
        }
        return URI.create(uri.getScheme() + "://" + uri.getAuthority() + path);
    }

    private String baseUrl() {
        String value = properties.getPublicBaseUrl();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String nextStep(SetupReadinessResponse response) {
        if (!response.ocrHealthy) {
            return "请先启动 OCR 服务。";
        }
        if (!response.botRegistered) {
            return "请填写飞书 App ID、App Secret 和 Verification Token 注册机器人。";
        }
        if (!response.tokenValid) {
            return "请检查飞书 App ID 和 App Secret。";
        }
        if (!response.eventCallbackVerified) {
            return "请在飞书开放平台配置事件回调后，在群里发送 @视流助手 /ping。";
        }
        return "已就绪，可以在飞书群发送图片并 @视流助手 整理。";
    }
}
