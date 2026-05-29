package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.List;

public class SaveTasksResponse {
    public int savedCount;
    public List<TaskDto> tasks = new ArrayList<>();
}
