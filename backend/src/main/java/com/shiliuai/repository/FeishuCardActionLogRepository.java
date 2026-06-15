package com.shiliuai.repository;

import com.shiliuai.entity.FeishuCardActionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeishuCardActionLogRepository extends JpaRepository<FeishuCardActionLogEntity, String> {
    List<FeishuCardActionLogEntity> findTop8ByOrderByCreatedAtDesc();
}
