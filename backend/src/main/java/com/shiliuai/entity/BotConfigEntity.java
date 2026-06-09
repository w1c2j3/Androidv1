package com.shiliuai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "bot_configs")
public class BotConfigEntity {
    @Id
    private String id;
    private String botName;
    private String appId;
    @Column(columnDefinition = "text")
    private String encryptedAppSecret;
    private String verificationToken;
    private String encryptKey;
    private String tenantName;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastEventAt;
    @Column(length = 512)
    private String lastMessageText;
    private Instant lastReplyAt;
    @Column(columnDefinition = "text")
    private String lastReplyError;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBotName() {
        return botName;
    }

    public void setBotName(String botName) {
        this.botName = botName;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getEncryptedAppSecret() {
        return encryptedAppSecret;
    }

    public void setEncryptedAppSecret(String encryptedAppSecret) {
        this.encryptedAppSecret = encryptedAppSecret;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public String getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(Instant lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public String getLastMessageText() {
        return lastMessageText;
    }

    public void setLastMessageText(String lastMessageText) {
        this.lastMessageText = lastMessageText;
    }

    public Instant getLastReplyAt() {
        return lastReplyAt;
    }

    public void setLastReplyAt(Instant lastReplyAt) {
        this.lastReplyAt = lastReplyAt;
    }

    public String getLastReplyError() {
        return lastReplyError;
    }

    public void setLastReplyError(String lastReplyError) {
        this.lastReplyError = lastReplyError;
    }
}
