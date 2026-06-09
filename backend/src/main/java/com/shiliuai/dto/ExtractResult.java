package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.List;

public class ExtractResult {
    public String traceId;
    public SummaryDto summary;
    public List<TaskCandidateDto> tasks = new ArrayList<>();
    public List<LinkCandidateDto> links = new ArrayList<>();
    public List<String> dailyReportMaterials = new ArrayList<>();
    public List<RiskFlagDto> riskFlags = new ArrayList<>();
}
