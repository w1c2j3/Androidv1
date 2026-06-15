package com.shiliuai.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.dto.AgentRunDto;
import com.shiliuai.dto.AgentRunListResponse;
import com.shiliuai.dto.CommandRunRequest;
import com.shiliuai.dto.MemoryCreateRequest;
import com.shiliuai.dto.PaperDto;
import com.shiliuai.dto.RiskFlagDto;
import com.shiliuai.dto.SaveTasksResponse;
import com.shiliuai.dto.TaskCandidateDto;
import com.shiliuai.entity.AgentRunEntity;
import com.shiliuai.repository.AgentRunRepository;
import com.shiliuai.service.llm.LlmChatService;
import com.shiliuai.service.memory.MemoryService;
import com.shiliuai.service.task.TaskService;
import com.shiliuai.util.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AgentRunService {
    private final AgentRunRepository agentRunRepository;
    private final TaskService taskService;
    private final LlmChatService llmChatService;
    private final MemoryService memoryService;
    private final AgentAccessPolicy agentAccessPolicy;
    private final ProjectReviewService projectReviewService;
    private final PaperSearchService paperSearchService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 异步 run 缓存：手机 → cloudflare quick tunnel → 后端的同步等待会被 100 秒响应窗口截掉。
     * submitAsync 立刻返回 runId + status=running 的占位 DTO，前端轮询 getRun 直到 status 终止。
     *
     * 参考 CodexService.runCache 同一套模式，避免演示时 524。
     * sweepRetainedRuns 在每次提交时清理 4 小时前的旧记录，避免内存堆积。
     */
    private final ConcurrentMap<String, AgentRunDto> runCache = new ConcurrentHashMap<>();
    private static final long RUN_RETENTION_SECONDS = 4 * 60 * 60L;

    public AgentRunService(AgentRunRepository agentRunRepository,
                           TaskService taskService,
                           LlmChatService llmChatService,
                           MemoryService memoryService,
                           AgentAccessPolicy agentAccessPolicy,
                           ProjectReviewService projectReviewService,
                           PaperSearchService paperSearchService,
                           ObjectMapper objectMapper,
                           Clock clock) {
        this.agentRunRepository = agentRunRepository;
        this.taskService = taskService;
        this.llmChatService = llmChatService;
        this.memoryService = memoryService;
        this.agentAccessPolicy = agentAccessPolicy;
        this.projectReviewService = projectReviewService;
        this.paperSearchService = paperSearchService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AgentRunDto createRun(CommandRunRequest request) {
        String command = request == null ? null : request.command;
        if (!StringUtils.hasText(command)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "命令不能为空");
        }
        String intent = inferIntent(command);
        AgentRunDto run = new AgentRunDto();
        run.runId = Ids.runId(clock);
        run.status = "done";
        run.intent = intent;
        run.module = moduleFor(intent);
        run.command = command.trim();
        run.projectPath = agentAccessPolicy.resolveProjectPath(request == null ? null : request.projectPath);
        run.source = request != null && StringUtils.hasText(request.source) ? request.source.trim() : "android";
        run.createdAt = Instant.now(clock).toString();
        run.requiresConfirmation = "project_review".equals(intent);
        run.steps = stepsFor(intent);
        fillResult(run, request);
        run.logs.addAll(logsFor(run));
        run.completedAt = Instant.now(clock).toString();
        agentRunRepository.save(toEntity(run));
        if (request != null && Boolean.TRUE.equals(request.saveTasks) && !run.tasks.isEmpty()) {
            taskService.saveCandidates(run.tasks, "agent:" + run.intent, run.runId);
        }
        if ("memory".equals(run.intent)) {
            saveMemoryFromRun(run);
        }
        return run;
    }

    /**
     * 异步提交：立刻返回 runId + status=running 的占位 DTO，子线程在后台跑 createRun。
     *
     * 设计目的：手机/飞书 → Cloudflare quick tunnel → 后端的同步等待会被 100 秒响应窗口截掉（Error 524）。
     * 前端在收到 runId 后按 1.5 秒轮询 getRun(runId)，直到 status 命中 Json.isTerminal 列表。
     */
    public AgentRunDto submitAsync(CommandRunRequest request) {
        String command = request == null ? null : request.command;
        if (!StringUtils.hasText(command)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "命令不能为空");
        }
        String intent = inferIntent(command);
        AgentRunDto placeholder = new AgentRunDto();
        placeholder.runId = Ids.runId(clock);
        placeholder.status = "running";
        placeholder.intent = intent;
        placeholder.module = moduleFor(intent);
        placeholder.command = command.trim();
        placeholder.projectPath = agentAccessPolicy.resolveProjectPath(request == null ? null : request.projectPath);
        placeholder.source = request != null && StringUtils.hasText(request.source) ? request.source.trim() : "android";
        placeholder.createdAt = Instant.now(clock).toString();
        placeholder.requiresConfirmation = "project_review".equals(intent);
        placeholder.steps = stepsFor(intent);
        placeholder.summary = "已入队，正在后台运行。前端请轮询 GET /api/v1/agent/runs/" + placeholder.runId;

        runCache.put(placeholder.runId, placeholder);
        sweepRetainedRuns();

        // 复制 request，避免子线程读到调用方后续修改
        final CommandRunRequest captured = copyRequest(request);
        final String reservedRunId = placeholder.runId;
        Thread worker = new Thread(() -> {
            try {
                AgentRunDto done = createRunInternal(captured, reservedRunId);
                runCache.put(reservedRunId, done);
            } catch (RuntimeException exception) {
                AgentRunDto failed = runCache.get(reservedRunId);
                if (failed == null) {
                    failed = placeholder;
                }
                failed.status = "failed";
                failed.summary = "Agent 异步执行失败：" + exception.getMessage();
                failed.completedAt = Instant.now(clock).toString();
                runCache.put(reservedRunId, failed);
            }
        }, "agent-async-" + reservedRunId);
        worker.setDaemon(true);
        worker.start();
        return placeholder;
    }

    public AgentRunDto getRun(String runId) {
        // 缓存先行：异步运行期间数据库还没写入，只在缓存里
        AgentRunDto cached = runCache.get(runId);
        if (cached != null) {
            return cached;
        }
        return agentRunRepository.findById(runId)
                .map(this::fromEntity)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AgentRun 不存在"));
    }

    /**
     * 与 createRun 等价，但接受预分配的 runId（让 submitAsync 占位和最终结果共用同一个 ID）。
     */
    private AgentRunDto createRunInternal(CommandRunRequest request, String reservedRunId) {
        String command = request == null ? null : request.command;
        if (!StringUtils.hasText(command)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "命令不能为空");
        }
        String intent = inferIntent(command);
        AgentRunDto run = new AgentRunDto();
        run.runId = StringUtils.hasText(reservedRunId) ? reservedRunId : Ids.runId(clock);
        run.status = "done";
        run.intent = intent;
        run.module = moduleFor(intent);
        run.command = command.trim();
        run.projectPath = agentAccessPolicy.resolveProjectPath(request == null ? null : request.projectPath);
        run.source = request != null && StringUtils.hasText(request.source) ? request.source.trim() : "android";
        run.createdAt = Instant.now(clock).toString();
        run.requiresConfirmation = "project_review".equals(intent);
        run.steps = stepsFor(intent);
        fillResult(run, request);
        run.logs.addAll(logsFor(run));
        run.completedAt = Instant.now(clock).toString();
        agentRunRepository.save(toEntity(run));
        if (request != null && Boolean.TRUE.equals(request.saveTasks) && !run.tasks.isEmpty()) {
            taskService.saveCandidates(run.tasks, "agent:" + run.intent, run.runId);
        }
        if ("memory".equals(run.intent)) {
            saveMemoryFromRun(run);
        }
        return run;
    }

    private static CommandRunRequest copyRequest(CommandRunRequest src) {
        if (src == null) return null;
        CommandRunRequest copy = new CommandRunRequest();
        copy.command = src.command;
        copy.projectPath = src.projectPath;
        copy.source = src.source;
        copy.contextText = src.contextText;
        copy.chatId = src.chatId;
        copy.traceId = src.traceId;
        copy.saveTasks = src.saveTasks;
        return copy;
    }

    private void sweepRetainedRuns() {
        Instant now = Instant.now(clock);
        for (Map.Entry<String, AgentRunDto> entry : runCache.entrySet()) {
            AgentRunDto value = entry.getValue();
            String stamp = value.completedAt != null ? value.completedAt : value.createdAt;
            if (stamp == null) continue;
            try {
                Instant t = Instant.parse(stamp);
                if (t.plusSeconds(RUN_RETENTION_SECONDS).isBefore(now)) {
                    runCache.remove(entry.getKey(), value);
                }
            } catch (Exception ignored) {
                // 时间戳异常的记录直接保留，等下次再尝试
            }
        }
    }

    public AgentRunListResponse listRuns() {
        AgentRunListResponse response = new AgentRunListResponse();
        response.items = agentRunRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::fromEntity)
                .sorted(Comparator.comparing((AgentRunDto run) -> run.createdAt).reversed())
                .limit(20)
                .toList();
        return response;
    }

    public SaveTasksResponse saveTasks(String runId) {
        AgentRunDto run = getRun(runId);
        return taskService.saveCandidates(run.tasks, "agent:" + run.intent, run.runId);
    }

    private AgentRunEntity toEntity(AgentRunDto run) {
        AgentRunEntity entity = new AgentRunEntity();
        entity.setRunId(run.runId);
        entity.setStatus(run.status);
        entity.setIntent(run.intent);
        entity.setModule(run.module);
        entity.setCommand(run.command);
        entity.setProjectPath(run.projectPath);
        entity.setSource(run.source);
        entity.setSummary(run.summary);
        entity.setCreatedAt(parseInstant(run.createdAt));
        entity.setCompletedAt(parseInstant(run.completedAt));
        try {
            entity.setPayloadJson(objectMapper.writeValueAsString(run));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AgentRun 序列化失败");
        }
        return entity;
    }

    private AgentRunDto fromEntity(AgentRunEntity entity) {
        try {
            return objectMapper.readValue(entity.getPayloadJson(), AgentRunDto.class);
        } catch (Exception exception) {
            AgentRunDto fallback = new AgentRunDto();
            fallback.runId = entity.getRunId();
            fallback.status = entity.getStatus();
            fallback.intent = entity.getIntent();
            fallback.module = entity.getModule();
            fallback.command = entity.getCommand();
            fallback.projectPath = entity.getProjectPath();
            fallback.source = entity.getSource();
            fallback.summary = entity.getSummary();
            fallback.createdAt = entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString();
            fallback.completedAt = entity.getCompletedAt() == null ? null : entity.getCompletedAt().toString();
            fallback.logs.add("AgentRun payload parse failed: " + exception.getMessage());
            return fallback;
        }
    }

    private void saveMemoryFromRun(AgentRunDto run) {
        MemoryCreateRequest request = new MemoryCreateRequest();
        request.title = "移动端记忆";
        request.content = run.command;
        request.category = "decision";
        request.source = "agent:" + run.runId;
        memoryService.create(request);
    }

    private void fillResult(AgentRunDto run, CommandRunRequest request) {
        switch (run.intent) {
            case "research" -> fillResearch(run);
            case "digest" -> fillDigest(run, request);
            case "task_create" -> fillTaskCreate(run);
            case "memory" -> fillMemory(run);
            default -> fillProjectReview(run, request);
        }
    }

    private void fillProjectReview(AgentRunDto run, CommandRunRequest request) {
        ProjectReviewService.ProjectReviewResult review = projectReviewService.review(run.projectPath, run.command);
        run.status = "done";
        run.summary = review.summary;
        run.risks.addAll(review.risks);
        run.nextSteps.addAll(review.nextSteps);
        run.tasks.addAll(review.tasks);
        run.logs.addAll(review.logs);
        appendContextEvidence(run, request == null ? "" : request.contextText);
    }

    private void fillResearch(AgentRunDto run) {
        try {
            List<PaperDto> papers = paperSearchService.search(run.command, 5);
            run.papers.addAll(papers);
            if (papers.isEmpty()) {
                run.status = "no_results";
                run.summary = "已调用真实论文检索数据源，但没有找到匹配论文。";
                run.nextSteps.add("换一个更具体的英文关键词后重新检索。");
                return;
            }
            run.status = "done";
            run.summary = "已从 " + paperSource(papers) + " 真实检索到 " + papers.size() + " 篇论文候选。";
            run.nextSteps.addAll(List.of(
                    "打开论文 URL 核对摘要和实验设置",
                    "筛选最适合当前项目验收指标的论文",
                    "将确认后的论文加入知识库或生成阅读任务"
            ));
            int index = 1;
            for (PaperDto paper : papers.stream().limit(3).toList()) {
                TaskCandidateDto task = task("task_tmp_" + index++, "阅读论文：" + paper.title, "medium");
                task.confidence = 0.82;
                run.tasks.add(task);
            }
        } catch (RuntimeException exception) {
            run.status = "data_source_error";
            run.summary = "论文检索调用失败：" + exception.getMessage();
            run.risks.add(new RiskFlagDto("research_source_error", "真实论文数据源调用失败，需要检查网络或 arXiv 响应。"));
        }
    }

    private void fillDigest(AgentRunDto run, CommandRunRequest request) {
        String contextText = request == null ? "" : request.contextText;
        String inlineText = extractInlineDigestText(run.command);
        if (!StringUtils.hasText(contextText) && !StringUtils.hasText(inlineText)) {
            run.status = "needs_data_source";
            run.summary = "没有真实群消息或用户粘贴文本；本次没有生成群总结。";
            run.risks.add(new RiskFlagDto("digest_source_missing", "群总结必须读取真实飞书消息或用户提供的真实文本。"));
            return;
        }
        String input = StringUtils.hasText(contextText)
                ? contextText.trim()
                : inlineText.trim();
        List<String> lines = digestLines(input);
        // Prompt 护栏：当上游传进来 OCR + 错误返回混在一起时（例如「Codex 524」「HTTP 5xx」），
        // 旧 prompt 会让 LLM 把错误一起总结，最终摘要变成「Codex 调用失败」之类的误导文本。
        // 这里显式要求模型识别并跳过错误/堆栈文本，只整理真实内容。
        String prompt = """
                你是视流 AI 的截图/聊天总结器。请把下面输入整理成摘要、决策、任务、风险和下一步，保持简洁。

                重要规则：
                - 如果输入里包含 HTTP 错误（如 500/502/503/504/524）、stacktrace、JSON 报错（status=failed/timeout/error）、
                  「调用失败」「请求超时」等系统消息，请把它们当作"无关噪声"忽略，不要写进摘要。
                - 如果排除噪声后没有任何真实业务内容，请明确回复"没有可总结的真实内容"，不要凭空编造。
                - 只整理真实业务文本（聊天、需求、决策、待办、截图正文）。

                输入：
                %s
                """.formatted(input);
        String modelAnswer = llmChatService.answerText(prompt);
        if (StringUtils.hasText(modelAnswer)
                && !modelAnswer.startsWith("我已收到消息")
                && !modelAnswer.startsWith("模型没有返回有效内容")) {
            run.status = "done";
            run.summary = modelAnswer;
            run.nextSteps.add("基于真实输入检查摘要是否遗漏关键任务。");
            appendDigestTasks(run, lines);
            return;
        }
        run.status = "done";
        run.summary = "已基于真实输入整理 " + lines.size() + " 条文本。要点：" + String.join(" / ", lines.stream().limit(3).toList());
        run.nextSteps.add("模型不可用时已返回真实文本摘要，建议配置 LLM 获取结构化决策和风险。");
        appendDigestTasks(run, lines);
    }

    private void fillTaskCreate(AgentRunDto run) {
        String title = cleanupTaskTitle(run.command);
        run.summary = "已识别为创建任务：" + title;
        run.nextSteps.add("保存任务后进入任务中心跟踪状态。");
        run.tasks.add(task("task_tmp_1", title, "medium"));
    }

    private void fillMemory(AgentRunDto run) {
        run.summary = "已识别为知识库记忆：这条内容会作为项目决策或长期上下文。";
        run.nextSteps.add("已写入知识库，可在 Android 知识库页面刷新查看。");
    }

    private static String inferIntent(String command) {
        String value = command.toLowerCase(Locale.ROOT);
        if (value.contains("/paper") || value.contains("论文") || value.contains("benchmark")) {
            return "research";
        }
        if (value.contains("/digest") || value.contains("群总结") || value.contains("群消息") || value.contains("总结消息")) {
            return "digest";
        }
        if (value.contains("/task") || value.contains("添加一个任务") || value.contains("创建任务")) {
            return "task_create";
        }
        if (value.contains("/remember") || value.contains("记住")) {
            return "memory";
        }
        return "project_review";
    }

    private static String moduleFor(String intent) {
        return switch (intent) {
            case "research" -> "Research Agent";
            case "digest" -> "Digest Agent";
            case "task_create" -> "Task Agent";
            case "memory" -> "Memory Agent";
            default -> "Project Review Agent";
        };
    }

    private static List<String> stepsFor(String intent) {
        List<String> steps = new ArrayList<>();
        steps.add("解析命令和意图");
        steps.add("选择模块：" + moduleFor(intent));
        if ("project_review".equals(intent)) {
            steps.add("校验项目路径访问规则");
            steps.add("扫描真实项目文件和 Git 状态");
        } else if ("research".equals(intent)) {
            steps.add("调用真实论文检索数据源");
            steps.add("整理论文候选和阅读任务");
        } else if ("digest".equals(intent)) {
            steps.add("检查模型输出和真实消息来源");
            steps.add("基于真实输入整理摘要和任务");
        } else {
            steps.add("提取结构化内容");
            steps.add("输出任务候选");
        }
        return steps;
    }

    private static List<String> logsFor(AgentRunDto run) {
        return List.of(
                "received command from " + run.source,
                "intent=" + run.intent + ", module=" + run.module,
                "projectPath=" + run.projectPath,
                "status=" + run.status
        );
    }

    private static TaskCandidateDto task(String id, String title, String priority) {
        TaskCandidateDto task = new TaskCandidateDto();
        task.tempId = id;
        task.title = title;
        task.owner = "未指定";
        task.priority = priority;
        task.status = "pending_confirm";
        task.confidence = 0.88;
        return task;
    }

    private static List<String> digestLines(String input) {
        if (!StringUtils.hasText(input)) {
            return List.of();
        }
        return input.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(8)
                .toList();
    }

    private static void appendDigestTasks(AgentRunDto run, List<String> lines) {
        int index = run.tasks.size() + 1;
        for (String line : lines) {
            if (containsAny(line, List.of("完成", "整理", "确认", "跟进", "修复", "实现", "测试"))) {
                run.tasks.add(task("task_tmp_" + index++, line, "medium"));
            }
        }
    }

    private static String cleanupTaskTitle(String command) {
        String value = command == null ? "" : command.trim();
        value = value.replace("/task", "").replace("添加一个任务", "").replace("创建任务", "").trim();
        return StringUtils.hasText(value) ? value : "新建移动端任务";
    }

    private static boolean containsAny(String value, List<String> candidates) {
        return value != null && candidates.stream().anyMatch(value::contains);
    }

    private static void appendContextEvidence(AgentRunDto run, String contextText) {
        if (!StringUtils.hasText(contextText)) {
            return;
        }
        String cleaned = contextText.trim();
        List<String> lines = cleaned.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(6)
                .toList();
        run.summary = run.summary + " 附带真实上下文 " + cleaned.length() + " 字。";
        run.logs.add("contextChars=" + cleaned.length());
        if (!lines.isEmpty()) {
            run.nextSteps.add("对照上下文核对下一步：" + truncate(String.join(" / ", lines.stream().limit(2).toList()), 120));
        }
        int index = run.tasks.size() + 1;
        for (String line : lines) {
            if (containsAny(line, List.of("完成", "整理", "确认", "跟进", "修复", "实现", "测试"))) {
                run.tasks.add(task("task_tmp_" + index++, "处理上下文：" + truncate(line, 80), "medium"));
            }
        }
    }

    private static String extractInlineDigestText(String command) {
        if (!StringUtils.hasText(command)) {
            return "";
        }
        String value = command.trim();
        int digestIndex = value.indexOf("/digest");
        if (digestIndex >= 0) {
            value = value.substring(digestIndex + "/digest".length()).trim();
        }
        int newline = value.indexOf('\n');
        if (newline < 0) {
            return "";
        }
        value = value.substring(newline + 1);
        value = value.replace("总结以下 OCR 内容：", "")
                .replace("总结以下 OCR 内容:", "")
                .replace("总结 OCR 内容：", "")
                .replace("总结 OCR 内容:", "")
                .trim();
        return value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private static String paperSource(List<PaperDto> papers) {
        boolean hasOpenAlex = papers.stream().anyMatch(paper ->
                (paper.whyRelevant != null && paper.whyRelevant.contains("OpenAlex"))
                        || (paper.venue != null && paper.venue.contains("OpenAlex")));
        boolean hasArxiv = papers.stream().anyMatch(paper -> paper.venue != null && paper.venue.startsWith("arXiv"));
        if (hasOpenAlex && hasArxiv) {
            return "arXiv/OpenAlex";
        }
        if (hasOpenAlex) {
            return "OpenAlex";
        }
        return "arXiv";
    }

    private static Instant parseInstant(String value) {
        return StringUtils.hasText(value) ? Instant.parse(value) : null;
    }
}
