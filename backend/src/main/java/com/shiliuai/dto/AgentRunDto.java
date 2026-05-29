package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.List;

public class AgentRunDto {
    public String runId;
    public String status;
    public String intent;
    public String module;
    public String command;
    public String projectPath;
    public String source;
    public String summary;
    public boolean requiresConfirmation;
    public String createdAt;
    public String completedAt;
    public List<String> steps = new ArrayList<>();
    public List<String> logs = new ArrayList<>();
    public List<String> nextSteps = new ArrayList<>();
    public List<RiskFlagDto> risks = new ArrayList<>();
    public List<TaskCandidateDto> tasks = new ArrayList<>();
    public List<PaperDto> papers = new ArrayList<>();
}

