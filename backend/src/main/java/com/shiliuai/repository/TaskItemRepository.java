package com.shiliuai.repository;

import com.shiliuai.entity.TaskItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TaskItemRepository extends JpaRepository<TaskItemEntity, String> {
    List<TaskItemEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<TaskItemEntity> findAllByOrderByCreatedAtDesc();

    // 团队化工作台：按负责人筛选，区分大小写但允许空格 trim 在 service 层做
    List<TaskItemEntity> findByOwnerOrderByCreatedAtDesc(String owner);

    List<TaskItemEntity> findByStatusAndOwnerOrderByCreatedAtDesc(String status, String owner);

    long countByStatus(String status);

    long countByCreatedAtAfter(Instant createdAt);
}
