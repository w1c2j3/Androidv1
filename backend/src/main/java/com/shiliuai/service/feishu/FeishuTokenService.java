package com.shiliuai.service.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.shiliuai.config.ShiliuProperties;
import com.shiliuai.entity.BotConfigEntity;
import com.shiliuai.service.bot.SecretEncryptor;
import com.shiliuai.util.UrlStrings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FeishuTokenService implements FeishuTokenProvider {
    private final RestClient restClient;
    private final SecretEncryptor secretEncryptor;
    private final Clock clock;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public FeishuTokenService(RestClient.Builder builder,
                              ShiliuProperties properties,
                              SecretEncryptor secretEncryptor,
                              Clock clock) {
        this.restClient = builder.baseUrl(UrlStrings.trimTrailingSlash(properties.getFeishu().getApiBaseUrl(),
                "https://open.feishu.cn/open-apis")).build();
        this.secretEncryptor = secretEncryptor;
        this.clock = clock;
    }

    @Override
    public String tenantAccessToken(BotConfigEntity bot) {
        CachedToken cachedToken = cache.get(bot.getId());
        Instant now = clock.instant();
        if (cachedToken != null && cachedToken.expiresAt.isAfter(now.plusSeconds(300))) {
            return cachedToken.token;
        }

        JsonNode response = restClient.post()
                .uri("/auth/v3/tenant_access_token/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "app_id", bot.getAppId(),
                        "app_secret", secretEncryptor.decrypt(bot.getEncryptedAppSecret())
                ))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.path("code").asInt(-1) != 0) {
            throw new FeishuApiException("获取 tenant_access_token 失败：" + (response == null ? "empty response" : response.toString()));
        }
        String token = response.path("tenant_access_token").asText("");
        int expire = response.path("expire").asInt(7200);
        if (token.isBlank()) {
            throw new FeishuApiException("获取 tenant_access_token 失败：响应中没有 token");
        }
        cache.put(bot.getId(), new CachedToken(token, now.plusSeconds(Math.max(60, expire))));
        return token;
    }
    private record CachedToken(String token, Instant expiresAt) {
    }
}
