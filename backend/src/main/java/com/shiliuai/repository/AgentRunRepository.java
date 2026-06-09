package com.shiliuai.repository;

import com.shiliuai.entity.AgentRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRunRepository extends JpaRepository<AgentRunEntity, String> {
    List<AgentRunEntity> findTop20ByOrderByCreatedAtDesc();
}

