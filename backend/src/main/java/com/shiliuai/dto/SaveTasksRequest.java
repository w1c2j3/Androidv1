package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.List;

public class SaveTasksRequest {
    public List<String> selectedTaskTempIds = new ArrayList<>();
    public OverrideFields override;

    public static class OverrideFields {
        public String owner;
        public String dueAt;
    }
}
