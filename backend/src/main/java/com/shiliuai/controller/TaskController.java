package com.shiliuai.controller;

import com.shiliuai.dto.SaveTasksRequest;
import com.shiliuai.dto.SaveTasksResponse;
import com.shiliuai.dto.TaskCreateRequest;
import com.shiliuai.dto.TaskDto;
import com.shiliuai.dto.TaskListResponse;
import com.shiliuai.dto.TaskReassignRequest;
import com.shiliuai.dto.TaskStatusUpdateRequest;
import com.shiliuai.service.task.TaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/from-trace/{traceId}")
    public SaveTasksResponse saveFromTrace(@PathVariable String traceId,
                                           @RequestBody(required = false) SaveTasksRequest request) {
        return taskService.saveFromTrace(traceId, request);
    }

    @GetMapping
    public TaskListResponse list(@RequestParam(required = false) String status,
                                 @RequestParam(required = false) String owner) {
        return taskService.list(status, owner);
    }

    @PostMapping
    public TaskDto create(@RequestBody TaskCreateRequest request) {
        return taskService.createTextTask(request == null ? null : request.title,
                request == null ? null : request.source,
                request == null ? null : request.owner);
    }

    @PatchMapping("/{taskId}/status")
    public TaskDto updateStatus(@PathVariable String taskId, @RequestBody TaskStatusUpdateRequest request) {
        return taskService.updateStatus(taskId, request);
    }

    /**
     * Android 端 HttpURLConnection 不支持 PATCH，这里给一个等价的 POST 入口。
     */
    @PostMapping("/{taskId}/status")
    public TaskDto updateStatusPost(@PathVariable String taskId, @RequestBody TaskStatusUpdateRequest request) {
        return taskService.updateStatus(taskId, request);
    }

    /**
     * 改派任务负责人。同时暴露 PATCH 和 POST 两个动词：
     * - PATCH 适用于浏览器 / curl
     * - POST 适用于 Android 端（HttpURLConnection 不支持 PATCH）
     */
    @PatchMapping("/{taskId}/owner")
    public TaskDto reassignPatch(@PathVariable String taskId, @RequestBody TaskReassignRequest request) {
        return taskService.reassign(taskId, request == null ? null : request.owner);
    }

    @PostMapping("/{taskId}/owner")
    public TaskDto reassignPost(@PathVariable String taskId, @RequestBody TaskReassignRequest request) {
        return taskService.reassign(taskId, request == null ? null : request.owner);
    }
}
