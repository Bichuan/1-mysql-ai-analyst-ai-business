package com.aianalyst.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Durable USER or ASSISTANT message without raw business result rows. */
@TableName("conversation_message")
public class ConversationMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long turnId;
    private String role;
    private String originalContent;
    private String standaloneQuestion;
    private String answerSummary;
    private Long queryHistoryId;
    private String status;
    private Integer estimatedTokens;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getTurnId() { return turnId; }
    public void setTurnId(Long turnId) { this.turnId = turnId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getOriginalContent() { return originalContent; }
    public void setOriginalContent(String originalContent) { this.originalContent = originalContent; }
    public String getStandaloneQuestion() { return standaloneQuestion; }
    public void setStandaloneQuestion(String standaloneQuestion) { this.standaloneQuestion = standaloneQuestion; }
    public String getAnswerSummary() { return answerSummary; }
    public void setAnswerSummary(String answerSummary) { this.answerSummary = answerSummary; }
    public Long getQueryHistoryId() { return queryHistoryId; }
    public void setQueryHistoryId(Long queryHistoryId) { this.queryHistoryId = queryHistoryId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getEstimatedTokens() { return estimatedTokens; }
    public void setEstimatedTokens(Integer estimatedTokens) { this.estimatedTokens = estimatedTokens; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
