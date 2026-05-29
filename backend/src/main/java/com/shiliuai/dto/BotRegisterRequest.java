package com.shiliuai.dto;

import jakarta.validation.constraints.NotBlank;

public class BotRegisterRequest {
    @NotBlank
    public String botName;
    @NotBlank
    public String appId;
    @NotBlank
    public String appSecret;
    @NotBlank
    public String verificationToken;
    public String encryptKey;
    public String tenantName;
}
