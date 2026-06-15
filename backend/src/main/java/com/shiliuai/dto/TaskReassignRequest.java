package com.shiliuai.dto;

/**
 * 任务改派请求体：携带新负责人名字。
 * 团队化工作台用：从任务卡 → 改派按钮 → 弹框输入。
 */
public class TaskReassignRequest {
    public String owner;
}
