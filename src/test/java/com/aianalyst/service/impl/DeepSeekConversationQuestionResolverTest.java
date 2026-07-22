package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.config.ConversationProperties;
import com.aianalyst.dto.TokenBudgetAssessment;
import com.aianalyst.dto.ConversationContextSnapshot;
import com.aianalyst.dto.ConversationContextUpdateCommand;
import com.aianalyst.dto.ConversationTurnSnapshot;
import com.aianalyst.dto.ResolvedConversationQuestion;
import com.aianalyst.service.ConversationContextService;
import com.aianalyst.service.ModelResilienceGateway;
import com.aianalyst.service.QueryMetricsService;
import com.aianalyst.service.TokenBudgetService;
import com.aianalyst.service.TokenEstimator;
import com.aianalyst.service.prompt.ConversationContextPromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeepSeekConversationQuestionResolverTest {

    private static final String CONVERSATION_ID = "7bc58b98-9b9d-4f6f-9fa5-429d94f2ee4a";

    @Mock
    private ConversationContextService contextService;

    @Mock
    private ConversationContextPromptBuilder promptBuilder;

    @Mock
    private ModelResilienceGateway modelResilienceGateway;

    @Mock
    private TokenBudgetService tokenBudgetService;

    @Mock
    private TokenEstimator tokenEstimator;

    @Mock
    private QueryMetricsService queryMetricsService;

    @BeforeEach
    void useAnAvailableTokenBudgetByDefault() {
        lenient().when(tokenBudgetService.assess(anyString()))
                .thenReturn(assessment(false));
    }

    @Test
    void shouldBypassPlanningModelForFirstTurn() {
        DeepSeekConversationQuestionResolver resolver = resolver();
        ConversationContextSnapshot empty = snapshot(null, 0L, null, 0L, 0L, List.of());
        when(contextService.loadContext(7L, CONVERSATION_ID)).thenReturn(Optional.of(empty));

        ResolvedConversationQuestion result = resolver.resolve(
                7L, CONVERSATION_ID, "查询本月销售额");

        assertThat(result.standaloneQuestion()).isEqualTo("查询本月销售额");
        verifyNoInteractions(promptBuilder, modelResilienceGateway);
        verify(contextService, never()).updateContext(any(), any(), any());
    }

    @Test
    void shouldRewriteFollowUpAndCompactOnlyTheOldestTurn() {
        DeepSeekConversationQuestionResolver resolver = resolver();
        ConversationContextSnapshot snapshot = snapshot(
                "此前摘要", 0L, "{}", 3L, 7L,
                List.of(turn(1L), turn(2L), turn(3L)));
        when(contextService.loadContext(7L, CONVERSATION_ID)).thenReturn(Optional.of(snapshot));
        when(promptBuilder.build(snapshot, "那华东呢？", 1)).thenReturn("planning prompt");
        when(modelResilienceGateway.planContext("planning prompt")).thenReturn(
                java.util.concurrent.CompletableFuture.completedFuture("""
                {
                  "standaloneQuestion": "查询华东地区本月销售额",
                  "topicChanged": false,
                  "structuredState": {"metric":"销售额","filters":[{"value":"华东"}]},
                  "rollingSummary": "此前摘要；第1轮查询了本月销售额"
                }
                """));
        when(contextService.updateContext(any(), any(), any())).thenReturn(true);

        ResolvedConversationQuestion result = resolver.resolve(
                7L, CONVERSATION_ID, "那华东呢？");

        assertThat(result.standaloneQuestion()).isEqualTo("查询华东地区本月销售额");
        ArgumentCaptor<ConversationContextUpdateCommand> commandCaptor =
                ArgumentCaptor.forClass(ConversationContextUpdateCommand.class);
        verify(contextService).updateContext(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(CONVERSATION_ID),
                commandCaptor.capture());
        assertThat(commandCaptor.getValue().expectedVersion()).isEqualTo(7L);
        assertThat(commandCaptor.getValue().summaryUntilTurn()).isEqualTo(1L);
        assertThat(commandCaptor.getValue().removeOldestTurns()).isEqualTo(1);
        assertThat(commandCaptor.getValue().clearRecentTurns()).isFalse();
        assertThat(commandCaptor.getValue().rollingSummary())
                .isEqualTo("此前摘要；第1轮查询了本月销售额");
    }

    @Test
    void shouldClearOldContextWhenTopicChanges() {
        DeepSeekConversationQuestionResolver resolver = resolver();
        ConversationContextSnapshot snapshot = snapshot(
                "旧主题摘要", 2L, "{\"metric\":\"销售额\"}", 5L, 9L, List.of(turn(5L)));
        when(contextService.loadContext(7L, CONVERSATION_ID)).thenReturn(Optional.of(snapshot));
        when(promptBuilder.build(snapshot, "查询客户总数", 0)).thenReturn("planning prompt");
        when(modelResilienceGateway.planContext("planning prompt")).thenReturn(
                java.util.concurrent.CompletableFuture.completedFuture("""
                {
                  "standaloneQuestion": "查询客户总数",
                  "topicChanged": true,
                  "structuredState": {"metric":"客户总数"},
                  "rollingSummary": ""
                }
                """));
        when(contextService.updateContext(any(), any(), any())).thenReturn(true);

        ResolvedConversationQuestion result = resolver.resolve(
                7L, CONVERSATION_ID, "查询客户总数");

        assertThat(result.topicChanged()).isTrue();
        ArgumentCaptor<ConversationContextUpdateCommand> commandCaptor =
                ArgumentCaptor.forClass(ConversationContextUpdateCommand.class);
        verify(contextService).updateContext(any(), any(), commandCaptor.capture());
        assertThat(commandCaptor.getValue().rollingSummary()).isNull();
        assertThat(commandCaptor.getValue().summaryUntilTurn()).isEqualTo(5L);
        assertThat(commandCaptor.getValue().clearRecentTurns()).isTrue();
    }

    @Test
    void shouldRejectContextDependentFollowUpWhenPlanningFails() {
        DeepSeekConversationQuestionResolver resolver = resolver();
        ConversationContextSnapshot snapshot = snapshot(null, 0L, null, 1L, 1L, List.of(turn(1L)));
        when(contextService.loadContext(7L, CONVERSATION_ID)).thenReturn(Optional.of(snapshot));
        when(promptBuilder.build(snapshot, "那华东呢？", 0)).thenReturn("planning prompt");
        when(modelResilienceGateway.planContext("planning prompt")).thenReturn(
                java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalStateException("timeout")));

        assertThatThrownBy(() -> resolver.resolve(7L, CONVERSATION_ID, "那华东呢？"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("暂时无法解析上下文追问，请补充完整的查询条件");

        verify(contextService, never()).updateContext(any(), any(), any());
    }

    @Test
    void shouldDegradeCompleteQuestionToSingleTurnWhenPlanningFails() {
        DeepSeekConversationQuestionResolver resolver = resolver();
        ConversationContextSnapshot snapshot = snapshot(null, 0L, null, 4L, 8L, List.of(turn(4L)));
        when(contextService.loadContext(7L, CONVERSATION_ID)).thenReturn(Optional.of(snapshot));
        when(promptBuilder.build(snapshot, "查询本月客户总数", 0)).thenReturn("planning prompt");
        when(modelResilienceGateway.planContext("planning prompt")).thenReturn(
                java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalStateException("timeout")));
        when(contextService.updateContext(any(), any(), any())).thenReturn(true);

        ResolvedConversationQuestion result = resolver.resolve(
                7L, CONVERSATION_ID, "查询本月客户总数");

        assertThat(result.standaloneQuestion()).isEqualTo("查询本月客户总数");
        assertThat(result.topicChanged()).isTrue();
        ArgumentCaptor<ConversationContextUpdateCommand> commandCaptor =
                ArgumentCaptor.forClass(ConversationContextUpdateCommand.class);
        verify(contextService).updateContext(any(), any(), commandCaptor.capture());
        assertThat(commandCaptor.getValue().expectedVersion()).isEqualTo(8L);
        assertThat(commandCaptor.getValue().summaryUntilTurn()).isEqualTo(4L);
        assertThat(commandCaptor.getValue().clearRecentTurns()).isTrue();
    }

    @Test
    void shouldCompressEarlyContextBeforePlanningWhenTokenThresholdIsExceeded() {
        DeepSeekConversationQuestionResolver resolver = resolver();
        String question = "那华东呢？";
        ConversationContextSnapshot snapshot = snapshot(
                "很长的旧摘要", 0L, "{}", 3L, 7L,
                List.of(turn(1L), turn(2L), turn(3L)));
        ConversationContextSnapshot compressedSnapshot = new ConversationContextSnapshot(
                CONVERSATION_ID, "压缩摘要", 1L, "{}", 3L, 8L, 0,
                List.of(turn(2L), turn(3L)));
        when(contextService.loadContext(7L, CONVERSATION_ID)).thenReturn(Optional.of(snapshot));
        when(promptBuilder.build(snapshot, question, 1)).thenReturn("oversized planning prompt");
        when(tokenBudgetService.assess("oversized planning prompt"))
                .thenReturn(assessment(true));
        when(promptBuilder.buildCompression(snapshot, 1, 1_024))
                .thenReturn("compression prompt");
        when(modelResilienceGateway.compressContext("compression prompt"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        "{\"rollingSummary\":\"压缩摘要\"}"));
        when(promptBuilder.build(compressedSnapshot, question, 0))
                .thenReturn("compressed planning prompt");
        when(tokenBudgetService.assess("compressed planning prompt"))
                .thenReturn(assessment(false));
        when(modelResilienceGateway.planContext("compressed planning prompt")).thenReturn(
                java.util.concurrent.CompletableFuture.completedFuture("""
                {
                  "standaloneQuestion": "查询华东地区销售额",
                  "topicChanged": false,
                  "structuredState": {"metric":"销售额"},
                  "rollingSummary": "压缩摘要"
                }
                """));
        when(contextService.updateContext(any(), any(), any())).thenReturn(true, true);

        ResolvedConversationQuestion result = resolver.resolve(
                7L, CONVERSATION_ID, question);

        assertThat(result.standaloneQuestion()).isEqualTo("查询华东地区销售额");
        ArgumentCaptor<ConversationContextUpdateCommand> commandCaptor =
                ArgumentCaptor.forClass(ConversationContextUpdateCommand.class);
        verify(contextService, org.mockito.Mockito.times(2))
                .updateContext(any(), any(), commandCaptor.capture());
        assertThat(commandCaptor.getAllValues().get(0).removeOldestTurns()).isEqualTo(1);
        assertThat(commandCaptor.getAllValues().get(0).summaryUntilTurn()).isEqualTo(1L);
        assertThat(commandCaptor.getAllValues().get(1).expectedVersion()).isEqualTo(8L);
        assertThat(commandCaptor.getAllValues().get(1).removeOldestTurns()).isZero();
        verify(queryMetricsService).recordTokenCompression();
    }

    @Test
    void shouldKeepOriginalContextWhenCompressionModelIsUnavailable() {
        DeepSeekConversationQuestionResolver resolver = resolver();
        String question = "那华东呢？";
        ConversationContextSnapshot snapshot = snapshot(
                "很长的旧摘要", 0L, "{}", 3L, 7L,
                List.of(turn(1L), turn(2L), turn(3L)));
        when(contextService.loadContext(7L, CONVERSATION_ID)).thenReturn(Optional.of(snapshot));
        when(promptBuilder.build(snapshot, question, 1)).thenReturn("oversized planning prompt");
        when(tokenBudgetService.assess("oversized planning prompt")).thenReturn(assessment(true));
        when(promptBuilder.buildCompression(snapshot, 1, 1_024)).thenReturn("compression prompt");
        when(modelResilienceGateway.compressContext("compression prompt")).thenReturn(
                java.util.concurrent.CompletableFuture.failedFuture(
                        new java.util.concurrent.TimeoutException("timeout")));

        assertThatThrownBy(() -> resolver.resolve(7L, CONVERSATION_ID, question))
                .isInstanceOf(com.aianalyst.common.ModelCallException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(com.aianalyst.common.ResultCode.MODEL_SERVICE_UNAVAILABLE);

        verify(contextService, never()).updateContext(any(), any(), any());
        verify(queryMetricsService).recordTokenCompression();
    }

    @Test
    void shouldRejectWhenProtectedRecentTurnsAloneExceedTheTokenBudget() {
        DeepSeekConversationQuestionResolver resolver = resolver();
        String question = "那华东呢？";
        ConversationContextSnapshot snapshot = snapshot(
                null, 0L, "{}", 2L, 4L, List.of(turn(1L), turn(2L)));
        when(contextService.loadContext(7L, CONVERSATION_ID)).thenReturn(Optional.of(snapshot));
        when(promptBuilder.build(snapshot, question, 0)).thenReturn("oversized planning prompt");
        when(tokenBudgetService.assess("oversized planning prompt"))
                .thenReturn(assessment(true));

        assertThatThrownBy(() -> resolver.resolve(7L, CONVERSATION_ID, question))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(com.aianalyst.common.ResultCode.CONTEXT_WINDOW_EXCEEDED);

        verify(queryMetricsService).recordTokenBudgetRejected();
        verifyNoInteractions(modelResilienceGateway);
    }

    private DeepSeekConversationQuestionResolver resolver() {
        return new DeepSeekConversationQuestionResolver(
                contextService,
                promptBuilder,
                modelResilienceGateway,
                new ObjectMapper().findAndRegisterModules(),
                new ConversationProperties(),
                tokenBudgetService,
                tokenEstimator,
                queryMetricsService);
    }

    private ConversationContextSnapshot snapshot(String rollingSummary,
                                                 long summaryUntilTurn,
                                                 String structuredState,
                                                 long currentTurn,
                                                 long version,
                                                 List<ConversationTurnSnapshot> turns) {
        return new ConversationContextSnapshot(
                CONVERSATION_ID,
                rollingSummary,
                summaryUntilTurn,
                structuredState,
                currentTurn,
                version,
                0,
                turns);
    }

    private TokenBudgetAssessment assessment(boolean exceeds) {
        return new TokenBudgetAssessment(100, 20, 5, 200, 256, exceeds);
    }

    private ConversationTurnSnapshot turn(long turnId) {
        return new ConversationTurnSnapshot(
                turnId,
                "第" + turnId + "轮原始问题",
                "第" + turnId + "轮独立问题",
                "第" + turnId + "轮回答摘要",
                100L + turnId,
                "SUCCESS",
                LocalDateTime.of(2026, 7, 20, 9, 0).plusMinutes(turnId));
    }
}
