package com.shiliuai.controller;

import com.shiliuai.dto.MemoryCreateRequest;
import com.shiliuai.dto.MemoryDto;
import com.shiliuai.dto.MemoryListResponse;
import com.shiliuai.service.memory.MemoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memory")
public class MemoryController {
    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public MemoryListResponse list() {
        return memoryService.list();
    }

    @PostMapping
    public MemoryDto create(@RequestBody MemoryCreateRequest request) {
        return memoryService.create(request);
    }
}

