package com.shiliuai.service.workbench;

import com.shiliuai.dto.WorkbenchOverviewResponse;
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
    private final VisionPipelineService visionPipelineService;
    private final Clock clock;

    public WorkbenchService(VisionTraceRepository visionTraceRepository,
                            TaskItemRepository taskItemRepository,
                            VisionPipelineService visionPipelineService,
                            Clock clock) {
        this.visionTraceRepository = visionTraceRepository;
        this.taskItemRepository = taskItemRepository;
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
        return response;
    }
}
