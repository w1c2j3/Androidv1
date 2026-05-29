package com.shiliuai.controller;

import com.shiliuai.dto.SetupReadinessResponse;
import com.shiliuai.service.setup.SetupReadinessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {
    private final SetupReadinessService setupReadinessService;

    public SetupController(SetupReadinessService setupReadinessService) {
        this.setupReadinessService = setupReadinessService;
    }

    @GetMapping("/readiness")
    public SetupReadinessResponse readiness() {
        return setupReadinessService.readiness();
    }
}
