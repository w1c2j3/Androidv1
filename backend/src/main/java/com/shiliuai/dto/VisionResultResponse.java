package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VisionResultResponse {
    public String traceId;
    public String status;
    public String stage;
    public int progress;
    public String message;
    public String errorCode;
    public String scene;
    public String extractMode;
    public String llmModel;
    public Double ocrConfidence;
    public Double extractConfidence;
    public String extractError;
    public SummaryDto summary;
    public List<TaskCandidateDto> tasks = new ArrayList<>();
    public List<LinkCandidateDto> links = new ArrayList<>();
    public List<String> dailyReportMaterials = new ArrayList<>();
    public List<RiskFlagDto> riskFlags = new ArrayList<>();
    public OcrPreview ocr;

    public static class OcrPreview {
        public String plainText;
        public String engine;
        public String engineVersion;
        public String modelProfile;
        public String lang;
        public Long latencyMs;
        public Double averageConfidence;
        public Double minConfidence;
        public int blockCount;
        public int width;
        public int height;
        public List<OcrBlock> blocks = new ArrayList<>();
        public Map<String, Object> quality = new LinkedHashMap<>();
    }
}
