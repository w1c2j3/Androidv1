package com.shiliuai.service.feishu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.dto.AgentRunDto;
import com.shiliuai.dto.CommandRunRequest;
import com.shiliuai.dto.PaperDto;
import com.shiliuai.dto.RiskFlagDto;
import com.shiliuai.dto.TaskCandidateDto;
import com.shiliuai.entity.BotConfigEntity;
import com.shiliuai.service.agent.AgentRunService;
import com.shiliuai.service.bot.BotConfigService;
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
    private final TaskExecutor feishuEventTaskExecutor;
    private final ObjectMapper objectMapper;

    public FeishuEventService(BotConfigService botConfigService,
                              FeishuClientAdapter feishuClientAdapter,
                              FeishuImageProcessingJobService imageProcessingJobService,
                              AgentRunService agentRunService,
                              LlmChatService llmChatService,
                              TaskService taskService,
                              @Qualifier("feishuEventTaskExecutor") TaskExecutor feishuEventTaskExecutor,
                              ObjectMapper objectMapper) {
        this.botConfigService = botConfigService;
        this.feishuClientAdapter = feishuClientAdapter;
        this.imageProcessingJobService = imageProcessingJobService;
        this.agentRunService = agentRunService;
        this.llmChatService = llmChatService;
        this.taskService = taskService;
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
                if (isAgentCommand(normalized)) {
                    replyTextSafely(bot, chatId, runAgentCommandSafely(bot, chatId, normalized));
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

    private String runAgentCommandSafely(BotConfigEntity bot, String chatId, String text) {
        try {
            CommandRunRequest request = new CommandRunRequest();
            request.command = text;
            request.source = "feishu_text";
            request.chatId = chatId;
            if (isDigestCommand(text)) {
                request.contextText = recentChatText(bot, chatId);
            }
            AgentRunDto run = agentRunService.createRun(request);
            return formatAgentRunReply(run);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to run Feishu AgentRun command", exception);
            return "命令已收到，但 AgentRun 创建失败：" + exception.getMessage();
        }
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
