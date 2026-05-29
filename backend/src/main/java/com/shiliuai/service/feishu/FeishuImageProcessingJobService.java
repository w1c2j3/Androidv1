package com.shiliuai.service.feishu;

import com.shiliuai.dto.ExtractResult;
import com.shiliuai.dto.FileRef;
import com.shiliuai.entity.BotConfigEntity;
import com.shiliuai.entity.VisionTraceEntity;
import com.shiliuai.service.card.FeishuCardService;
import com.shiliuai.service.vision.VisionPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class FeishuImageProcessingJobService {
    private static final Logger log = LoggerFactory.getLogger(FeishuImageProcessingJobService.class);

    private final FeishuClientAdapter feishuClientAdapter;
    private final FeishuResourceDownloader feishuResourceDownloader;
    private final FeishuCardService feishuCardService;
    private final VisionPipelineService visionPipelineService;
    private final TaskExecutor visionTaskExecutor;

    public FeishuImageProcessingJobService(FeishuClientAdapter feishuClientAdapter,
                                           FeishuResourceDownloader feishuResourceDownloader,
                                           FeishuCardService feishuCardService,
                                           VisionPipelineService visionPipelineService,
                                           @Qualifier("visionTaskExecutor") TaskExecutor visionTaskExecutor) {
        this.feishuClientAdapter = feishuClientAdapter;
        this.feishuResourceDownloader = feishuResourceDownloader;
        this.feishuCardService = feishuCardService;
        this.visionPipelineService = visionPipelineService;
        this.visionTaskExecutor = visionTaskExecutor;
    }

    public String submit(BotConfigEntity bot, String chatId, String messageId, String imageKey) {
        VisionTraceEntity trace = visionPipelineService.createPendingTrace("feishu_image");
        String traceId = trace.getTraceId();
        String processingMessageId = feishuClientAdapter.sendCard(
                bot,
                chatId,
                feishuCardService.buildProcessingCard(traceId, "received", 5, "已收到图片，等待后台任务开始")
        );
        visionTaskExecutor.execute(() -> run(bot, chatId, messageId, imageKey, traceId, processingMessageId));
        return traceId;
    }

    private void run(BotConfigEntity bot,
                     String chatId,
                     String sourceMessageId,
                     String imageKey,
                     String traceId,
                     String processingMessageId) {
        try {
            updateProcessingCard(bot, processingMessageId, traceId, "resource_downloading", 12, "正在读取飞书图片资源");
            visionPipelineService.markProcessing(traceId, "resource_downloading", 12, "正在读取飞书图片资源");

            FileRef fileRef = feishuResourceDownloader.downloadImage(bot, sourceMessageId, imageKey);
            visionPipelineService.processExistingTrace(traceId, fileRef, "feishu_image", "auto",
                    (currentTraceId, stage, progress, message) ->
                            updateProcessingCard(bot, processingMessageId, currentTraceId, stage, progress, message));

            VisionTraceEntity trace = visionPipelineService.getTrace(traceId);
            if ("done".equals(trace.getStatus())) {
                ExtractResult extractResult = visionPipelineService.getExtractResult(traceId);
                replaceOrSendCard(bot, chatId, processingMessageId, feishuCardService.buildResultCard(extractResult));
                return;
            }
            replaceOrSendCard(bot, chatId, processingMessageId,
                    feishuCardService.buildErrorCard("图片处理失败", trace.getErrorMessage(), traceId));
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            visionPipelineService.markError(traceId, "FEISHU_IMAGE_PROCESS_FAILED", message);
            replaceOrSendCard(bot, chatId, processingMessageId,
                    feishuCardService.buildErrorCard("图片处理失败", message, traceId));
        }
    }

    private void updateProcessingCard(BotConfigEntity bot,
                                      String processingMessageId,
                                      String traceId,
                                      String stage,
                                      int progress,
                                      String message) {
        if (!StringUtils.hasText(processingMessageId)) {
            return;
        }
        try {
            feishuClientAdapter.updateCard(bot, processingMessageId,
                    feishuCardService.buildProcessingCard(traceId, stage, progress, message));
        } catch (RuntimeException exception) {
            log.warn("更新飞书处理中卡片失败 traceId={} stage={}: {}", traceId, stage, exception.getMessage());
        }
    }

    private void replaceOrSendCard(BotConfigEntity bot, String chatId, String processingMessageId, Map<String, Object> card) {
        if (StringUtils.hasText(processingMessageId)) {
            try {
                feishuClientAdapter.updateCard(bot, processingMessageId, card);
                return;
            } catch (RuntimeException exception) {
                log.warn("更新飞书结果卡片失败，改为发送新卡片：{}", exception.getMessage());
            }
        }
        feishuClientAdapter.sendCard(bot, chatId, card);
    }
}
