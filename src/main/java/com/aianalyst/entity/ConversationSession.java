package com.aianalyst.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Durable owner and state record for one user conversation. */
@TableName("conversation_session")
public class ConversationSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private Long userId;
    private String title;
    private String rollingSummary;
    private Long summaryUntilTurn;
    private Long currentTurn;
    private String structuredState;
    private Integer estimatedTokens;
    private Long version;
    private String status;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRollingSummary() { return rollingSummary; }
    public void setRollingSummary(String rollingSummary) { this.rollingSummary = rollingSummary; }
    public Long getSummaryUntilTurn() { return summaryUntilTurn; }
    public void setSummaryUntilTurn(Long summaryUntilTurn) { this.summaryUntilTurn = summaryUntilTurn; }
    public Long getCurrentTurn() { return currentTurn; }
    public void setCurrentTurn(Long currentTurn) { this.currentTurn = currentTurn; }
    public String getStructuredState() { return structuredState; }
    public void setStructuredState(String structuredState) { this.structuredState = structuredState; }
    public Integer getEstimatedTokens() { return estimatedTokens; }
    public void setEstimatedTokens(Integer estimatedTokens) { this.estimatedTokens = estimatedTokens; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
