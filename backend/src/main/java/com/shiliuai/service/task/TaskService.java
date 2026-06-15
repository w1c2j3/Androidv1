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

        // 防御性：OCR/抽取实质上失败时，不再创建占位任务。
        // 这是用户报的 bug —— 「图片中没有明显任务或链接。但是点击保存任务还是会出现」。
        // 触发条件：tasks 列表为空、或全部候选缺少证据文本、或抽取走了规则兜底且 OCR 文本为空。
        String skipReason = detectSkipReason(extractResult);
        if (skipReason != null) {
            response.skippedReason = skipReason;
            response.skippedCount = extractResult.tasks == null ? 0 : extractResult.tasks.size();
            return response;
        }

        int skipped = 0;
        for (TaskCandidateDto candidate : extractResult.tasks) {
            if (!saveAll && !selected.contains(candidate.tempId)) {
                continue;
            }
            if (!isMeaningful(candidate)) {
                skipped++;
                continue;
            }
            TaskItemEntity entity = new TaskItemEntity();
            entity.setId(Ids.taskId(clock));
            entity.setTitle(candidate.title);
            entity.setOwner(resolveOwner(candidate, request));
            entity.setDueAt(resolveDueAt(request));
            entity.setDueText(candidate.dueText);
            entity.setPriority(candidate.priority == null ? "medium" : candidate.priority);
            // 同 saveCandidates：candidate.status 是抽取阶段提示，入库统一为 "todo"。
            entity.setStatus("todo");
            entity.setSource(trace.getSource());
            entity.setSourceType("vision_trace");
            entity.setSourceId(traceId);
            entity.setTraceId(traceId);
            entity.setEvidenceText(candidate.evidence);
            entity.setConfirmedByUser(true);
            entity.setConfidence(candidate.confidence);
            entity.setCreatedAt(clock.instant());
            taskItemRepository.save(entity);
            response.tasks.add(toDto(entity));
        }
        response.savedCount = response.tasks.size();
        response.skippedCount = skipped;
        if (response.savedCount == 0 && skipped > 0) {
            response.skippedReason = "候选缺少证据文本，已全部跳过。";
        }
        return response;
    }

    /**
     * 判定本次抽取是否实质失败，应当拒绝保存任务。
     * 返回非 null 字符串作为给用户的提示语；返回 null 表示可以正常进入保存流程。
     */
    private static String detectSkipReason(ExtractResult extractResult) {
        if (extractResult == null) {
            return "未获取到抽取结果，请重新触发识图。";
        }
        if (extractResult.tasks == null || extractResult.tasks.isEmpty()) {
            return "OCR 没识别到明显任务，不创建占位任务。";
        }
        boolean ocrUnusable = extractResult.ocrConfidence != null && extractResult.ocrConfidence < 0.05;
        boolean isFallback = "rule_fallback".equals(extractResult.extractMode);
        if (ocrUnusable && isFallback) {
            return "OCR 置信度为 0 且只走了规则兜底，疑似图片不可识别，已拒绝保存。";
        }
        return null;
    }

    /**
     * 候选必须至少有一段证据文本，否则就是规则兜底凭空生成的占位条目。
     */
    private static boolean isMeaningful(TaskCandidateDto candidate) {
        if (candidate == null) return false;
        if (!StringUtils.hasText(candidate.title)) return false;
        if (!StringUtils.hasText(candidate.evidence)) return false;
        return true;
    }

    @Transactional(readOnly = true)
    public TaskListResponse list(String status) {
        return list(status, null);
    }

    /**
     * 团队化工作台筛选：status 与 owner 任一可选。
     * owner 为空字符串时不过滤；trim 后仍空也不过滤。
     */
    @Transactional(readOnly = true)
    public TaskListResponse list(String status, String owner) {
        boolean hasStatus = StringUtils.hasText(status);
        boolean hasOwner = StringUtils.hasText(owner);
        String trimmedOwner = hasOwner ? owner.trim() : null;
        List<TaskItemEntity> entities;
        if (hasStatus && hasOwner) {
            entities = taskItemRepository.findByStatusAndOwnerOrderByCreatedAtDesc(status, trimmedOwner);
        } else if (hasStatus) {
            entities = taskItemRepository.findByStatusOrderByCreatedAtDesc(status);
        } else if (hasOwner) {
            entities = taskItemRepository.findByOwnerOrderByCreatedAtDesc(trimmedOwner);
        } else {
            entities = taskItemRepository.findAllByOrderByCreatedAtDesc();
        }
        TaskListResponse response = new TaskListResponse();
        response.items = entities.stream().map(this::toDto).toList();
        return response;
    }

    /**
     * 改派负责人。空字符串/null 视作"未指定"。
     */
    @Transactional
    public TaskDto reassign(String taskId, String newOwner) {
        TaskItemEntity entity = taskItemRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        String safe = StringUtils.hasText(newOwner) ? newOwner.trim() : "未指定";
        entity.setOwner(safe);
        taskItemRepository.save(entity);
        return toDto(entity);
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
        String safeSource = StringUtils.hasText(source) ? source.trim() : "feishu_text";
        entity.setSource(safeSource);
        entity.setSourceType("manual");
        entity.setSourceId(null);
        entity.setConfirmedByUser(true);
        entity.setConfidence(1.0);
        entity.setCreatedAt(clock.instant());
        taskItemRepository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public SaveTasksResponse saveCandidates(List<TaskCandidateDto> candidates, String source, String sourceId) {
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
            // 显式忽略 candidate.status（如 "pending_confirm"）；它只是抽取阶段的展示提示，
            // 真正的任务生命周期由 TaskService.updateStatus 接管，因此入库统一写 "todo"。
            entity.setStatus("todo");
            entity.setSource(StringUtils.hasText(source) ? source : "agent");
            entity.setSourceType("agent_run");
            entity.setSourceId(sourceId);
            entity.setTraceId(StringUtils.hasText(sourceId) && sourceId.startsWith("trace_") ? sourceId : null);
            entity.setEvidenceText(candidate.evidence);
            entity.setConfirmedByUser(true);
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
        dto.sourceType = entity.getSourceType();
        dto.sourceId = entity.getSourceId();
        dto.traceId = entity.getTraceId();
        dto.evidenceText = entity.getEvidenceText();
        dto.confirmedByUser = entity.getConfirmedByUser();
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
