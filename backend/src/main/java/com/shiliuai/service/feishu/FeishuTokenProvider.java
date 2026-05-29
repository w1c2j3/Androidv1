package com.shiliuai.service.feishu;

import com.shiliuai.entity.BotConfigEntity;

public interface FeishuTokenProvider {
    String tenantAccessToken(BotConfigEntity bot);
}
