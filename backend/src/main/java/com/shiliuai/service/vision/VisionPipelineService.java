package com.shiliuai.service.vision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.dto.ExtractRequest;
import com.shiliuai.dto.ExtractResult;
import com.shiliuai.dto.FileRef;
import com.shiliuai.dto.OcrRequest;
import com.shiliuai.dto.OcrResult;
import com.shiliuai.dto.VisionResultResponse;
import com.shiliuai.dto.VisionTraceListResponse;
import com.shiliuai.dto.VisionTraceSummaryDto;
import com.shiliuai.entity.ReportMaterialEntity;
import com.shiliuai.entity.VisionTraceEntity;
import com.shiliuai.repository.ReportMaterialRepository;
import com.shiliuai.repository.VisionTraceRepository;
import com.shiliuai.service.extract.ExtractService;
import com.shiliuai.service.storage.FileStorageService;
import com.shiliuai.util.Ids;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
public class VisionPipelineService {
    private final FileStorageService fileStorageService;
    private final OcrProvider ocrProvider;
    private final SceneClassifier sceneClassifier;
    private final ExtractService extractService;
    private final VisionTraceRepository traceRepository;
    private final ReportMaterialRepository reportMaterialRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TaskExecutor visionTaskExecutor;

    public VisionPipelineService(FileStorageService fileStorageService,
                                 OcrProvider ocrProvider,
                                 SceneClassifier sceneClassifier,
                                 ExtractService extractService,
                                 VisionTraceRepository traceRepository,
                                 ReportMaterialRepository reportMaterialRepository,
                                 ObjectMapper objectMapper,
                                 Clock clock,
                                 @Qualifier("visionTaskExecutor") TaskExecutor visionTaskExecutor) {
        this.fileStorageService = fileStorageService;
        this.ocrProvider = ocrProvider;
        this.sceneClassifier = sceneClassifier;
        this.extractService = extractService;
        this.traceRepository = traceRepository;
        this.reportMaterialRepository = reportMaterialRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.visionTaskExecutor = visionTaskExecutor;
    }

    public VisionTraceEntity startUpload(MultipartFile file, String source, String sceneHint) {
        FileRef fileRef = fileStorageService.store(file);
        VisionTraceEntity trace = createTrace(source, fileRef.localPath);
        submitProcessingJob(trace.getTraceId(), () ->
                processExistingTrace(trace.getTraceId(), fileRef, trace.getSource(), sceneHint));
        return trace;
    }

    public VisionTraceEntity startFileProcessing(FileRef fileRef, String source, String sceneHint) {
        VisionTraceEntity trace = createTrace(source, fileRef.localPath);
        submitProcessingJob(trace.getTraceId(), () ->
                processExistingTrace(trace.getTraceId(), fileRef, trace.getSource(), sceneHint));
        return trace;
    }

    public VisionTraceEntity createPendingTrace(String source) {
        return createTrace(source, null);
    }

    public void markProcessing(String traceId, String stage, int progress, String message) {
        updateTrace(traceId, trace -> {
            trace.setStatus("processing");
            trace.setStage(stage);
            trace.setProgress(progress);
            trace.setStatusMessage(message);
        });
    }

    public void markError(String traceId, String errorCode, String errorMessage) {
        String safeErrorMessage = limit(errorMessage, 240);
        updateTrace(traceId, trace -> {
            trace.setStatus("error");
            trace.setStage("error");
            trace.setProgress(100);
            trace.setErrorCode(errorCode);
            trace.setErrorMessage(safeErrorMessage);
            trace.setStatusMessage(safeErrorMessage);
        });
    }

    public void processExistingTrace(String traceId, FileRef fileRef, String source, String sceneHint) {
        processExistingTrace(traceId, fileRef, source, sceneHint, VisionProgressListener.noop());
    }

