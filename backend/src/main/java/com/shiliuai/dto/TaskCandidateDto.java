package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.List;

public class TaskCandidateDto {
    public String tempId;
    public String title;
    public String owner;
    public String dueText;
    public String dueAt;
    public String priority;
    public List<String> sourceBlockIds = new ArrayList<>();
    public double confidence;
    public String status;
}
