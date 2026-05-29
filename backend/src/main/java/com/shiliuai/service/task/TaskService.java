package com.shiliuai.service.task;

import com.shiliuai.dto.ExtractResult;
import com.shiliuai.dto.SaveTasksRequest;
import com.shiliuai.dto.SaveTasksResponse;
import com.shiliuai.dto.TaskCandidateDto;
import com.shiliuai.dto.TaskDto;
import com.shiliuai.dto.TaskListResponse;
import com.shiliuai.dto.TaskStatusUpdateRequest;
import com.shiliuai.entity.TaskItemEntity;
import com.shiliuai.entity.VisionTraceEntity;
import com.shiliuai.repository.TaskItemRepository;
import com.shiliuai.service.vision.VisionPipelineService;
import com.shiliuai.util.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Service
public class TaskService {
    private final TaskItemRepository taskItemRepository;
    private final VisionPipelineService visionPipelineService;
    private final Clock clock;

    public TaskService(TaskItemRepository taskItemRepository,
                       VisionPipelineService visionPipelineService,
                       Clock clock) {
        this.taskItemRepository = taskItemRepository;
        this.visionPipelineService = visionPipelineService;
        this.clock = clock;
    }

    @Transactional
    public SaveTasksResponse saveFromTrace(String traceId, SaveTasksRequest request) {
        ExtractResult extractResult = visionPipelineService.getExtractResult(traceId);
        VisionTraceEntity trace = visionPipelineService.getTrace(traceId);
        Set<String> selected = request == null || request.selectedTaskTempIds == null
                ? Set.of()
                : new HashSet<>(request.selectedTaskTempIds);
        boolean saveAll = selected.isEmpty();
        SaveTasksResponse response = new SaveTasksResponse();
        for (TaskCandidateDto candidate : extractResult.tasks) {
            if (!saveAll && !selected.contains(candidate.tempId)) {
                continue;
            }
            TaskItemEntity entity = new TaskItemEntity();
            entity.setId(Ids.taskId(clock));
            entity.setTitle(candidate.title);
            entity.setOwner(resolveOwner(candidate, request));
            entity.setDueAt(resolveDueAt(request));
            entity.setDueText(candidate.dueText);
            entity.setPriority(candidate.priority == null ? "medium" : candidate.priority);
            entity.setStatus("todo");
            entity.setSource(trace.getSource());
            entity.setTraceId(traceId);
            entity.setConfidence(candidate.confidence);
            entity.setCreatedAt(clock.instant());
            taskItemRepository.save(entity);
            response.tasks.add(toDto(entity));
        }
        response.savedCount = response.tasks.size();
        return response;
    }

    @Transactional(readOnly = true)
    public TaskListResponse list(String status) {
        List<TaskItemEntity> entities = StringUtils.hasText(status)
                ? taskItemRepository.findByStatusOrderByCreatedAtDesc(status)
                : taskItemRepository.findAllByOrderByCreatedAtDesc();
        TaskListResponse response = new TaskListResponse();
        response.items = entities.stream().map(this::toDto).toList();
        return response;
    }

    @Transactional
    public TaskDto createTextTask(String title, String source, String owner) {
        if (!StringUtils.hasText(title)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务标题不能为空");
        }
        TaskItemEntity entity = new TaskItemEntity();
        entity.setId(Ids.taskId(clock));
        entity.setTitle(title.trim());
        entity.setOwner(StringUtils.hasText(owner) ? owner.trim() : "未指定");
        entity.setPriority("medium");
        entity.setStatus("todo");
        entity.setSource(StringUtils.hasText(source) ? source.trim() : "feishu_text");
        entity.setConfidence(1.0);
        entity.setCreatedAt(clock.instant());
        taskItemRepository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public SaveTasksResponse saveCandidates(List<TaskCandidateDto> candidates, String source, String traceId) {
        SaveTasksResponse response = new SaveTasksResponse();
        if (candidates == null || candidates.isEmpty()) {
            return response;
        }
        for (TaskCandidateDto candidate : candidates) {
            TaskItemEntity entity = new TaskItemEntity();
            entity.setId(Ids.taskId(clock));
            entity.setTitle(StringUtils.hasText(candidate.title) ? candidate.title.trim() : "未命名任务");
            entity.setOwner(StringUtils.hasText(candidate.owner) ? candidate.owner.trim() : "未指定");
            entity.setDueText(candidate.dueText);
            entity.setPriority(StringUtils.hasText(candidate.priority) ? candidate.priority : "medium");
            entity.setStatus("todo");
            entity.setSource(StringUtils.hasText(source) ? source : "agent");
            entity.setTraceId(traceId);
            entity.setConfidence(candidate.confidence);
            entity.setCreatedAt(clock.instant());
            taskItemRepository.save(entity);
            response.tasks.add(toDto(entity));
        }
        response.savedCount = response.tasks.size();
        return response;
    }

    @Transactional
    public TaskDto updateStatus(String taskId, TaskStatusUpdateRequest request) {
        String status = request == null ? null : request.status;
        if (!StringUtils.hasText(status) || !Set.of("todo", "in_progress", "done", "ignored").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务状态不合法");
        }
        TaskItemEntity entity = taskItemRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        entity.setStatus(status);
        taskItemRepository.save(entity);
        return toDto(entity);
    }

    public TaskDto toDto(TaskItemEntity entity) {
        TaskDto dto = new TaskDto();
        dto.id = entity.getId();
        dto.title = entity.getTitle();
        dto.owner = entity.getOwner();
        dto.dueAt = entity.getDueAt() == null ? null : entity.getDueAt().toString();
        dto.dueText = entity.getDueText();
        dto.priority = entity.getPriority();
        dto.status = entity.getStatus();
        dto.source = entity.getSource();
        dto.traceId = entity.getTraceId();
        dto.confidence = entity.getConfidence();
        dto.createdAt = entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString();
        return dto;
    }

    private static String resolveOwner(TaskCandidateDto candidate, SaveTasksRequest request) {
        if (request != null && request.override != null && StringUtils.hasText(request.override.owner)) {
            return request.override.owner;
        }
        return StringUtils.hasText(candidate.owner) ? candidate.owner : "未指定";
    }

    private static Instant resolveDueAt(SaveTasksRequest request) {
        if (request == null || request.override == null || !StringUtils.hasText(request.override.dueAt)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(request.override.dueAt).toInstant();
        } catch (DateTimeParseException ignored) {
            return Instant.parse(request.override.dueAt);
        }
    }
}
