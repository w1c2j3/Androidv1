package com.shiliuai.dto;

import jakarta.validation.constraints.NotBlank;

public class BotCallbackConfigUpdateRequest {
    @NotBlank
    public String verificationToken;
    public String encryptKey;
}
