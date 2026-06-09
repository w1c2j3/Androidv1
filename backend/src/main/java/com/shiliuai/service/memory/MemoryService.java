package com.shiliuai.service.memory;

import com.shiliuai.dto.MemoryCreateRequest;
import com.shiliuai.dto.MemoryDto;
import com.shiliuai.dto.MemoryListResponse;
import com.shiliuai.entity.MemoryItemEntity;
import com.shiliuai.repository.MemoryItemRepository;
import com.shiliuai.util.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;

@Service
public class MemoryService {
    private final MemoryItemRepository memoryItemRepository;
    private final Clock clock;

    public MemoryService(MemoryItemRepository memoryItemRepository, Clock clock) {
        this.memoryItemRepository = memoryItemRepository;
        this.clock = clock;
    }

    @Transactional
    public MemoryDto create(MemoryCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆内容不能为空");
        }
        MemoryItemEntity entity = new MemoryItemEntity();
        entity.setId(Ids.memoryId(clock));
        entity.setTitle(StringUtils.hasText(request.title) ? request.title.trim() : titleFrom(request.content));
        entity.setContent(request.content.trim());
        entity.setCategory(StringUtils.hasText(request.category) ? request.category.trim() : "decision");
        entity.setSource(StringUtils.hasText(request.source) ? request.source.trim() : "android");
        entity.setCreatedAt(clock.instant());
        memoryItemRepository.save(entity);
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public MemoryListResponse list() {
        MemoryListResponse response = new MemoryListResponse();
        response.items = memoryItemRepository.findTop50ByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
        return response;
    }

    private MemoryDto toDto(MemoryItemEntity entity) {
        MemoryDto dto = new MemoryDto();
        dto.id = entity.getId();
        dto.title = entity.getTitle();
        dto.content = entity.getContent();
        dto.category = entity.getCategory();
        dto.source = entity.getSource();
        dto.createdAt = entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString();
        return dto;
    }

    private static String titleFrom(String content) {
        String trimmed = content.trim();
        return trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 24);
    }
}

