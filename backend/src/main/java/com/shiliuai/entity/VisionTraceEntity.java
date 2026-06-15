package com.shiliuai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "vision_traces")
public class VisionTraceEntity {
    @Id
    private String traceId;
    private String source;
    private String scene;
    private String status;
    private String stage;
    private Integer progress;
    @Column(columnDefinition = "text")
    private String statusMessage;
    @Column(columnDefinition = "text")
    private String ocrJson;
    @Column(columnDefinition = "text")
    private String extractJson;
    private String ocrEngine;
    private Double ocrConfidence;
    private String extractMode;
    private String llmModel;
    private Double extractConfidence;
    @Column(columnDefinition = "text")
    private String extractError;
    private String imagePath;
    private String errorCode;
    @Column(columnDefinition = "text")
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getOcrJson() {
        return ocrJson;
    }

    public void setOcrJson(String ocrJson) {
        this.ocrJson = ocrJson;
    }

    public String getExtractJson() {
        return extractJson;
    }

    public void setExtractJson(String extractJson) {
        this.extractJson = extractJson;
    }

    public String getOcrEngine() {
        return ocrEngine;
    }

    public void setOcrEngine(String ocrEngine) {
        this.ocrEngine = ocrEngine;
    }

    public Double getOcrConfidence() {
        return ocrConfidence;
    }

    public void setOcrConfidence(Double ocrConfidence) {
        this.ocrConfidence = ocrConfidence;
    }

    public String getExtractMode() {
        return extractMode;
    }

    public void setExtractMode(String extractMode) {
        this.extractMode = extractMode;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public Double getExtractConfidence() {
        return extractConfidence;
    }

    public void setExtractConfidence(Double extractConfidence) {
        this.extractConfidence = extractConfidence;
    }

    public String getExtractError() {
        return extractError;
    }

    public void setExtractError(String extractError) {
        this.extractError = extractError;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
}
