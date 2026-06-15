package com.shiliuai.service.workbench;

import com.shiliuai.dto.FeishuCardActionLogDto;
import com.shiliuai.dto.WorkbenchOverviewResponse;
import com.shiliuai.entity.FeishuCardActionLogEntity;
import com.shiliuai.repository.FeishuCardActionLogRepository;
import com.shiliuai.repository.TaskItemRepository;
import com.shiliuai.repository.VisionTraceRepository;
import com.shiliuai.service.vision.VisionPipelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class WorkbenchService {
    private final VisionTraceRepository visionTraceRepository;
    private final TaskItemRepository taskItemRepository;
    private final FeishuCardActionLogRepository actionLogRepository;
    private final VisionPipelineService visionPipelineService;
    private final Clock clock;

    public WorkbenchService(VisionTraceRepository visionTraceRepository,
                            TaskItemRepository taskItemRepository,
                            FeishuCardActionLogRepository actionLogRepository,
                            VisionPipelineService visionPipelineService,
                            Clock clock) {
        this.visionTraceRepository = visionTraceRepository;
        this.taskItemRepository = taskItemRepository;
        this.actionLogRepository = actionLogRepository;
        this.visionPipelineService = visionPipelineService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkbenchOverviewResponse overview() {
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        WorkbenchOverviewResponse response = new WorkbenchOverviewResponse();
        response.todayTraceCount = visionTraceRepository.countByCreatedAtAfter(startOfDay);
        response.todayDoneTraceCount = visionTraceRepository.countByStatusAndCreatedAtAfter("done", startOfDay);
        response.todayErrorTraceCount = visionTraceRepository.countByStatusAndCreatedAtAfter("error", startOfDay);
        response.todoTaskCount = taskItemRepository.countByStatus("todo");
        response.inProgressTaskCount = taskItemRepository.countByStatus("in_progress");
        response.doneTaskCount = taskItemRepository.countByStatus("done");
        response.ignoredTaskCount = taskItemRepository.countByStatus("ignored");
        response.todayCreatedTaskCount = taskItemRepository.countByCreatedAtAfter(startOfDay);
        response.recentTraces = visionPipelineService.listTraces(8).items;
        response.recentCardActions = actionLogRepository.findTop8ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDto)
                .toList();
        return response;
    }

    private FeishuCardActionLogDto toDto(FeishuCardActionLogEntity entity) {
        FeishuCardActionLogDto dto = new FeishuCardActionLogDto();
        dto.id = entity.getId();
        dto.botId = entity.getBotId();
        dto.traceId = entity.getTraceId();
        dto.action = entity.getAction();
        dto.status = normalizeCardActionStatus(entity.getStatus());
        dto.errorCode = entity.getErrorCode();
        dto.errorMessage = entity.getErrorMessage();
        dto.createdAt = entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString();
        return dto;
    }

    private static String normalizeCardActionStatus(String status) {
        return "success".equals(status) ? "ok" : status;
    }
}
