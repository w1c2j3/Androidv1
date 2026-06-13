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
    public SummaryDto summary;
    public List<TaskCandidateDto> tasks = new ArrayList<>();
    public List<LinkCandidateDto> links = new ArrayList<>();
    public List<String> dailyReportMaterials = new ArrayList<>();
    public List<RiskFlagDto> riskFlags = new ArrayList<>();
    public OcrPreview ocr;

    public static class OcrPreview {
        public String plainText;
        public int blockCount;
        public int width;
        public int height;
        public List<OcrBlock> blocks = new ArrayList<>();
        public Map<String, Object> quality = new LinkedHashMap<>();
    }
}
