package com.shiliuai.service.setup;

import com.shiliuai.dto.QueuePoolStatusDto;
import com.shiliuai.dto.QueueStatusResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class QueueStatusService {
    private final ThreadPoolTaskExecutor visionTaskExecutor;
    private final ThreadPoolTaskExecutor feishuEventTaskExecutor;

    public QueueStatusService(@Qualifier("visionTaskExecutor") ThreadPoolTaskExecutor visionTaskExecutor,
                              @Qualifier("feishuEventTaskExecutor") ThreadPoolTaskExecutor feishuEventTaskExecutor) {
        this.visionTaskExecutor = visionTaskExecutor;
        this.feishuEventTaskExecutor = feishuEventTaskExecutor;
    }

    public QueueStatusResponse status() {
        QueueStatusResponse response = new QueueStatusResponse();
        response.pools = List.of(
                pool("vision", "OCR / Vision 后台队列", visionTaskExecutor),
                pool("feishu", "飞书事件后台队列", feishuEventTaskExecutor)
        );
        boolean overloaded = response.pools.stream().anyMatch(pool -> "overloaded".equals(pool.status));
        boolean busy = response.pools.stream().anyMatch(pool -> "busy".equals(pool.status));
        response.healthy = !overloaded;
        response.status = overloaded ? "overloaded" : (busy ? "busy" : "idle");
        response.message = switch (response.status) {
            case "overloaded" -> "队列接近或已经满载，演示保护会快速返回并提示稍后重试。";
            case "busy" -> "后台任务正在处理，新的任务会排队。";
            default -> "后台队列空闲。";
        };
        return response;
    }

    private static QueuePoolStatusDto pool(String name, String label, ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor threadPool = executor.getThreadPoolExecutor();
        int queueSize = threadPool.getQueue().size();
        int remaining = threadPool.getQueue().remainingCapacity();
        int queueCapacity = queueSize + remaining;
        int active = threadPool.getActiveCount();
        int max = threadPool.getMaximumPoolSize();
        double activeUsage = max <= 0 ? 0.0 : active / (double) max;
        double queueUsage = queueCapacity <= 0 ? 0.0 : queueSize / (double) queueCapacity;

        QueuePoolStatusDto dto = new QueuePoolStatusDto();
        dto.name = name;
        dto.label = label;
        dto.corePoolSize = threadPool.getCorePoolSize();
        dto.maxPoolSize = max;
        dto.poolSize = threadPool.getPoolSize();
        dto.activeCount = active;
        dto.queueSize = queueSize;
        dto.queueCapacity = queueCapacity;
        dto.remainingQueueCapacity = remaining;
        dto.taskCount = threadPool.getTaskCount();
        dto.completedTaskCount = threadPool.getCompletedTaskCount();
        dto.activeUsage = round(activeUsage);
        dto.queueUsage = round(queueUsage);
        dto.status = resolveStatus(dto);
        dto.message = message(dto);
        return dto;
    }

    private static String resolveStatus(QueuePoolStatusDto dto) {
        if (dto.remainingQueueCapacity == 0 || dto.queueUsage >= 0.85) {
            return "overloaded";
        }
        if (dto.queueUsage >= 0.50 || dto.activeCount >= dto.corePoolSize) {
            return "busy";
        }
        return "idle";
    }

    private static String message(QueuePoolStatusDto dto) {
        return dto.label + " active=" + dto.activeCount + "/" + dto.maxPoolSize
                + ", queue=" + dto.queueSize + "/" + dto.queueCapacity
                + ", completed=" + dto.completedTaskCount;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
