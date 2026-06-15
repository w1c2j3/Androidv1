package com.shiliuai.dto;

public class SetupReadinessResponse {
    public boolean backendOk;
    public boolean ocrConfigured;
    public boolean ocrHealthy;
    public String ocrEndpoint;
    public boolean llmConfigured;
    public boolean llmHealthy;
    public String llmModel;
    public boolean botRegistered;
    public String botId;
    public String botStatus;
    public boolean tokenValid;
    public boolean eventCallbackVerified;
    public boolean ready;
    public String callbackUrl;
    public String cardCallbackUrl;
    public String nextStep;
}
