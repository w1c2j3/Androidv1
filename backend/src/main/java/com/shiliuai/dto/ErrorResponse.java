package com.shiliuai.dto;

public class ErrorResponse {
    public String errorCode;
    public String message;

    public ErrorResponse() {
    }

    public ErrorResponse(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }
}
