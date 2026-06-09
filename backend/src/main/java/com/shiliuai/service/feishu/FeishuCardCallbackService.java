package com.shiliuai.service.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.shiliuai.dto.ExtractResult;
import com.shiliuai.dto.SaveTasksRequest;
import com.shiliuai.dto.SaveTasksResponse;
import com.shiliuai.entity.BotConfigEntity;
import com.shiliuai.service.bot.BotConfigService;
import com.shiliuai.service.card.FeishuCardService;
import com.shiliuai.service.task.TaskService;
import com.shiliuai.service.vision.VisionPipelineService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class FeishuCardCallbackService {
    private final BotConfigService botConfigService;
    private final TaskService taskService;
    private final VisionPipelineService visionPipelineService;
    private final FeishuCardService feishuCardService;

    public FeishuCardCallbackService(BotConfigService botConfigService,
                                     TaskService taskService,
                                     VisionPipelineService visionPipelineService,
                                     FeishuCardService feishuCardService) {
        this.botConfigService = botConfigService;
        this.taskService = taskService;
        this.visionPipelineService = visionPipelineService;
        this.feishuCardService = feishuCardService;
    }

    public Object handleCallback(String botId, JsonNode body) {
        if (body.hasNonNull("challenge")) {
            return Map.of("challenge", body.path("challenge").asText());
        }
        BotConfigEntity bot = botConfigService.getRequired(botId);
        verifyToken(bot, body);
        JsonNode value = body.at("/event/action/value");
        String action = value.path("action").asText("");
        String traceId = value.path("traceId").asText("");
        if ("open_task_confirm".equals(action)) {
            ExtractResult extractResult = visionPipelineService.getExtractResult(traceId);
            return card(feishuCardService.buildTaskConfirmCard(extractResult));
        }
        if ("save_tasks".equals(action)) {
            SaveTasksResponse response = taskService.saveFromTrace(traceId, new SaveTasksRequest());
            return toastAndCard("success", "已保存 " + response.savedCount + " 个任务",
                    feishuCardService.buildSaveSuccessCard(response, traceId));
        }
        if ("ignore".equals(action)) {
            return toast("info", "已忽略");
        }
        if ("create_report_material".equals(action)) {
            ExtractResult extractResult = visionPipelineService.getExtractResult(traceId);
            return toastAndCard("success", "日报素材已生成", feishuCardService.buildDailyReportCard(extractResult));
        }
        if ("set_reminder".equals(action)) {
            return toast("info", "提醒设置将在下一版接入飞书提醒。");
        }
        if ("private_result".equals(action)) {
            return toast("info", "已记录为仅自己可见，权限策略将在工作台中生效。");
        }
        return toast("warning", "未知操作");
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

    private static Map<String, Object> toast(String type, String content) {
        return Map.of("toast", Map.of("type", type, "content", content));
    }

    private static Map<String, Object> card(Map<String, Object> card) {
        return Map.of("card", card);
    }

    private static Map<String, Object> toastAndCard(String type, String content, Map<String, Object> card) {
        return Map.of("toast", Map.of("type", type, "content", content), "card", card);
    }
}
