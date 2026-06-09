package com.shiliuai.service.feishu;

import com.shiliuai.entity.BotConfigEntity;

import java.util.List;
import java.util.Map;

public interface FeishuClientAdapter {
    String sendText(BotConfigEntity bot, String chatId, String text);

    String sendCard(BotConfigEntity bot, String chatId, Map<String, Object> card);

    void updateCard(BotConfigEntity bot, String messageId, Map<String, Object> card);

    List<String> listRecentTextMessages(BotConfigEntity bot, String chatId, int limit);
}
