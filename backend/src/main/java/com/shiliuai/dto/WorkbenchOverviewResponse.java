package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkbenchOverviewResponse {
    public long todayTraceCount;
    public long todayDoneTraceCount;
    public long todayErrorTraceCount;
    public long todoTaskCount;
    public long inProgressTaskCount;
    public long doneTaskCount;
    public long ignoredTaskCount;
    public long todayCreatedTaskCount;
    public List<VisionTraceSummaryDto> recentTraces = new ArrayList<>();
    public List<FeishuCardActionLogDto> recentCardActions = new ArrayList<>();
}
