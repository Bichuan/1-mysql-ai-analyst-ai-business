package com.aianalyst.service.impl;

import com.aianalyst.dto.QueryHistoryRecordCommand;
import com.aianalyst.entity.QueryHistory;
import com.aianalyst.mapper.QueryHistoryMapper;
import com.aianalyst.service.QueryMetricsService;
import com.aianalyst.vo.PageResultVO;
import com.aianalyst.vo.QueryHistoryVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryHistoryServiceImplTest {

    @Mock
    private QueryHistoryMapper queryHistoryMapper;

    @Mock
    private QueryMetricsService queryMetricsService;

    private final Executor directExecutor = Runnable::run;

    @Test
    void shouldPersistOnlyMaskedRows() {
        QueryHistoryServiceImpl service = new QueryHistoryServiceImpl(
                queryHistoryMapper, new ObjectMapper(), directExecutor, queryMetricsService);
        QueryHistoryRecordCommand command = new QueryHistoryRecordCommand(
                7L, "查询客户", "SELECT * FROM biz_customer", "PASS", null,
                List.of(Map.of("email", "t***@gmail.com", "customer_name", "客户A")),
                "本次查询返回 1 条客户数据", 12, "SUCCESS", null);

        service.recordAsync(command);

        ArgumentCaptor<QueryHistory> historyCaptor = ArgumentCaptor.forClass(QueryHistory.class);
        verify(queryHistoryMapper).insert(historyCaptor.capture());
        QueryHistory history = historyCaptor.getValue();
        assertThat(history.getQueryResult())
                .contains("t***@gmail.com")
                .doesNotContain("test@gmail.com");
        assertThat(history.getStatus()).isEqualTo("SUCCESS");
        assertThat(history.getExecutionTime()).isEqualTo(12);
    }

    @Test
    void shouldQueryOnlyCurrentUsersHistory() {
        QueryHistoryServiceImpl service = new QueryHistoryServiceImpl(
                queryHistoryMapper, new ObjectMapper(), directExecutor, queryMetricsService);
        QueryHistory history = new QueryHistory();
        history.setId(101L);
        history.setUserId(7L);
        history.setNaturalLanguage("查询客户");
        history.setGeneratedSql("SELECT id FROM biz_customer LIMIT 10");
        history.setStatus("SUCCESS");
        history.setCreatedAt(LocalDateTime.of(2026, 7, 13, 10, 0));
        Page<QueryHistory> mapperPage = new Page<>(2, 5);
        mapperPage.setTotal(11);
        mapperPage.setRecords(List.of(history));
        when(queryHistoryMapper.selectPage(any(Page.class), org.mockito.ArgumentMatchers.<Wrapper<QueryHistory>>any()))
                .thenReturn(mapperPage);

        PageResultVO<QueryHistoryVO> result = service.pageMyHistory(7L, 2, 5);

        assertThat(result.total()).isEqualTo(11);
        assertThat(result.pages()).isEqualTo(3);
        assertThat(result.records()).extracting(QueryHistoryVO::id).containsExactly(101L);
        verify(queryHistoryMapper).selectPage(any(Page.class),
                org.mockito.ArgumentMatchers.<Wrapper<QueryHistory>>any());
    }

    @Test
    void shouldNotPropagateAsyncPersistenceFailure() {
        QueryHistoryServiceImpl service = new QueryHistoryServiceImpl(
                queryHistoryMapper, new ObjectMapper(), directExecutor, queryMetricsService);
        doThrow(new IllegalStateException("system database unavailable"))
                .when(queryHistoryMapper).insert(any(QueryHistory.class));

        assertThatCode(() -> service.recordAsync(new QueryHistoryRecordCommand(
                7L, "查询客户", null, null, null, null, null,
                null, "FAIL", "查询执行失败")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRecordMetricWhenHistoryTaskIsRejected() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };
        QueryHistoryServiceImpl service = new QueryHistoryServiceImpl(
                queryHistoryMapper, new ObjectMapper(), rejectingExecutor, queryMetricsService);

        service.recordAsync(new QueryHistoryRecordCommand(
                7L, "查询客户", null, null, null, null, null,
                null, "FAIL", "查询执行失败"));

        verify(queryMetricsService).recordHistoryTaskRejected();
    }
}