    public void processExistingTrace(String traceId,
                                     FileRef fileRef,
                                     String source,
                                     String sceneHint,
                                     VisionProgressListener progressListener) {
        String safeSource = source == null || source.isBlank() ? "unknown" : source;
        VisionProgressListener listener = progressListener == null ? VisionProgressListener.noop() : progressListener;
        try {
            updateProcessingStage(traceId, "resource_downloaded", 20, "图片资源已保存，准备进行 OCR", listener,
                    trace -> trace.setImagePath(fileRef.localPath));

            updateProcessingStage(traceId, "ocr", 45, "正在进行 OCR 识别", listener);

            OcrResult ocrResult = recognize(traceId, fileRef, safeSource, sceneHint);
            normalizeOcrResult(ocrResult);

            updateProcessingStage(traceId, "ocr_done", 65, "OCR 已完成，正在判断图片类型", listener);
            String scene = sceneClassifier.classify(ocrResult, sceneHint);

            updateProcessingStage(traceId, "structuring", 80, "正在抽取摘要、任务和链接", listener);
            ExtractResult extractResult = extract(traceId, scene, ocrResult);
            saveReportMaterials(traceId, extractResult);

            String ocrJson = objectMapper.writeValueAsString(ocrResult);
            String extractJson = objectMapper.writeValueAsString(extractResult);
            updateTrace(traceId, trace -> {
                trace.setScene(scene);
                trace.setOcrJson(ocrJson);
                trace.setExtractJson(extractJson);
                trace.setOcrEngine(ocrResult.engine);
                trace.setOcrConfidence(ocrResult.averageConfidence);
                trace.setExtractMode(extractResult.extractMode);
                trace.setLlmModel(extractResult.llmModel);
                trace.setExtractConfidence(extractResult.extractConfidence);
                trace.setExtractError(extractResult.extractError);
                trace.setStatus("done");
                trace.setStage("done");
                trace.setProgress(100);
                trace.setStatusMessage("识别完成");
                trace.setErrorCode(null);
                trace.setErrorMessage(null);
            });
            listener.onProgress(traceId, "done", 100, "识别完成");
        } catch (Exception exception) {
            String message = "OCR 或抽取失败：" + safeMessage(exception);
            markError(traceId, "OCR_FAILED", message);
            listener.onProgress(traceId, "error", 100, message);
        }
    }

    private VisionTraceEntity createTrace(String source, String imagePath) {
        String safeSource = source == null || source.isBlank() ? "unknown" : source;
        String traceId = Ids.traceId(clock, safeSource);
        Instant now = clock.instant();
        VisionTraceEntity trace = new VisionTraceEntity();
        trace.setTraceId(traceId);
        trace.setSource(safeSource);
        trace.setStatus("processing");
        trace.setStage("received");
        trace.setProgress(5);
        trace.setStatusMessage("已接收图片，等待后台识别");
        trace.setImagePath(imagePath);
        trace.setCreatedAt(now);
        trace.setUpdatedAt(now);
        return traceRepository.save(trace);
    }

    private void submitProcessingJob(String traceId, Runnable job) {
        try {
            visionTaskExecutor.execute(job);
        } catch (TaskRejectedException exception) {
            markError(traceId, "VISION_QUEUE_FULL", "演示保护：OCR 队列已满，本次图片没有进入后台处理，请稍后重试。");
        }
    }

    private OcrResult recognize(String traceId, FileRef fileRef, String source, String sceneHint) {
        OcrRequest ocrRequest = new OcrRequest();
        ocrRequest.traceId = traceId;
        ocrRequest.imageFileId = fileRef.localFileId;
        ocrRequest.imagePath = fileRef.localPath;
        ocrRequest.source = source;
        ocrRequest.hints = Map.of(
                "expectedScene", sceneHint == null ? "auto" : sceneHint,
                "language", "zh-CN",
                "enableTable", true,
                "enableChatBubble", true
        );
        return ocrProvider.recognize(ocrRequest);
    }

    private ExtractResult extract(String traceId, String scene, OcrResult ocrResult) {
        ExtractRequest extractRequest = new ExtractRequest();
        extractRequest.traceId = traceId;
        extractRequest.scene = scene;
        extractRequest.plainText = ocrResult.plainText;
        extractRequest.ocrResult = ocrResult;
        extractRequest.options = Map.of(
                "extractTasks", true,
                "extractLinks", true,
                "generateDailyReportMaterial", true,
                "language", "zh-CN"
        );
        return extractService.extract(extractRequest);
    }

