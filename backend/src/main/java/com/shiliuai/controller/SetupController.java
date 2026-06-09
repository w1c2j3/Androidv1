package com.shiliuai.controller;

import com.shiliuai.dto.SetupReadinessResponse;
import com.shiliuai.dto.QueueStatusResponse;
import com.shiliuai.service.setup.QueueStatusService;
import com.shiliuai.service.setup.SetupReadinessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {
    private final SetupReadinessService setupReadinessService;
    private final QueueStatusService queueStatusService;

    public SetupController(SetupReadinessService setupReadinessService,
                           QueueStatusService queueStatusService) {
        this.setupReadinessService = setupReadinessService;
        this.queueStatusService = queueStatusService;
    }

    @GetMapping("/readiness")
    public SetupReadinessResponse readiness() {
        return setupReadinessService.readiness();
    }

    @GetMapping("/queues")
    public QueueStatusResponse queues() {
        return queueStatusService.status();
    }
}
