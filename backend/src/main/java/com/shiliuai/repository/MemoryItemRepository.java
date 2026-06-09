package com.shiliuai.repository;

import com.shiliuai.entity.MemoryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryItemRepository extends JpaRepository<MemoryItemEntity, String> {
    List<MemoryItemEntity> findTop50ByOrderByCreatedAtDesc();
}

