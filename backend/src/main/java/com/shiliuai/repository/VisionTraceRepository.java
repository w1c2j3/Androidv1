package com.shiliuai.repository;

import com.shiliuai.entity.VisionTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface VisionTraceRepository extends JpaRepository<VisionTraceEntity, String> {
    long countByCreatedAtAfter(Instant createdAt);

    long countByStatusAndCreatedAtAfter(String status, Instant createdAt);
}
