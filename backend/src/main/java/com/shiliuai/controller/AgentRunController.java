package com.shiliuai.controller;

import com.shiliuai.dto.AgentRunDto;
import com.shiliuai.dto.AgentRunListResponse;
import com.shiliuai.dto.CommandRunRequest;
import com.shiliuai.dto.SaveTasksResponse;
import com.shiliuai.service.agent.AgentRunService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentRunController {
    private final AgentRunService agentRunService;

    public AgentRunController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @PostMapping("/runs")
    public AgentRunDto create(@RequestBody CommandRunRequest request) {
        return agentRunService.createRun(request);
    }

    @GetMapping("/runs")
    public AgentRunListResponse list() {
        return agentRunService.listRuns();
    }

    @GetMapping("/runs/{runId}")
    public AgentRunDto get(@PathVariable String runId) {
        return agentRunService.getRun(runId);
    }

    @PostMapping("/runs/{runId}/tasks")
    public SaveTasksResponse saveTasks(@PathVariable String runId) {
        return agentRunService.saveTasks(runId);
    }
}

