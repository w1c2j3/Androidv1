package com.shiliuai.controller;

import com.shiliuai.dto.WorkbenchOverviewResponse;
import com.shiliuai.service.workbench.WorkbenchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workbench")
public class WorkbenchController {
    private final WorkbenchService workbenchService;

    public WorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping("/overview")
    public WorkbenchOverviewResponse overview() {
        return workbenchService.overview();
    }
}
