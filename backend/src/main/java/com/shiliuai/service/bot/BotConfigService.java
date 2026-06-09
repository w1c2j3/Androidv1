package com.shiliuai.service.bot;

import com.shiliuai.config.ShiliuProperties;
import com.shiliuai.dto.BotCallbackConfigUpdateRequest;
import com.shiliuai.dto.BotHealthResponse;
import com.shiliuai.dto.BotRegisterRequest;
import com.shiliuai.dto.BotRegisterResponse;
import com.shiliuai.entity.BotConfigEntity;
import com.shiliuai.repository.BotConfigRepository;
import com.shiliuai.service.feishu.FeishuTokenProvider;
import com.shiliuai.util.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class BotConfigService {
    private final BotConfigRepository repository;
    private final SecretEncryptor secretEncryptor;
    private final FeishuTokenProvider feishuTokenProvider;
    private final ShiliuProperties properties;
    private final Clock clock;

    public BotConfigService(BotConfigRepository repository,
                            SecretEncryptor secretEncryptor,
                            FeishuTokenProvider feishuTokenProvider,
                            ShiliuProperties properties,
                            Clock clock) {
        this.repository = repository;
        this.secretEncryptor = secretEncryptor;
        this.feishuTokenProvider = feishuTokenProvider;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public BotRegisterResponse register(BotRegisterRequest request) {
        Instant now = clock.instant();
        BotConfigEntity entity = new BotConfigEntity();
        entity.setId(Ids.botId(clock));
        entity.setBotName(request.botName);
        entity.setAppId(request.appId);
        entity.setEncryptedAppSecret(secretEncryptor.encrypt(request.appSecret));
        entity.setVerificationToken(request.verificationToken);
        entity.setEncryptKey(request.encryptKey);
        entity.setTenantName(request.tenantName);
        entity.setStatus("registered");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        verifyTenantToken(entity);
        repository.save(entity);

        BotRegisterResponse response = new BotRegisterResponse();
        response.botId = entity.getId();
        response.botName = entity.getBotName();
        response.status = entity.getStatus();
        response.callbackUrl = baseUrl() + "/feishu/events/" + entity.getId();
        response.cardCallbackUrl = baseUrl() + "/feishu/card-callback/" + entity.getId();
        response.requiredPermissions = List.of(
                "im:message:send_as_bot",
                "im:message:receive_v1",
                "im:message:readonly",
                "im:resource:read"
        );
        response.nextStep = "请到飞书开放平台配置事件订阅 URL，并启用机器人能力。";
        return response;
    }

    @Transactional(readOnly = true)
    public BotHealthResponse health(String botId) {
        BotConfigEntity entity = getRequired(botId);
        BotHealthResponse response = new BotHealthResponse();
        response.botId = entity.getId();
        response.registered = true;
        response.tokenValid = hasCredentials(entity) && canFetchTenantToken(entity, response);
        response.eventCallbackVerified = entity.getLastEventAt() != null;
        response.lastEventAt = entity.getLastEventAt() == null ? null : entity.getLastEventAt().toString();
        response.lastMessageText = entity.getLastMessageText();
        response.lastReplyAt = entity.getLastReplyAt() == null ? null : entity.getLastReplyAt().toString();
        response.lastReplyError = entity.getLastReplyError();
        if (!response.tokenValid) {
            response.status = "error";
            response.nextStep = "请检查飞书 App ID、App Secret 和应用可用状态。";
        } else {
            response.status = response.eventCallbackVerified ? "ready" : "waiting_event";
            response.nextStep = response.eventCallbackVerified
                    ? "请在飞书群里发送一张截图并 @视流助手 整理。"
                    : "请在飞书测试群发送：@视流助手 /ping。";
        }
        return response;
    }

    @Transactional(readOnly = true)
    public BotConfigEntity getRequired(String botId) {
        return repository.findById(botId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "机器人配置不存在"));
    }

    @Transactional
    public BotHealthResponse updateCallbackConfig(String botId, BotCallbackConfigUpdateRequest request) {
        BotConfigEntity bot = getRequired(botId);
        bot.setVerificationToken(request.verificationToken);
        bot.setEncryptKey(request.encryptKey);
        bot.setUpdatedAt(clock.instant());
        repository.save(bot);
        return health(botId);
    }

    @Transactional
    public void markEventReceived(BotConfigEntity bot, String lastMessageText) {
        bot.setLastEventAt(clock.instant());
        bot.setLastMessageText(lastMessageText);
        bot.setUpdatedAt(clock.instant());
        repository.save(bot);
    }

    @Transactional
    public void markReplySucceeded(String botId) {
        BotConfigEntity bot = getRequired(botId);
        bot.setLastReplyAt(clock.instant());
        bot.setLastReplyError(null);
        bot.setUpdatedAt(clock.instant());
        repository.save(bot);
    }

    @Transactional
    public void markReplyFailed(String botId, String errorMessage) {
        BotConfigEntity bot = getRequired(botId);
        bot.setLastReplyAt(clock.instant());
        bot.setLastReplyError(limit(errorMessage, 1000));
        bot.setUpdatedAt(clock.instant());
        repository.save(bot);
    }

    private String baseUrl() {
        String value = properties.getPublicBaseUrl();
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private void verifyTenantToken(BotConfigEntity entity) {
        if (!hasCredentials(entity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书 App ID 和 App Secret 不能为空");
        }
        try {
            feishuTokenProvider.tenantAccessToken(entity);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书凭证校验失败：" + exception.getMessage(), exception);
        }
    }

    private boolean canFetchTenantToken(BotConfigEntity entity, BotHealthResponse response) {
        try {
            feishuTokenProvider.tenantAccessToken(entity);
            return true;
        } catch (RuntimeException exception) {
            response.tokenError = exception.getMessage();
            return false;
        }
    }

    private static boolean hasCredentials(BotConfigEntity entity) {
        return StringUtils.hasText(entity.getAppId()) && StringUtils.hasText(entity.getEncryptedAppSecret());
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}
