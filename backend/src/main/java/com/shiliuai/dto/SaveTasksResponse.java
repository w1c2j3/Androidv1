package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.List;

public class SaveTasksResponse {
    public int savedCount;
    public List<TaskDto> tasks = new ArrayList<>();
    /**
     * 跳过保存的原因（如 OCR 失败 / 没有候选）。前端可据此提示用户"图片没识别到任务，不创建占位条目"。
     */
    public String skippedReason;
    public int skippedCount;
}
