package com.shiliuai.controller;

import com.shiliuai.dto.CodexRunRequest;
import com.shiliuai.dto.CodexRunResponse;
import com.shiliuai.service.codex.CodexService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 视流 AI · Codex 直连入口。
 *
 * 端点：
 * - POST /api/v1/codex/run    同步调用（飞书 webhook、本地 curl）。
 * - POST /api/v1/codex/submit 异步提交，立刻返回 runId（手机端，绕开 Cloudflare 100s 响应窗口）。
 * - GET  /api/v1/codex/runs/{runId} 查询异步 run 的最新状态。
 *
 * 默认 sandbox=read-only，用于让团队成员从手机/飞书远程触发 codex 进行
 * 项目级问答、代码审计、风险扫描，避免误改文件。
 * 需要写权限时显式传 "workspace-write"。
 */
@RestController
@RequestMapping("/api/v1/codex")
public class CodexController {

    private final CodexService codexService;

    public CodexController(CodexService codexService) {
        this.codexService = codexService;
    }

    @PostMapping("/run")
    public CodexRunResponse run(@RequestBody CodexRunRequest request) {
        return codexService.run(request);
    }

    @PostMapping("/submit")
    public CodexRunResponse submit(@RequestBody CodexRunRequest request) {
        return codexService.submitAsync(request);
    }

    @GetMapping("/runs/{runId}")
    public CodexRunResponse fetch(@PathVariable String runId) {
        return codexService.fetchRun(runId);
    }
}
