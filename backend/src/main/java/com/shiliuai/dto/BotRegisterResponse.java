package com.shiliuai.dto;

import java.util.List;

public class BotRegisterResponse {
    public String botId;
    public String botName;
    public String status;
    public String callbackUrl;
    public String cardCallbackUrl;
    public List<String> requiredPermissions;
    public String nextStep;
}
