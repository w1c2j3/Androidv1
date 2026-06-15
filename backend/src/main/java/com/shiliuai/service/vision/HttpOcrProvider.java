package com.shiliuai.service.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.config.ShiliuProperties;
import com.shiliuai.dto.OcrBlock;
import com.shiliuai.dto.OcrRequest;
import com.shiliuai.dto.OcrResult;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HttpOcrProvider implements OcrProvider {
    private final RestClient restClient;
    private final ShiliuProperties properties;
    private final ObjectMapper objectMapper;

    public HttpOcrProvider(RestClient.Builder builder, ShiliuProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        // OCR 服务真实端到端 30-90s（含 PaddleOCR 推理 + 大图）；旧值 75s 偏紧。
        // 拉到 95s 给 PP-OCRv5 + 5MB 图留余量，仍小于 Cloudflare quick tunnel 100s 窗口。
        factory.setReadTimeout(Duration.ofSeconds(95));
        this.restClient = builder.requestFactory(factory).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public OcrResult recognize(OcrRequest request) {
        Path imagePath = Path.of(request.imagePath);
        if (!Files.isRegularFile(imagePath)) {
            throw new IllegalStateException("OCR 图片文件不存在：" + request.imagePath);
        }

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new FileSystemResource(imagePath));
        form.add("traceId", request.traceId);
        form.add("source", request.source == null ? "" : request.source);
        form.add("hints", toJson(request.hints));

        JsonNode response = restClient.post()
                .uri(properties.getOcr().getHttpEndpoint())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("OCR HTTP 服务返回空响应");
        }
        JsonNode payload = response.has("data") && response.path("data").isObject() ? response.path("data") : response;
        return toOcrResult(payload, request.traceId);
    }

    private OcrResult toOcrResult(JsonNode node, String fallbackTraceId) {
        OcrResult result = new OcrResult();
        result.traceId = text(node, "traceId", fallbackTraceId);
        result.imageType = text(node, "imageType", "unknown");
        result.engine = text(node, "engine", null);
        result.engineVersion = text(node, "engineVersion", null);
        result.modelProfile = text(node, "modelProfile", null);
        result.lang = text(node, "lang", null);
        result.latencyMs = node.hasNonNull("latencyMs") ? node.path("latencyMs").asLong() : null;
        result.averageConfidence = node.hasNonNull("averageConfidence") ? node.path("averageConfidence").asDouble() : null;
        result.minConfidence = node.hasNonNull("minConfidence") ? node.path("minConfidence").asDouble() : null;
        result.width = node.path("width").asInt(0);
        result.height = node.path("height").asInt(0);
        result.plainText = firstText(node, List.of("plainText", "plain_text", "text", "ocrText"));
        result.blocks = parseBlocks(node.path("blocks"));
        result.quality = parseObject(node.path("quality"));
        fillConfidence(result);

        if ((result.plainText == null || result.plainText.isBlank()) && !result.blocks.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (OcrBlock block : result.blocks) {
                if (block.text != null && !block.text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(block.text);
                }
            }
            result.plainText = builder.toString();
        }
        if (result.plainText == null) {
            result.plainText = "";
        }
        return result;
    }

    private static void fillConfidence(OcrResult result) {
        if (result.blocks == null || result.blocks.isEmpty()) {
            return;
        }
        if (result.averageConfidence == null) {
            result.averageConfidence = result.blocks.stream()
                    .mapToDouble(block -> block.confidence)
                    .average()
                    .orElse(0.0);
        }
        if (result.minConfidence == null) {
            result.minConfidence = result.blocks.stream()
                    .mapToDouble(block -> block.confidence)
                    .min()
                    .orElse(0.0);
        }
    }

    private List<OcrBlock> parseBlocks(JsonNode blocksNode) {
        if (!blocksNode.isArray()) {
            return List.of();
        }
        List<OcrBlock> blocks = new ArrayList<>();
        int index = 1;
        for (JsonNode item : blocksNode) {
            OcrBlock block = new OcrBlock();
            block.id = text(item, "id", "b" + index++);
            block.type = text(item, "type", "text_line");
            block.text = firstText(item, List.of("text", "plainText", "value"));
            block.bbox = parseBbox(item.path("bbox"));
            block.confidence = item.path("confidence").asDouble(0.0);
            blocks.add(block);
        }
        return blocks;
    }

    private static int[] parseBbox(JsonNode bboxNode) {
        if (!bboxNode.isArray() || bboxNode.size() < 4) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{
                bboxNode.get(0).asInt(),
                bboxNode.get(1).asInt(),
                bboxNode.get(2).asInt(),
                bboxNode.get(3).asInt()
        };
    }

    private static Map<String, Object> parseObject(JsonNode node) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!node.isObject()) {
            return values;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (value.isNumber()) {
                values.put(field.getKey(), value.asDouble());
            } else if (value.isBoolean()) {
                values.put(field.getKey(), value.asBoolean());
            } else {
                values.put(field.getKey(), value.asText());
            }
        }
        return values;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private static String firstText(JsonNode node, List<String> names) {
        for (String name : names) {
            String value = node.path(name).asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String name, String fallback) {
        String value = node.path(name).asText(null);
        return value == null || value.isBlank() ? fallback : value;
    }
}
