package com.shiliuai.service.feishu;

import com.shiliuai.entity.FeishuCardActionLogEntity;
import com.shiliuai.repository.FeishuCardActionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 飞书卡片回调日志独立服务。
 *
 * 所有写入都跑在 REQUIRES_NEW 事务里，确保即使外层业务事务回滚，
 * 失败日志仍能落库，便于事后排查。
 */
@Service
public class FeishuCardActionLogService {
    private final FeishuCardActionLogRepository repository;

    public FeishuCardActionLogService(FeishuCardActionLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(FeishuCardActionLogEntity log) {
        repository.save(log);
    }
}
