package com.shiliuai.service.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.dto.ExtractResult;
import com.shiliuai.dto.SaveTasksRequest;
import com.shiliuai.dto.SaveTasksResponse;
import com.shiliuai.entity.BotConfigEntity;
import com.shiliuai.entity.FeishuCardActionLogEntity;
import com.shiliuai.service.bot.BotConfigService;
import com.shiliuai.service.card.FeishuCardService;
import com.shiliuai.service.task.TaskService;
import com.shiliuai.service.vision.VisionPipelineService;
import com.shiliuai.util.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Map;

/**
 * 飞书卡片回调编排服务。
 *
 * 事务策略：本类方法不挂 {@code @Transactional}，业务子服务（TaskService、VisionPipelineService 等）
 * 各自管理自己的事务边界。卡片回调失败时通过 {@link FeishuCardActionLogService} 的 REQUIRES_NEW
 * 新事务把失败日志独立落库，不会被业务事务回滚带走。
 *
 * 错误响应策略：除 token 校验失败外，业务异常都被吞并转成 toast 响应返回给飞书，
 * 避免 Controller 抛 5xx 触发飞书 200672 错误码。
 */
@Service
public class FeishuCardCallbackService {
    private final BotConfigService botConfigService;
    private final TaskService taskService;
    private final VisionPipelineService visionPipelineService;
    private final FeishuCardService feishuCardService;
    private final FeishuActionResponseAdapter responseAdapter;
    private final FeishuCardActionLogService actionLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FeishuCardCallbackService(BotConfigService botConfigService,
                                     TaskService taskService,
                                     VisionPipelineService visionPipelineService,
                                     FeishuCardService feishuCardService,
                                     FeishuActionResponseAdapter responseAdapter,
                                     FeishuCardActionLogService actionLogService,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.botConfigService = botConfigService;
        this.taskService = taskService;
        this.visionPipelineService = visionPipelineService;
        this.feishuCardService = feishuCardService;
        this.responseAdapter = responseAdapter;
        this.actionLogService = actionLogService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Object handleCallback(String botId, JsonNode body) {
        if (body.hasNonNull("challenge")) {
            return Map.of("challenge", body.path("challenge").asText());
        }
        JsonNode value = body.at("/event/action/value");
        String action = value.path("action").asText("");
        String traceId = value.path("traceId").asText("");
        FeishuCardActionLogEntity log = createLog(botId, traceId, action, body);

        // token 校验失败需要让飞书侧看到 403，不当作业务失败处理。
        try {
            BotConfigEntity bot = botConfigService.getRequired(botId);
            verifyToken(bot, body);
        } catch (ResponseStatusException securityException) {
            log.setStatus("rejected");
            log.setErrorCode(String.valueOf(securityException.getStatusCode().value()));
            log.setErrorMessage(safeMessage(securityException));
            actionLogService.save(log);
            throw securityException;
        }

        try {
            Object response = route(action, traceId);
            log.setStatus("ok");
            log.setResponseJson(toJson(response));
            actionLogService.save(log);
            return response;
        } catch (RuntimeException exception) {
            // 业务失败：用新事务把日志独立落库，不被业务事务回滚带走。
            Map<String, Object> failureResponse =
                    responseAdapter.toast("warning", "操作失败：" + safeMessage(exception));
            log.setStatus("error");
            log.setErrorCode(errorCode(exception));
            log.setErrorMessage(safeMessage(exception));
            log.setResponseJson(toJson(failureResponse));
            actionLogService.save(log);
            // 返回 toast 让飞书前端看到友好提示，不 rethrow 成 500。
            return failureResponse;
        }
    }

    private Object route(String action, String traceId) {
        if ("open_task_confirm".equals(action)) {
            ExtractResult extractResult = visionPipelineService.getExtractResult(traceId);
            return responseAdapter.card(feishuCardService.buildTaskConfirmCard(extractResult));
        }
        if ("save_tasks".equals(action)) {
            SaveTasksResponse response = taskService.saveFromTrace(traceId, new SaveTasksRequest());
            return responseAdapter.toastAndCard("success", "已保存 " + response.savedCount + " 个任务",
                    feishuCardService.buildSaveSuccessCard(response, traceId));
        }
        if ("ignore".equals(action)) {
            return responseAdapter.toast("info", "已忽略");
        }
        if ("create_report_material".equals(action)) {
            ExtractResult extractResult = visionPipelineService.getExtractResult(traceId);
            return responseAdapter.toastAndCard("success", "日报素材已生成", feishuCardService.buildDailyReportCard(extractResult));
        }
        if ("set_reminder".equals(action)) {
            return responseAdapter.toast("info", "提醒设置将在下一版接入飞书提醒。");
        }
        if ("private_result".equals(action)) {
            return responseAdapter.toast("info", "已记录为仅自己可见，权限策略将在工作台中生效。");
        }
        return responseAdapter.toast("warning", "未知操作");
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

    private FeishuCardActionLogEntity createLog(String botId, String traceId, String action, JsonNode body) {
        FeishuCardActionLogEntity log = new FeishuCardActionLogEntity();
        log.setId(Ids.cardActionLogId(clock));
        log.setBotId(botId);
        log.setTraceId(traceId);
        log.setAction(StringUtils.hasText(action) ? action : "unknown");
        log.setRequestJson(toJson(body));
        log.setStatus("received");
        log.setCreatedAt(clock.instant());
        return log;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof ResponseStatusException statusException) {
            return String.valueOf(statusException.getStatusCode().value());
        }
        return exception.getClass().getSimpleName();
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message) ? message : exception.getClass().getSimpleName();
    }
}
