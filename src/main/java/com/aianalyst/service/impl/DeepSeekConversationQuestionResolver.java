package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.config.ConversationProperties;
import com.aianalyst.dto.ConversationContextSnapshot;
import com.aianalyst.dto.ConversationContextUpdateCommand;
import com.aianalyst.dto.ConversationTurnSnapshot;
import com.aianalyst.dto.ResolvedConversationQuestion;
import com.aianalyst.service.ConversationContextService;
import com.aianalyst.service.ConversationQuestionResolver;
import com.aianalyst.service.ModelCallAwaiter;
import com.aianalyst.service.ModelCallType;
import com.aianalyst.service.ModelResilienceGateway;
import com.aianalyst.service.QueryMetricsService;
import com.aianalyst.service.TokenBudgetService;
import com.aianalyst.service.TokenEstimator;
import com.aianalyst.service.prompt.ConversationContextPromptBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** One bounded planning call resolves follow-ups, topic changes, state and rolling compaction. */
@Service
public class DeepSeekConversationQuestionResolver implements ConversationQuestionResolver {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekConversationQuestionResolver.class);
    private static final int MAX_CONTEXT_UPDATE_ATTEMPTS = 2;
    private static final int MAX_STANDALONE_QUESTION_LENGTH = 500;
    private static final int MAX_STRUCTURED_STATE_LENGTH = 4_000;
    private static final int MAX_ROLLING_SUMMARY_LENGTH = 4_000;
    private static final Pattern CONTEXT_DEPENDENT_QUESTION = Pattern.compile(
            "(?i)^(那|那么|这个|这些|换成|改成|再看|只看|然后|还有|前\\s*\\d+)|"
                    + ".*(呢|换成去年|改成去年|改成前\\s*\\d+|只看.{0,12})$");

    private final ConversationContextService contextService;
    private final ConversationContextPromptBuilder promptBuilder;
    private final ModelResilienceGateway modelResilienceGateway;
    private final ObjectMapper objectMapper;
    private final ConversationProperties properties;
    private final TokenBudgetService tokenBudgetService;
    private final TokenEstimator tokenEstimator;
    private final QueryMetricsService queryMetricsService;

    public DeepSeekConversationQuestionResolver(ConversationContextService contextService,
                                                ConversationContextPromptBuilder promptBuilder,
                                                ModelResilienceGateway modelResilienceGateway,
                                                ObjectMapper objectMapper,
                                                ConversationProperties properties,
                                                TokenBudgetService tokenBudgetService,
                                                TokenEstimator tokenEstimator,
                                                QueryMetricsService queryMetricsService) {
        this.contextService = contextService;
        this.promptBuilder = promptBuilder;
        this.modelResilienceGateway = modelResilienceGateway;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tokenBudgetService = tokenBudgetService;
        this.tokenEstimator = tokenEstimator;
        this.queryMetricsService = queryMetricsService;
    }

    @Override
    public ResolvedConversationQuestion resolve(Long userId,
                                                String conversationId,
                                                String currentQuestion) {
        for (int attempt = 0; attempt < MAX_CONTEXT_UPDATE_ATTEMPTS; attempt++) {
            ConversationContextSnapshot snapshot = contextService.loadContext(userId, conversationId)
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "会话不存在或已失效"));
            if (!hasReusableContext(snapshot)) {
                return ResolvedConversationQuestion.firstTurn(currentQuestion);
            }

            PreparedPlanning prepared = preparePlanning(
                    userId, conversationId, snapshot, currentQuestion);
            if (prepared == null) {
                log.info("Conversation context version changed during token compression; retrying once. conversationId={}",
                        conversationId);
                continue;
            }
            snapshot = prepared.snapshot();
            int compactionTurnCount = prepared.compactionTurnCount();
            ModelPlan plan;
            try {
                plan = parsePlan(
                        ModelCallAwaiter.await(
                                ModelCallType.CONTEXT_PLANNING,
                                () -> modelResilienceGateway.planContext(prepared.prompt())),
                        compactionTurnCount > 0);
            } catch (RuntimeException exception) {
                return fallbackOrReject(userId, conversationId, currentQuestion, snapshot, exception);
            }

            ContextDecision decision = decide(snapshot, plan, compactionTurnCount);
            List<ConversationTurnSnapshot> remainingTurns = remainingTurns(snapshot, decision);
            int estimatedTokens = tokenEstimator.estimateConversationContext(
                    decision.rollingSummary(), plan.structuredState(), remainingTurns);
            boolean updated = contextService.updateContext(
                    userId,
                    conversationId,
                    new ConversationContextUpdateCommand(
                            snapshot.version(),
                            decision.rollingSummary(),
                            decision.summaryUntilTurn(),
                            plan.structuredState(),
                            estimatedTokens,
                            decision.removeOldestTurns(),
                            decision.clearRecentTurns()));
            if (updated) {
                return new ResolvedConversationQuestion(
                        plan.standaloneQuestion(),
                        plan.topicChanged(),
                        plan.structuredState(),
                        decision.rollingSummary(),
                        decision.summaryUntilTurn());
            }
            log.info("Conversation context version changed during planning; retrying once. conversationId={}",
                    conversationId);
        }
        throw new BusinessException(ResultCode.BUSINESS_ERROR, "会话正在更新，请稍后重试");
    }

    private ResolvedConversationQuestion fallbackOrReject(Long userId,
                                                          String conversationId,
                                                          String currentQuestion,
                                                          ConversationContextSnapshot snapshot,
                                                          RuntimeException exception) {
        log.warn("Conversation planning failed. conversationId={}, cause={}",
                conversationId, exception.getClass().getSimpleName());
        if (isContextDependent(currentQuestion)) {
            throw new BusinessException(
                    ResultCode.BUSINESS_ERROR,
                    "暂时无法解析上下文追问，请补充完整的查询条件");
        }

        // A complete independent question can safely degrade to single-turn mode. Clear reusable
        // history with optimistic locking so an unrelated old state cannot leak into its next turn.
        boolean reset = contextService.updateContext(
                userId,
                conversationId,
                new ConversationContextUpdateCommand(
                        snapshot.version(), null, snapshot.currentTurn(), null, 0, 0, true));
        if (!reset) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "会话正在更新，请稍后重试");
        }
        return new ResolvedConversationQuestion(
                currentQuestion, true, null, null, snapshot.currentTurn());
    }

    private ContextDecision decide(ConversationContextSnapshot snapshot,
                                   ModelPlan plan,
                                   int compactionTurnCount) {
        if (plan.topicChanged()) {
            return new ContextDecision(null, snapshot.currentTurn(), 0, true);
        }
        if (compactionTurnCount == 0) {
            return new ContextDecision(
                    snapshot.rollingSummary(), snapshot.summaryUntilTurn(), 0, false);
        }

        List<ConversationTurnSnapshot> turns = snapshot.recentTurns();
        long summaryUntilTurn = turns.get(compactionTurnCount - 1).turnId();
        if (!StringUtils.hasText(plan.rollingSummary())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "模型未返回可用的对话摘要");
        }
        return new ContextDecision(
                plan.rollingSummary(), summaryUntilTurn, compactionTurnCount, false);
    }

    private PreparedPlanning preparePlanning(Long userId,
                                             String conversationId,
                                             ConversationContextSnapshot snapshot,
                                             String currentQuestion) {
        int compactionTurnCount = normalCompactionTurnCount(snapshot);
        String prompt = promptBuilder.build(snapshot, currentQuestion, compactionTurnCount);
        if (!tokenBudgetService.assess(prompt).exceedsLimit()) {
            return new PreparedPlanning(snapshot, compactionTurnCount, prompt);
        }

        int turnsToCompress = compactionTurnCount;
        if (turnsToCompress == 0 && !StringUtils.hasText(snapshot.rollingSummary())) {
            rejectOverBudget(prompt);
        }

        queryMetricsService.recordTokenCompression();
        String compressionPrompt = promptBuilder.buildCompression(
                snapshot, turnsToCompress, properties.getRollingSummaryTargetTokens());
        String compressedSummary;
        try {
            compressedSummary = parseCompressedSummary(ModelCallAwaiter.await(
                    ModelCallType.CONTEXT_COMPRESSION,
                    () -> modelResilienceGateway.compressContext(compressionPrompt)));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "上下文压缩失败，请稍后重试");
        }

        List<ConversationTurnSnapshot> remainingTurns = snapshot.recentTurns().stream()
                .skip(turnsToCompress)
                .toList();
        long summaryUntilTurn = turnsToCompress == 0
                ? snapshot.summaryUntilTurn()
                : snapshot.recentTurns().get(turnsToCompress - 1).turnId();
        int compressedEstimatedTokens = tokenEstimator.estimateConversationContext(
                compressedSummary, snapshot.structuredState(), remainingTurns);
        boolean updated = contextService.updateContext(
                userId,
                conversationId,
                new ConversationContextUpdateCommand(
                        snapshot.version(),
                        compressedSummary,
                        summaryUntilTurn,
                        snapshot.structuredState(),
                        compressedEstimatedTokens,
                        turnsToCompress,
                        false));
        if (!updated) {
            return null;
        }

        ConversationContextSnapshot compressedSnapshot = new ConversationContextSnapshot(
                snapshot.conversationId(),
                compressedSummary,
                summaryUntilTurn,
                snapshot.structuredState(),
                snapshot.currentTurn(),
                snapshot.version() + 1,
                compressedEstimatedTokens,
                remainingTurns);
        int remainingCompactionCount = normalCompactionTurnCount(compressedSnapshot);
        String compressedPrompt = promptBuilder.build(
                compressedSnapshot, currentQuestion, remainingCompactionCount);
        if (tokenBudgetService.assess(compressedPrompt).exceedsLimit()) {
            rejectOverBudget(compressedPrompt);
        }
        return new PreparedPlanning(
                compressedSnapshot, remainingCompactionCount, compressedPrompt);
    }

    private int normalCompactionTurnCount(ConversationContextSnapshot snapshot) {
        return Math.max(
                0, snapshot.recentTurns().size() - (properties.getRecentTurnCount() - 1));
    }

    private List<ConversationTurnSnapshot> remainingTurns(ConversationContextSnapshot snapshot,
                                                          ContextDecision decision) {
        if (decision.clearRecentTurns()) {
            return List.of();
        }
        return snapshot.recentTurns().stream()
                .skip(decision.removeOldestTurns())
                .toList();
    }

    private String parseCompressedSummary(String response) {
        String json = stripMarkdownFence(response);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("compressed context must be a JSON object");
            }
            String summary = requiredText(root, "rollingSummary");
            if (summary.length() > MAX_ROLLING_SUMMARY_LENGTH
                    || tokenEstimator.estimate(summary) > properties.getRollingSummaryTargetTokens()) {
                throw new IllegalArgumentException("compressed summary exceeds its target budget");
            }
            return summary;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("compressed context is not valid JSON", exception);
        }
    }

    private void rejectOverBudget(String prompt) {
        queryMetricsService.recordTokenBudgetRejected();
        tokenBudgetService.requireWithinBudget(prompt);
        throw new BusinessException(ResultCode.CONTEXT_WINDOW_EXCEEDED);
    }

    private ModelPlan parsePlan(String response, boolean summaryRequired) {
        String json = stripMarkdownFence(response);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("context plan must be a JSON object");
            }
            String standaloneQuestion = requiredText(root, "standaloneQuestion");
            if (standaloneQuestion.length() > MAX_STANDALONE_QUESTION_LENGTH) {
                throw new IllegalArgumentException("standalone question is too long");
            }
            boolean topicChanged = root.path("topicChanged").asBoolean(false);
            JsonNode stateNode = root.path("structuredState");
            String structuredState = stateNode.isObject()
                    ? objectMapper.writeValueAsString(stateNode)
                    : "{}";
            if (structuredState.length() > MAX_STRUCTURED_STATE_LENGTH) {
                throw new IllegalArgumentException("structured state is too long");
            }
            String rollingSummary = root.path("rollingSummary").asText("").trim();
            if (rollingSummary.length() > MAX_ROLLING_SUMMARY_LENGTH) {
                throw new IllegalArgumentException("rolling summary is too long");
            }
            if (summaryRequired && !topicChanged && !StringUtils.hasText(rollingSummary)) {
                throw new IllegalArgumentException("rolling summary is required");
            }
            return new ModelPlan(
                    standaloneQuestion, topicChanged, structuredState, rollingSummary);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("context plan is not valid JSON", exception);
        }
    }

    private String requiredText(JsonNode root, String fieldName) {
        String value = root.path(fieldName).asText("").trim();
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private String stripMarkdownFence(String response) {
        String value = response == null ? "" : response.trim();
        if (value.startsWith("```")) {
            int firstLineEnd = value.indexOf('\n');
            value = firstLineEnd >= 0 ? value.substring(firstLineEnd + 1) : "";
        }
        if (value.endsWith("```")) {
            value = value.substring(0, value.length() - 3);
        }
        return value.trim();
    }

    private boolean hasReusableContext(ConversationContextSnapshot snapshot) {
        return !snapshot.recentTurns().isEmpty()
                || StringUtils.hasText(snapshot.rollingSummary())
                || StringUtils.hasText(snapshot.structuredState());
    }

    private boolean isContextDependent(String question) {
        String value = question == null ? "" : question.trim();
        return value.length() <= 30 && CONTEXT_DEPENDENT_QUESTION.matcher(value).find();
    }

    private record ModelPlan(String standaloneQuestion,
                             boolean topicChanged,
                             String structuredState,
                             String rollingSummary) {
    }

    private record ContextDecision(String rollingSummary,
                                   long summaryUntilTurn,
                                   int removeOldestTurns,
                                   boolean clearRecentTurns) {
    }

    private record PreparedPlanning(ConversationContextSnapshot snapshot,
                                    int compactionTurnCount,
                                    String prompt) {
    }
}
