package com.shiliuai.dto;

public class RiskFlagDto {
    public String type;
    public String message;

    public RiskFlagDto() {
    }

    public RiskFlagDto(String type, String message) {
        this.type = type;
        this.message = message;
    }
}
