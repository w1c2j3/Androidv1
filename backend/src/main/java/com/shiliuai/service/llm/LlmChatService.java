package com.shiliuai.service.llm;

public interface LlmChatService {
    String answerText(String userText);

    boolean isAvailable();

    String modelName();

    String chatJson(String systemPrompt, String userPrompt);

    /**
     * 真实心跳：实际请求一次 chat/completions，返回 true 仅当远端 2xx 且能解析出文本。
     * 用于 SetupReadinessService 区分「配置了 LLM」与「LLM 真的能用」。
     */
    boolean ping();
}
