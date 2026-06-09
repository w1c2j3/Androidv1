package com.shiliuai.repository;

import com.shiliuai.entity.TaskItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TaskItemRepository extends JpaRepository<TaskItemEntity, String> {
    List<TaskItemEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<TaskItemEntity> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);

    long countByCreatedAtAfter(Instant createdAt);
}
