package com.shiliuai.service.feishu;

import com.shiliuai.dto.FileRef;
import com.shiliuai.entity.BotConfigEntity;

public interface FeishuResourceDownloader {
    FileRef downloadImage(BotConfigEntity bot, String messageId, String imageKey);
}
