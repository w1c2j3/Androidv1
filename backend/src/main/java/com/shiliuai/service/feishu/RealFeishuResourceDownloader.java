package com.shiliuai.service.feishu;

import com.shiliuai.config.ShiliuProperties;
import com.shiliuai.dto.FileRef;
import com.shiliuai.entity.BotConfigEntity;
import com.shiliuai.service.storage.FileStorageService;
import com.shiliuai.util.UrlStrings;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class RealFeishuResourceDownloader implements FeishuResourceDownloader {
    private final RestClient restClient;
    private final FeishuTokenProvider tokenService;
    private final FileStorageService fileStorageService;

    public RealFeishuResourceDownloader(RestClient.Builder builder,
                                        ShiliuProperties properties,
                                        FeishuTokenProvider tokenService,
                                        FileStorageService fileStorageService) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = builder.requestFactory(factory).baseUrl(UrlStrings.trimTrailingSlash(properties.getFeishu().getApiBaseUrl(),
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
        MediaType contentType = response.getHeaders().getContentType();
        String mimeType = contentType == null ? "application/octet-stream" : contentType.toString();
        String extension = pickExtension(contentType, body);
        return fileStorageService.storeBytes(imageKey + extension, mimeType, body);
    }

    /**
     * 优先用响应 Content-Type，其次嗅探文件头 magic bytes，最终回退到 jpg。
     *
     * 直接把图片以 .bin 落地会触发 PaddleOCR "Not supported input file type"，
     * 所以这里必须返回 OCR 服务认识的扩展名。
     */
    private static String pickExtension(MediaType contentType, byte[] body) {
        if (contentType != null) {
            String subtype = contentType.getSubtype() == null ? "" : contentType.getSubtype().toLowerCase();
            switch (subtype) {
                case "jpeg":
                case "jpg":
                    return ".jpg";
                case "png":
                    return ".png";
                case "webp":
                    return ".webp";
                case "bmp":
                    return ".bmp";
                case "gif":
                    return ".gif";
                case "tiff":
                case "tif":
                    return ".tif";
                default:
                    // fall through to magic byte sniff
            }
        }
        return sniffExtension(body);
    }

    private static String sniffExtension(byte[] body) {
        if (body == null || body.length < 4) {
            return ".jpg";
        }
        // JPEG: FF D8 FF
        if ((body[0] & 0xFF) == 0xFF && (body[1] & 0xFF) == 0xD8 && (body[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        // PNG: 89 50 4E 47
        if ((body[0] & 0xFF) == 0x89 && body[1] == 'P' && body[2] == 'N' && body[3] == 'G') {
            return ".png";
        }
        // GIF: GIF87a or GIF89a
        if (body.length >= 6 && body[0] == 'G' && body[1] == 'I' && body[2] == 'F') {
            return ".gif";
        }
        // BMP: BM
        if (body[0] == 'B' && body[1] == 'M') {
            return ".bmp";
        }
        // WEBP: RIFF....WEBP
        if (body.length >= 12 && body[0] == 'R' && body[1] == 'I' && body[2] == 'F' && body[3] == 'F'
                && body[8] == 'W' && body[9] == 'E' && body[10] == 'B' && body[11] == 'P') {
            return ".webp";
        }
        // TIFF: II*\0 or MM\0*
        if ((body[0] == 'I' && body[1] == 'I' && (body[2] & 0xFF) == 0x2A && body[3] == 0)
                || (body[0] == 'M' && body[1] == 'M' && body[2] == 0 && (body[3] & 0xFF) == 0x2A)) {
            return ".tif";
        }
        // 默认按 JPEG 落地：飞书图片绝大多数都是 jpeg
        return ".jpg";
    }
}
