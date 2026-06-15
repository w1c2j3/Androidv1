package com.shiliuai.service.feishu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.dto.AgentRunDto;
import com.shiliuai.dto.CodexRunRequest;
import com.shiliuai.dto.CodexRunResponse;
import com.shiliuai.dto.CommandRunRequest;
import com.shiliuai.dto.PaperDto;
import com.shiliuai.dto.RiskFlagDto;
import com.shiliuai.dto.TaskCandidateDto;
import com.shiliuai.entity.BotConfigEntity;
import com.shiliuai.service.agent.AgentRunService;
import com.shiliuai.service.bot.BotConfigService;
import com.shiliuai.service.codex.CodexService;
import com.shiliuai.service.llm.LlmChatService;
import com.shiliuai.service.task.TaskService;
import com.shiliuai.dto.TaskDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class FeishuEventService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeishuEventService.class);

    private final BotConfigService botConfigService;
    private final FeishuClientAdapter feishuClientAdapter;
    private final FeishuImageProcessingJobService imageProcessingJobService;
    private final AgentRunService agentRunService;
    private final LlmChatService llmChatService;
    private final TaskService taskService;
    private final CodexService codexService;
    private final TaskExecutor feishuEventTaskExecutor;
    private final ObjectMapper objectMapper;

    public FeishuEventService(BotConfigService botConfigService,
                              FeishuClientAdapter feishuClientAdapter,
                              FeishuImageProcessingJobService imageProcessingJobService,
                              AgentRunService agentRunService,
                              LlmChatService llmChatService,
                              TaskService taskService,
                              CodexService codexService,
                              @Qualifier("feishuEventTaskExecutor") TaskExecutor feishuEventTaskExecutor,
                              ObjectMapper objectMapper) {
        this.botConfigService = botConfigService;
        this.feishuClientAdapter = feishuClientAdapter;
        this.imageProcessingJobService = imageProcessingJobService;
        this.agentRunService = agentRunService;
        this.llmChatService = llmChatService;
        this.taskService = taskService;
        this.codexService = codexService;
        this.feishuEventTaskExecutor = feishuEventTaskExecutor;
        this.objectMapper = objectMapper;
    }

    public Object handleEvent(String botId, JsonNode body) {
        if (body.hasNonNull("challenge")) {
            return Map.of("challenge", body.path("challenge").asText());
        }
        BotConfigEntity bot = botConfigService.getRequired(botId);
        verifyToken(bot, body);
        String eventType = body.at("/header/event_type").asText("");
        if (!"im.message.receive_v1".equals(eventType)) {
            return ok("ignored event type: " + eventType);
        }

        JsonNode message = body.at("/event/message");
        String messageType = message.path("message_type").asText("");
        String chatId = message.path("chat_id").asText("");
        String chatType = message.path("chat_type").asText("");
        String messageId = message.path("message_id").asText("");
        JsonNode content = parseContent(message.path("content").asText("{}"));

        if ("text".equals(messageType)) {
            String text = content.path("text").asText("");
            LOGGER.info("Feishu text event accepted botId={} chatId={} chatType={} messageId={} text={}",
                    botId, chatId, chatType, messageId, text);
            botConfigService.markEventReceived(bot, text);
            if (text.contains("/ping")) {
                replyTextAsync(bot, chatId, "pong");
            } else if (StringUtils.hasText(text)) {
                handleTextAsync(bot, chatId, text);
            }
            return ok("text accepted");
        }

        if ("image".equals(messageType)) {
            String imageKey = content.path("image_key").asText("");
            LOGGER.info("Feishu image event accepted botId={} chatId={} chatType={} messageId={} imageKey={}",
                    botId, chatId, chatType, messageId, imageKey);
            botConfigService.markEventReceived(bot, "[image] " + imageKey);
            try {
                String traceId = imageProcessingJobService.submit(bot, chatId, messageId, imageKey);
                return ok("image accepted", traceId);
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to submit Feishu image job", exception);
                replyTextAsync(bot, chatId, "图片已收到，但后台处理入口暂时不可用；请稍后重试或在 Android 端上传截图。");
                return ok("image accepted but protected");
            }
        }

        botConfigService.markEventReceived(bot, "[" + messageType + "]");
        return ok("unsupported message type: " + messageType);
    }

    private void verifyToken(BotConfigEntity bot, JsonNode body) {
        String expected = bot.getVerificationToken();
        if (!StringUtils.hasText(expected)) {
            return;
        }
        String actual = body.at("/header/token").asText(null);
        if (!StringUtils.hasText(actual)) {
            actual = body.path("token").asText(null);
        }
        if (!expected.equals(actual)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "飞书 Verification Token 校验失败");
        }
    }

    private JsonNode parseContent(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode().put("text", content);
        }
    }

    private void replyTextSafely(BotConfigEntity bot, String chatId, String text) {
        try {
            feishuClientAdapter.sendText(bot, chatId, text);
            botConfigService.markReplySucceeded(bot.getId());
        } catch (RuntimeException exception) {
            botConfigService.markReplyFailed(bot.getId(), exception.getMessage());
            LOGGER.warn("Failed to send Feishu text reply to chat {}", chatId, exception);
        }
    }

    private void replyTextAsync(BotConfigEntity bot, String chatId, String text) {
        try {
            feishuEventTaskExecutor.execute(() -> replyTextSafely(bot, chatId, text));
        } catch (TaskRejectedException exception) {
            LOGGER.warn("Feishu reply queue is full; sending protection reply synchronously");
            replyTextSafely(bot, chatId, "演示保护：当前飞书回复队列已满，请稍后再试。");
        }
    }

    private void handleTextAsync(BotConfigEntity bot, String chatId, String text) {
        try {
            feishuEventTaskExecutor.execute(() -> {
                String normalized = normalizeText(text);
                if (isCodexCommand(normalized)) {
                    // 1) 立刻回占位
                    boolean writeMode = normalized.startsWith("/codex!") || normalized.contains("/codex!");
                    String prompt = stripCodexPrefix(normalized);
                    if (!StringUtils.hasText(prompt)) {
                        replyTextSafely(bot, chatId, "用法：\n  /codex <你要交给 codex 的指令>\n  /codex! <允许写文件的指令>（慎用）");
                        return;
                    }
                    CodexRunRequest request = new CodexRunRequest();
                    request.prompt = prompt;
                    request.sandbox = writeMode ? "workspace-write" : "read-only";
                    request.source = "feishu";
                    try {
                        CodexRunResponse placeholder = codexService.submitAsync(request);
                        replyTextSafely(bot, chatId, formatCodexPlaceholderReply(placeholder, writeMode));
                        // 2) 后台 poll 完成后发第二条
                        watchCodexThenReply(bot, chatId, placeholder.runId, writeMode);
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Feishu codex submit failed", exception);
                        replyTextSafely(bot, chatId, "Codex 调用失败：" + exception.getMessage());
                    }
                    return;
                }
                if (isAgentCommand(normalized)) {
                    // Agent 路径同样改异步：先回 runId，完成后再发最终结果。
                    try {
                        CommandRunRequest request = new CommandRunRequest();
                        request.command = normalized;
                        request.source = "feishu_text";
                        request.chatId = chatId;
                        if (isDigestCommand(normalized)) {
                            request.contextText = recentChatText(bot, chatId);
                        }
                        AgentRunDto placeholder = agentRunService.submitAsync(request);
                        replyTextSafely(bot, chatId, formatAgentPlaceholderReply(placeholder));
                        watchAgentThenReply(bot, chatId, placeholder.runId);
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Feishu agent submit failed", exception);
                        replyTextSafely(bot, chatId, "命令已收到，但 AgentRun 创建失败：" + exception.getMessage());
                    }
                    return;
                }
                String taskTitle = extractTaskTitle(text);
                if (StringUtils.hasText(taskTitle)) {
                    TaskDto task = taskService.createTextTask(taskTitle, "feishu_text", "我");
                    replyTextSafely(bot, chatId, "已添加任务：" + task.title + "\n可以在 Android 工作台的任务列表查看。");
                    return;
                }
                replyTextSafely(bot, chatId, answerTextSafely(text));
            });
        } catch (TaskRejectedException exception) {
            LOGGER.warn("Feishu text command queue is full; sending protection reply synchronously");
            replyTextSafely(bot, chatId, "演示保护：当前后台任务较多，这条命令没有进入队列，请稍后重试。");
        }
    }

    /**
     * 真实调用 Codex：从消息中提取 `/codex` 之后的 prompt，真实运行 codex exec。
     * 默认 read-only sandbox，避免飞书远程触发误改文件；
     * 如需写入显式写 `/codex! ...` 触发 workspace-write。
     *
     * 飞书路径直接走 submitAsync 在 handleTextAsync 中处理，避免 codex 长任务把
     * feishuEventTaskExecutor 拖死或被 Cloudflare 100s 响应窗口截断。
     */

    private static String formatCodexPlaceholderReply(CodexRunResponse placeholder, boolean writeMode) {
        StringBuilder b = new StringBuilder();
        b.append("Codex · 已入队");
        if (writeMode) b.append("（workspace-write）");
        b.append("\n运行编号：").append(placeholder.runId);
        b.append("\n状态：").append(placeholder.status);
        b.append("\n\n命令已派发，完成后会再发一条结果。如长时间无回应，请在 Android 工作台查询 runId。");
        return truncate(b.toString(), 800);
    }

    private static String formatAgentPlaceholderReply(AgentRunDto placeholder) {
        StringBuilder b = new StringBuilder();
        b.append("AgentRun · 已入队\n");
        b.append("运行编号：").append(placeholder.runId).append('\n');
        b.append("状态：").append(emptyAs(placeholder.status, "unknown")).append('\n');
        b.append('\n').append(emptyAs(placeholder.summary, "命令已派发，完成后会再发一条结果。"));
        return truncate(b.toString(), 800);
    }

    /**
     * 后台轮询 codexService.fetchRun(runId) 直到状态终止，然后发第二条回复。
     * 不复用 feishuEventTaskExecutor，避免占用前台事件队列。
     */
    private void watchCodexThenReply(BotConfigEntity bot, String chatId, String runId, boolean writeMode) {
        Thread t = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 30L * 60_000L; // 30 分钟最长等待
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(2_000L);
                    CodexRunResponse latest = codexService.fetchRun(runId);
                    if (latest != null && latest.status != null && isTerminalStatus(latest.status)) {
                        replyTextSafely(bot, chatId, formatCodexReply(latest, writeMode));
                        return;
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException ignored) {
                    // 缓存过期 / NOT_FOUND 视为终止失败
                    replyTextSafely(bot, chatId, "Codex runId=" + runId + " 状态已丢失，请稍后重试。");
                    return;
                }
            }
            replyTextSafely(bot, chatId, "Codex runId=" + runId + " 超过 30 分钟仍未完成，已停止跟踪。");
        }, "feishu-codex-watch-" + runId);
        t.setDaemon(true);
        t.start();
    }

    private void watchAgentThenReply(BotConfigEntity bot, String chatId, String runId) {
        Thread t = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 10L * 60_000L; // 10 分钟最长
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(1_500L);
                    AgentRunDto latest = agentRunService.getRun(runId);
                    if (latest != null && latest.status != null && isTerminalStatus(latest.status)) {
                        replyTextSafely(bot, chatId, formatAgentRunReply(latest));
                        return;
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException ignored) {
                    replyTextSafely(bot, chatId, "AgentRun runId=" + runId + " 状态已丢失，请稍后重试。");
                    return;
                }
            }
            replyTextSafely(bot, chatId, "AgentRun runId=" + runId + " 超过 10 分钟仍未完成，已停止跟踪。");
        }, "feishu-agent-watch-" + runId);
        t.setDaemon(true);
        t.start();
    }

    private static boolean isTerminalStatus(String status) {
        // 与 Android Json.isTerminal 保持一致
        String s = status.toLowerCase();
        return s.equals("done") || s.equals("error") || s.equals("failed")
                || s.equals("timeout") || s.equals("disabled")
                || s.equals("no_results") || s.equals("data_source_error") || s.equals("needs_data_source")
                || s.equals("completed") || s.equals("success");
    }

    private static String stripCodexPrefix(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        // 去掉 @bot 之后剩余部分里的 /codex 前缀
        int idx = trimmed.indexOf("/codex");
        if (idx < 0) return trimmed;
        String rest = trimmed.substring(idx + "/codex".length());
        if (rest.startsWith("!")) rest = rest.substring(1);
        return rest.replaceFirst("^[，,。:：；;\\s]+", "").trim();
    }

    private static boolean isCodexCommand(String text) {
        return StringUtils.hasText(text) && text.contains("/codex");
    }

    private static String formatCodexReply(CodexRunResponse response, boolean writeMode) {
        StringBuilder b = new StringBuilder();
        b.append("Codex · ").append(response.status == null ? "unknown" : response.status);
        if (writeMode) b.append("（workspace-write）");
        b.append("\n运行编号：").append(response.runId);
        if (response.durationMs != null) {
            b.append("\n耗时：").append(response.durationMs).append(" ms");
        }
        b.append("\n\n").append(response.summary == null ? "(无输出)" : response.summary);
        if (StringUtils.hasText(response.stderrTail) && !"done".equals(response.status)) {
            b.append("\n\nstderr 尾部：\n").append(response.stderrTail);
        }
        return truncate(b.toString(), 1600);
    }

    private String recentChatText(BotConfigEntity bot, String chatId) {
        try {
            return String.join("\n", feishuClientAdapter.listRecentTextMessages(bot, chatId, 50));
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to read Feishu message history from chat {}", chatId, exception);
            return "";
        }
    }

    private static String extractTaskTitle(String text) {
        String normalized = normalizeText(text);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        String[] markers = {"添加一个任务", "添加任务", "新增一个任务", "新增任务", "创建一个任务", "创建任务", "记录一个任务", "记一个任务"};
        for (String marker : markers) {
            int index = normalized.indexOf(marker);
            if (index >= 0) {
                String title = normalized.substring(index + marker.length())
                        .replaceFirst("^[，,。:：；;\\s]+", "")
                        .trim();
                return StringUtils.hasText(title) ? title : marker;
            }
        }
        return "";
    }

    private static boolean isAgentCommand(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("/plan")
                || text.contains("/bug")
                || text.contains("/paper")
                || text.contains("/digest")
                || text.contains("/remember")
                || text.contains("/task")
                || text.contains("收集论文")
                || text.contains("总结群消息")
                || text.contains("记住：")
                || text.contains("记住:");
    }

    private static boolean isDigestCommand(String text) {
        return StringUtils.hasText(text)
                && (text.contains("/digest")
                || text.contains("群总结")
                || text.contains("群消息")
                || text.contains("总结消息"));
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text
                .replaceAll("@[_a-zA-Z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String formatAgentRunReply(AgentRunDto run) {
        StringBuilder builder = new StringBuilder();
        String prefix = "done".equals(run.status) ? "已完成：" : "未完成：";
        builder.append(prefix).append(emptyAs(run.module, "AgentRun")).append('\n');
        builder.append("运行编号：").append(run.runId).append('\n');
        builder.append("状态：").append(emptyAs(run.status, "unknown")).append('\n');
        builder.append('\n').append(emptyAs(run.summary, "已生成结果。"));

        if (run.risks != null && !run.risks.isEmpty()) {
            builder.append("\n\n风险：");
            for (RiskFlagDto risk : run.risks.stream().limit(2).toList()) {
                builder.append("\n- ").append(emptyAs(risk.message, risk.type));
            }
        }

        if (run.nextSteps != null && !run.nextSteps.isEmpty()) {
            builder.append("\n\n下一步：");
            for (String step : run.nextSteps.stream().limit(3).toList()) {
                builder.append("\n- ").append(step);
            }
        }

        if (run.tasks != null && !run.tasks.isEmpty()) {
            builder.append("\n\n任务候选：");
            for (TaskCandidateDto task : run.tasks.stream().limit(3).toList()) {
                builder.append("\n- ").append(emptyAs(task.title, "未命名任务"));
            }
            builder.append("\n\n可在 Android 任务中心保存和跟进。");
        }

        if (run.papers != null && !run.papers.isEmpty()) {
            builder.append("\n\n论文候选：");
            for (PaperDto paper : run.papers.stream().limit(2).toList()) {
                builder.append("\n- ").append(emptyAs(paper.title, "论文")).append("（").append(emptyAs(paper.year, "年份未知")).append("）");
            }
        }

        return truncate(builder.toString(), 1400);
    }

    private String answerTextSafely(String text) {
        try {
            return llmChatService.answerText(text);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to generate LLM answer", exception);
            return "我已收到消息，但模型接口暂时不可用。你可以先发送图片，我会继续进行 OCR 整理。";
        }
    }

    private static String emptyAs(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private static Map<String, Object> ok(String message) {
        return Map.of("code", 0, "msg", message);
    }

    private static Map<String, Object> ok(String message, String traceId) {
        return Map.of("code", 0, "msg", message, "traceId", traceId);
    }

}
