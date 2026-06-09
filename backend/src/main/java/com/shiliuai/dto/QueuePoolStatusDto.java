package com.shiliuai.dto;

public class QueuePoolStatusDto {
    public String name;
    public String label;
    public String status;
    public String message;
    public int corePoolSize;
    public int maxPoolSize;
    public int poolSize;
    public int activeCount;
    public int queueSize;
    public int queueCapacity;
    public int remainingQueueCapacity;
    public long taskCount;
    public long completedTaskCount;
    public double activeUsage;
    public double queueUsage;
}
