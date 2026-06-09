package com.shiliuai.service.feishu;

public class FeishuApiException extends RuntimeException {
    public FeishuApiException(String message) {
        super(message);
    }

    public FeishuApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
