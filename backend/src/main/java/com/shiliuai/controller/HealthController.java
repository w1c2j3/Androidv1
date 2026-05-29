package com.shiliuai.controller;

import com.shiliuai.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
public class HealthController {
    @GetMapping("/api/v1/health")
    public HealthResponse health() {
        return new HealthResponse(
                "ok",
                "shiliu-ai-backend",
                "1.0.0",
                OffsetDateTime.now().toString()
        );
    }
}
