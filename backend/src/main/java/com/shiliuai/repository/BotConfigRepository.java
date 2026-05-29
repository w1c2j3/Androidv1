package com.shiliuai.repository;

import com.shiliuai.entity.BotConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BotConfigRepository extends JpaRepository<BotConfigEntity, String> {
    Optional<BotConfigEntity> findByAppId(String appId);

    Optional<BotConfigEntity> findFirstByOrderByCreatedAtDesc();
}
