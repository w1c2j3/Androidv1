package com.shiliuai.repository;

import com.shiliuai.entity.ReportMaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportMaterialRepository extends JpaRepository<ReportMaterialEntity, String> {
    List<ReportMaterialEntity> findByTraceIdOrderByCreatedAtAsc(String traceId);
}
