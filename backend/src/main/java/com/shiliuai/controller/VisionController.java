package com.shiliuai.controller;

import com.shiliuai.dto.VisionTraceListResponse;
import com.shiliuai.dto.VisionResultResponse;
import com.shiliuai.dto.VisionUploadResponse;
import com.shiliuai.entity.VisionTraceEntity;
import com.shiliuai.service.vision.VisionPipelineService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/vision")
public class VisionController {
    private final VisionPipelineService visionPipelineService;

    public VisionController(VisionPipelineService visionPipelineService) {
        this.visionPipelineService = visionPipelineService;
    }

    @PostMapping("/upload")
    public VisionUploadResponse upload(@RequestPart("file") MultipartFile file,
                                       @RequestParam(defaultValue = "android_upload") String source,
                                       @RequestParam(defaultValue = "auto") String sceneHint) {
        VisionTraceEntity trace = visionPipelineService.startUpload(file, source, sceneHint);
        VisionUploadResponse response = new VisionUploadResponse();
        response.traceId = trace.getTraceId();
        response.status = trace.getStatus();
        response.pollUrl = "/api/v1/vision/results/" + trace.getTraceId();
        response.message = trace.getStatusMessage();
        response.nextStep = "error".equals(trace.getStatus()) ? "retry_later" : "poll_result";
        return response;
    }

    @GetMapping("/results/{traceId}")
    public VisionResultResponse result(@PathVariable String traceId) {
        return visionPipelineService.getResult(traceId);
    }

    /**
     * 仅重跑结构化抽取阶段：保留持久化的 OCR JSON，重新调用 ExtractService（默认 LLM）。
     * 替代之前前端用 /agent/runs + /digest 重跑 LLM 的做法，避免把上一轮错误 JSON 喂回模型。
     */
    @PostMapping("/results/{traceId}/reextract")
    public VisionResultResponse reextract(@PathVariable String traceId) {
        return visionPipelineService.reextract(traceId);
    }

    @GetMapping("/traces")
    public VisionTraceListResponse traces(@RequestParam(defaultValue = "20") int limit) {
        return visionPipelineService.listTraces(limit);
    }

    @GetMapping("/files/{traceId}")
    public ResponseEntity<Resource> image(@PathVariable String traceId) throws IOException {
        VisionTraceEntity trace = visionPipelineService.getTrace(traceId);
        if (trace.getImagePath() == null || trace.getImagePath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片尚未保存");
        }
        Path path = Path.of(trace.getImagePath()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片文件不存在");
        }
        String contentType = Files.probeContentType(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType))
                .body(new FileSystemResource(path));
    }
}
