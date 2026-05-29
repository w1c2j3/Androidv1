package com.shiliuai.service.feishu;

import com.shiliuai.config.ShiliuProperties;
import com.shiliuai.dto.FileRef;
import com.shiliuai.entity.BotConfigEntity;
import com.shiliuai.service.storage.FileStorageService;
import com.shiliuai.util.UrlStrings;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RealFeishuResourceDownloader implements FeishuResourceDownloader {
    private final RestClient restClient;
    private final FeishuTokenProvider tokenService;
    private final FileStorageService fileStorageService;

    public RealFeishuResourceDownloader(RestClient.Builder builder,
                                        ShiliuProperties properties,
                                        FeishuTokenProvider tokenService,
                                        FileStorageService fileStorageService) {
        this.restClient = builder.baseUrl(UrlStrings.trimTrailingSlash(properties.getFeishu().getApiBaseUrl(),
                "https://open.feishu.cn/open-apis")).build();
        this.tokenService = tokenService;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public FileRef downloadImage(BotConfigEntity bot, String messageId, String imageKey) {
        ResponseEntity<byte[]> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/im/v1/messages/{messageId}/resources/{fileKey}")
                        .queryParam("type", "image")
                        .build(messageId, imageKey))
                .header("Authorization", "Bearer " + tokenService.tenantAccessToken(bot))
                .retrieve()
                .toEntity(byte[].class);
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new FeishuApiException("飞书图片资源下载失败：响应为空");
        }
        String mimeType = response.getHeaders().getContentType() == null
                ? "application/octet-stream"
                : response.getHeaders().getContentType().toString();
        return fileStorageService.storeBytes(imageKey + ".bin", mimeType, body);
    }
}
