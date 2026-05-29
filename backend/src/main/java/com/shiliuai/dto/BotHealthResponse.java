package com.shiliuai.dto;

public class BotHealthResponse {
    public String botId;
    public boolean registered;
    public boolean tokenValid;
    public String tokenError;
    public boolean eventCallbackVerified;
    public String lastEventAt;
    public String lastMessageText;
    public String lastReplyAt;
    public String lastReplyError;
    public String status;
    public String nextStep;
}