    public VisionResultResponse getResult(String traceId) {
        VisionTraceEntity trace = traceRepository.findById(traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "识别 trace 不存在"));
        VisionResultResponse response = new VisionResultResponse();
        response.traceId = trace.getTraceId();
        response.status = trace.getStatus();
        response.scene = trace.getScene();
        response.stage = trace.getStage();
        response.progress = trace.getProgress() == null ? 0 : trace.getProgress();
        response.message = trace.getStatusMessage();
        response.errorCode = trace.getErrorCode();
        if ("processing".equals(trace.getStatus())) {
            if (response.message == null) {
                response.message = "正在处理图片";
            }
            return response;
        }
        if ("error".equals(trace.getStatus())) {
            response.stage = "error";
            response.progress = 100;
            response.message = trace.getErrorMessage();
            return response;
        }
        OcrResult ocrResult = read(trace.getOcrJson(), OcrResult.class);
        ExtractResult extractResult = read(trace.getExtractJson(), ExtractResult.class);
        response.stage = trace.getStage() == null ? "done" : trace.getStage();
        response.progress = trace.getProgress() == null ? 100 : trace.getProgress();
        response.message = trace.getStatusMessage() == null ? "识别完成" : trace.getStatusMessage();
        response.summary = extractResult.summary;
        response.tasks = extractResult.tasks;
        response.links = extractResult.links;
        response.dailyReportMaterials = extractResult.dailyReportMaterials;
        response.riskFlags = extractResult.riskFlags;
        response.extractMode = extractResult.extractMode;
        response.llmModel = extractResult.llmModel;
        response.ocrConfidence = extractResult.ocrConfidence;
        response.extractConfidence = extractResult.extractConfidence;
        response.extractError = extractResult.extractError;
        VisionResultResponse.OcrPreview preview = new VisionResultResponse.OcrPreview();
        preview.plainText = ocrResult.plainText;
        preview.engine = ocrResult.engine;
        preview.engineVersion = ocrResult.engineVersion;
        preview.modelProfile = ocrResult.modelProfile;
        preview.lang = ocrResult.lang;
        preview.latencyMs = ocrResult.latencyMs;
        preview.averageConfidence = ocrResult.averageConfidence;
        preview.minConfidence = ocrResult.minConfidence;
        preview.blockCount = ocrResult.blocks == null ? 0 : ocrResult.blocks.size();
        preview.width = ocrResult.width;
        preview.height = ocrResult.height;
        preview.blocks = ocrResult.blocks == null ? java.util.List.of() : ocrResult.blocks;
        preview.quality = ocrResult.quality == null ? java.util.Map.of() : ocrResult.quality;
        response.ocr = preview;
        return response;
    }

    /**
     * 仅重跑结构化抽取阶段：保留持久化的 OCR JSON，重新调用 ExtractService（默认 LLM）。
     *
     * 修复点：以前前端用 /agent/runs + /digest 重跑 LLM，导致输入里混着上一轮的错误 JSON，
     * LLM 把错误一起总结，输出"Codex 524"之类的误导文本。
     * 改成专用端点后，重抽取只走 OCR 原文 → ExtractService，无任何错误上下文污染。
     */
    public VisionResultResponse reextract(String traceId) {
        VisionTraceEntity trace = traceRepository.findById(traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "识别 trace 不存在"));
        if (!"done".equals(trace.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "识别尚未完成，不能重抽取");
        }
        OcrResult ocrResult = read(trace.getOcrJson(), OcrResult.class);
        String scene = trace.getScene() == null ? "auto" : trace.getScene();
        ExtractResult extractResult;
        try {
            extractResult = extract(traceId, scene, ocrResult);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "重抽取失败：" + safeMessage(exception), exception);
        }
        saveReportMaterials(traceId, extractResult);
        try {
            String extractJson = objectMapper.writeValueAsString(extractResult);
            updateTrace(traceId, t -> {
                t.setExtractJson(extractJson);
                t.setExtractMode(extractResult.extractMode);
                t.setLlmModel(extractResult.llmModel);
                t.setExtractConfidence(extractResult.extractConfidence);
                t.setExtractError(extractResult.extractError);
                t.setStatusMessage("已重抽取");
            });
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "重抽取结果序列化失败", exception);
        }
        return getResult(traceId);
    }

    public ExtractResult getExtractResult(String traceId) {
        VisionTraceEntity trace = traceRepository.findById(traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "识别 trace 不存在"));
        if (!"done".equals(trace.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "识别尚未完成，不能保存任务");
        }
        return read(trace.getExtractJson(), ExtractResult.class);
    }

    public VisionTraceEntity getTrace(String traceId) {
        return traceRepository.findById(traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "识别 trace 不存在"));
    }

    public VisionTraceListResponse listTraces(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        VisionTraceListResponse response = new VisionTraceListResponse();
        response.items = traceRepository.findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(this::toSummaryDto)
                .toList();
        return response;
    }

    public VisionTraceSummaryDto toSummaryDto(VisionTraceEntity trace) {
        VisionTraceSummaryDto dto = new VisionTraceSummaryDto();
        dto.traceId = trace.getTraceId();
        dto.source = trace.getSource();
        dto.scene = trace.getScene();
        dto.status = trace.getStatus();
        dto.stage = trace.getStage();
        dto.progress = trace.getProgress() == null ? 0 : trace.getProgress();
        dto.errorCode = trace.getErrorCode();
        dto.message = trace.getStatusMessage() == null ? trace.getErrorMessage() : trace.getStatusMessage();
        dto.createdAt = trace.getCreatedAt() == null ? null : trace.getCreatedAt().toString();
        dto.updatedAt = trace.getUpdatedAt() == null ? null : trace.getUpdatedAt().toString();
        if ("done".equals(trace.getStatus()) && trace.getExtractJson() != null) {
            ExtractResult extractResult = read(trace.getExtractJson(), ExtractResult.class);
            dto.summaryTitle = extractResult.summary == null ? null : extractResult.summary.title;
            dto.extractMode = extractResult.extractMode;
            dto.ocrConfidence = extractResult.ocrConfidence;
            dto.extractConfidence = extractResult.extractConfidence;
            dto.taskCount = extractResult.tasks == null ? 0 : extractResult.tasks.size();
            dto.linkCount = extractResult.links == null ? 0 : extractResult.links.size();
            dto.riskCount = extractResult.riskFlags == null ? 0 : extractResult.riskFlags.size();
        }
        return dto;
    }

    private void updateProcessingStage(String traceId,
                                       String stage,
                                       int progress,
                                       String message,
                                       VisionProgressListener listener) {
        updateProcessingStage(traceId, stage, progress, message, listener, trace -> {
        });
    }

    private void updateProcessingStage(String traceId,
                                       String stage,
                                       int progress,
                                       String message,
                                       VisionProgressListener listener,
                                       TraceMutation extraMutation) {
        updateTrace(traceId, trace -> {
            trace.setStatus("processing");
            trace.setStage(stage);
            trace.setProgress(progress);
            trace.setStatusMessage(message);
            extraMutation.apply(trace);
        });
        listener.onProgress(traceId, stage, progress, message);
    }

    private void updateTrace(String traceId, TraceMutation mutation) {
        VisionTraceEntity trace = traceRepository.findById(traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "识别 trace 不存在"));
        mutation.apply(trace);
        trace.setUpdatedAt(clock.instant());
        traceRepository.save(trace);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private static void normalizeOcrResult(OcrResult result) {
        if (result == null || result.blocks == null || result.blocks.isEmpty()) {
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

    private void saveReportMaterials(String traceId, ExtractResult extractResult) {
        if (extractResult == null || extractResult.dailyReportMaterials == null || extractResult.dailyReportMaterials.isEmpty()) {
            return;
        }
        for (String material : extractResult.dailyReportMaterials) {
            if (material == null || material.isBlank()) {
                continue;
            }
            ReportMaterialEntity entity = new ReportMaterialEntity();
            entity.setId(Ids.reportMaterialId(clock));
            entity.setTraceId(traceId);
            entity.setType("daily_report");
            entity.setContent(material.trim());
            entity.setCreatedAt(clock.instant());
            reportMaterialRepository.save(entity);
        }
    }

    private <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            // Trace 状态为 done 但 json 为空时不能继续 NPE，给上层返回 INTERNAL_SERVER_ERROR
            // 比 NullPointerException 更明确，且前端能展示具体错因。
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "识别结果为空，可能是 OCR/抽取阶段未写入数据");
        }
        try {
            T value = objectMapper.readValue(json, type);
            if (value == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "识别结果反序列化为空");
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取识别结果失败", exception);
        }
    }

    private interface TraceMutation {
        void apply(VisionTraceEntity trace);
    }
}
