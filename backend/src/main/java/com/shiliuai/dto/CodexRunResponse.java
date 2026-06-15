package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 视流 AI · Codex 调用结果。
 *
 * status：done / failed / disabled / timeout
 * exitCode：codex 进程退出码
 * summary：最终模型输出（已合并 message events）
 * events：原始 JSON 事件流（保留以便前端展开调试）
 */
public class CodexRunResponse {
    public String runId;
    public String status;
    public Integer exitCode;
    public String prompt;
    public String workingDir;
    public String sandbox;
    public String summary;
    public String stderrTail;
    public List<String> events = new ArrayList<>();
    public Long durationMs;
    public String startedAt;
    public String finishedAt;
}
