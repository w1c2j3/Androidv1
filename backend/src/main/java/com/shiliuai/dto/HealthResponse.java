package com.shiliuai.dto;

public class HealthResponse {
    public String status;
    public String service;
    public String version;
    public String time;

    public HealthResponse(String status, String service, String version, String time) {
        this.status = status;
        this.service = service;
        this.version = version;
        this.time = time;
    }
}
