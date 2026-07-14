package com.aianalyst.service.impl;

import com.aianalyst.dto.QueryHistoryRecordCommand;
import com.aianalyst.entity.QueryHistory;
import com.aianalyst.mapper.QueryHistoryMapper;
import com.aianalyst.service.QueryHistoryService;
import com.aianalyst.service.QueryMetricsService;
import com.aianalyst.vo.PageResultVO;
import com.aianalyst.vo.QueryHistoryVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 查询审计历史服务。持久化只使用系统库 Mapper，绝不接触只读业务数据源。
 */
@Service
public class QueryHistoryServiceImpl implements QueryHistoryService {

    private static final Logger log = LoggerFactory.getLogger(QueryHistoryServiceImpl.class);
    private static final int MAX_AUDIT_REASON_LENGTH = 500;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

    private final QueryHistoryMapper queryHistoryMapper;
    private final ObjectMapper objectMapper;
    private final Executor queryHistoryExecutor;
    private final QueryMetricsService queryMetricsService;

    public QueryHistoryServiceImpl(QueryHistoryMapper queryHistoryMapper,
                                   ObjectMapper objectMapper,
                                   @Qualifier("queryHistoryExecutor") Executor queryHistoryExecutor,
                                   QueryMetricsService queryMetricsService) {
        this.queryHistoryMapper = queryHistoryMapper;
        this.objectMapper = objectMapper;
        this.queryHistoryExecutor = queryHistoryExecutor;
        this.queryMetricsService = queryMetricsService;
    }

    @Override
    public void recordAsync(QueryHistoryRecordCommand command) {
        try {
            // 主查询结果已经生成；审计写入只能异步旁路执行，不能因为系统库短暂故障而改变接口结果。
            CompletableFuture.runAsync(() -> persist(command), queryHistoryExecutor);
        } catch (RejectedExecutionException exception) {
            queryMetricsService.recordHistoryTaskRejected();
            // 有界队列满时宁可丢弃单条非关键历史并告警，也不能让 CallerRunsPolicy 拖慢用户查询。
            log.warn("Query history task was rejected. userId={}, status={}", command.userId(), command.status());
        }
    }

    @Override
    public PageResultVO<QueryHistoryVO> pageMyHistory(Long userId, long page, long size) {
        Page<QueryHistory> historyPage = queryHistoryMapper.selectPage(
                new Page<>(page, size),
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<QueryHistory>lambdaQuery()
                        // 用户 ID 是服务端从 JWT Principal 取得的，不接收前端传入的 userId，防止越权查看历史。
                        .eq(QueryHistory::getUserId, userId)
                        .orderByDesc(QueryHistory::getCreatedAt));

        List<QueryHistoryVO> records = historyPage.getRecords().stream()
                .map(this::toHistoryVO)
                .toList();
        return new PageResultVO<>(records, historyPage.getTotal(), historyPage.getCurrent(),
                historyPage.getSize(), historyPage.getPages());
    }

    private void persist(QueryHistoryRecordCommand command) {
        try {
            QueryHistory history = new QueryHistory();
            history.setUserId(command.userId());
            history.setNaturalLanguage(command.question());
            history.setGeneratedSql(command.generatedSql());
            history.setSqlAuditResult(command.sqlAuditResult());
            history.setSqlAuditReason(truncate(command.sqlAuditReason(), MAX_AUDIT_REASON_LENGTH));
            // 命令对象只接收 maskedRows；系统库审计记录不会反向保存业务库原始敏感数据。
            history.setQueryResult(serializeMaskedRows(command.maskedRows()));
            history.setAiSummary(command.aiSummary());
            history.setExecutionTime(command.executionTime());
            history.setStatus(command.status());
            history.setErrorMessage(truncate(command.errorMessage(), MAX_ERROR_MESSAGE_LENGTH));
            queryHistoryMapper.insert(history);
        } catch (Exception exception) {
            // 异步任务内部吞掉异常，保证无论使用何种 Executor 都不会影响已返回或即将返回的主查询。
            log.error("Failed to persist query history. userId={}, status={}",
                    command.userId(), command.status(), exception);
        }
    }

    private String serializeMaskedRows(List<java.util.Map<String, Object>> maskedRows) {
        if (maskedRows == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(maskedRows);
        } catch (JsonProcessingException exception) {
            // 审计结果序列化失败时保留其余审计字段，不能让单个字段破坏整条历史记录。
            log.warn("Masked query result cannot be serialized for history. cause={}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private QueryHistoryVO toHistoryVO(QueryHistory history) {
        return new QueryHistoryVO(history.getId(), history.getNaturalLanguage(), history.getGeneratedSql(),
                history.getSqlAuditResult(), history.getSqlAuditReason(), history.getAiSummary(),
                history.getExecutionTime(), history.getStatus(), history.getErrorMessage(), history.getCreatedAt());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
