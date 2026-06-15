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

    /**
     * 异步提交：立即返回 runId + status=running 的占位，避免 Cloudflare 100 秒响应窗口截断。
     * 前端拿到 runId 后按 1.5 秒间隔轮询 GET /runs/{runId}，直到 Json.isTerminal 终止状态。
     */
    @PostMapping("/runs/submit")
    public AgentRunDto submit(@RequestBody CommandRunRequest request) {
        return agentRunService.submitAsync(request);
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

