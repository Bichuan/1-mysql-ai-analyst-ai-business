package com.aianalyst.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Audit record for each natural-language data query. */
@TableName("query_history")
public class QueryHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String naturalLanguage;
    private String generatedSql;
    private String sqlAuditResult;
    private String sqlAuditReason;
    private String queryResult;
    private String aiSummary;
    private Integer executionTime;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNaturalLanguage() { return naturalLanguage; }
    public void setNaturalLanguage(String naturalLanguage) { this.naturalLanguage = naturalLanguage; }
    public String getGeneratedSql() { return generatedSql; }
    public void setGeneratedSql(String generatedSql) { this.generatedSql = generatedSql; }
    public String getSqlAuditResult() { return sqlAuditResult; }
    public void setSqlAuditResult(String sqlAuditResult) { this.sqlAuditResult = sqlAuditResult; }
    public String getSqlAuditReason() { return sqlAuditReason; }
    public void setSqlAuditReason(String sqlAuditReason) { this.sqlAuditReason = sqlAuditReason; }
    public String getQueryResult() { return queryResult; }
    public void setQueryResult(String queryResult) { this.queryResult = queryResult; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public Integer getExecutionTime() { return executionTime; }
    public void setExecutionTime(Integer executionTime) { this.executionTime = executionTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
