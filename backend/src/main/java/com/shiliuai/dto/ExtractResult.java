package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.List;

public class ExtractResult {
    public String traceId;
    public String scene;
    public String extractMode;
    public String llmModel;
    public Double ocrConfidence;
    public Double extractConfidence;
    public String rawModelJson;
    public String extractError;
    public SummaryDto summary;
    public List<TaskCandidateDto> tasks = new ArrayList<>();
    public List<LinkCandidateDto> links = new ArrayList<>();
    public List<String> dailyReportMaterials = new ArrayList<>();
    public List<RiskFlagDto> riskFlags = new ArrayList<>();
}
