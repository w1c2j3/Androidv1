package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.List;

public class TaskCandidateDto {
    public String tempId;
    public String title;
    public String owner;
    public String dueText;
    public String dueAt;
    public String priority;
    public String evidence;
    public List<String> sourceBlockIds = new ArrayList<>();
    public double confidence;

    /**
     * 候选状态。仅作为抽取阶段对前端的展示提示（"pending_confirm" 等），
     * <b>不被 TaskService 持久化</b>——saveCandidates / saveFromTrace 总是把入库状态强制写成 "todo"，
     * 由 {@link com.shiliuai.service.task.TaskService#updateStatus} 接管后续生命周期。
     *
     * 历史上抽取层（RuleBasedExtractService / LlmStructuredExtractService / AgentRunService）
     * 都写 "pending_confirm"，但持久化路径完全忽略它；保留该字段是为了前端展示和单测断言一致。
     */
    public String status;
}
