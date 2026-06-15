package com.shiliuai.runner;

import com.shiliuai.repository.TaskItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 视流 AI · 团队上线时一次性清理 task_items 表。
 *
 * 历史：之前的 28 条演示任务占据列表，团队上线第一天看到的是“别人的脏数据”。
 *
 * 用法：
 *   - 默认 SHILIU_TASK_CLEANUP_ENABLED=false，不会清表
 *   - 需要清理演示任务时临时置 true 启动一次，跑完后恢复 false
 *
 * 选择 ApplicationRunner 而不是 Flyway 的理由：
 *   - 无需新增外部依赖 / 网络下载
 *   - 通过环境变量即可关闭，不需要再追加 V2 migration
 */
@Component
public class TaskCleanupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskCleanupRunner.class);

    private final TaskItemRepository taskItemRepository;
    private final boolean enabled;

    public TaskCleanupRunner(TaskItemRepository taskItemRepository,
                             @Value("${shiliu.task-cleanup.enabled:false}") boolean enabled) {
        this.taskItemRepository = taskItemRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("TaskCleanup disabled, skip");
            return;
        }
        long before = taskItemRepository.count();
        if (before == 0) {
            log.info("TaskCleanup: task_items 已为空，跳过");
            return;
        }
        taskItemRepository.deleteAllInBatch();
        log.warn("TaskCleanup: 已清空 task_items 表 {} 条（团队上线初始化）。如需关闭，请设置 SHILIU_TASK_CLEANUP_ENABLED=false。", before);
    }
}
