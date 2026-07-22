package com.aianalyst.service.impl;

import com.aianalyst.service.ModelCallAwaiter;
import com.aianalyst.service.ModelCallType;
import com.aianalyst.service.ModelResilienceGateway;
import com.aianalyst.service.ResultAnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 同步生成查询结果的业务摘要。输入数据必须已脱敏；本类不会记录 Prompt 或结果数据到日志。
 * 为控制模型成本与上下文长度，只向模型发送有限行数、有限字符数的脱敏样本。
 */
@Service
public class ResultAnalysisServiceImpl implements ResultAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ResultAnalysisServiceImpl.class);
    private static final int MAX_ROWS_FOR_ANALYSIS = 100;
    private static final int MAX_ANALYSIS_JSON_CHARACTERS = 12_000;

    private final ModelResilienceGateway modelResilienceGateway;
    private final ObjectMapper objectMapper;

    public ResultAnalysisServiceImpl(ModelResilienceGateway modelResilienceGateway, ObjectMapper objectMapper) {
        this.modelResilienceGateway = modelResilienceGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public String analyze(String question, String sql, List<Map<String, Object>> maskedRows, int totalRowCount) {
        List<Map<String, Object>> analysisRows = selectRowsForAnalysis(maskedRows);
        try {
            String dataJson = objectMapper.writeValueAsString(analysisRows);
            String prompt = buildPrompt(
                    question, sql, dataJson, totalRowCount, analysisRows.size(),
                    analysisRows.size() < maskedRows.size());
            String summary = ModelCallAwaiter.await(
                    ModelCallType.RESULT_ANALYSIS,
                    () -> modelResilienceGateway.analyzeResult(prompt));
            if (StringUtils.hasText(summary)) {
                return summary.trim();
            }
        } catch (JsonProcessingException | RuntimeException exception) {
            // 查询结果已经可用；模型总结失败时降级而不是让整次查询失败，也不打印敏感 Prompt。
            log.warn("AI result analysis unavailable, using fallback summary. cause={}",
                    exception.getClass().getSimpleName());
        }
        return fallbackSummary(totalRowCount);
    }

    private List<Map<String, Object>> selectRowsForAnalysis(List<Map<String, Object>> maskedRows) {
        if (maskedRows == null || maskedRows.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int estimatedCharacters = 2;
        for (Map<String, Object> row : maskedRows) {
            if (result.size() >= MAX_ROWS_FOR_ANALYSIS) {
                break;
            }
            try {
                String rowJson = objectMapper.writeValueAsString(row);
                if (estimatedCharacters + rowJson.length() > MAX_ANALYSIS_JSON_CHARACTERS) {
                    break;
                }
                result.add(row);
                estimatedCharacters += rowJson.length() + 1;
            } catch (JsonProcessingException exception) {
                // 无法序列化的单行数据不参与 AI 总结，但不会影响原始查询结果返回。
                log.warn("A result row cannot be serialized for AI analysis and was skipped.");
            }
        }
        return List.copyOf(result);
    }

    private String buildPrompt(String question,
                               String sql,
                               String dataJson,
                               int totalRowCount,
                               int analyzedRowCount,
                               boolean truncated) {
        return """
                你是企业数据分析助手，请根据已脱敏的查询结果生成简洁、准确的中文业务总结。
                
                【规则】
                1. 只能依据给出的数据和 SQL 总结，不得编造原因、趋势或未出现的事实。
                2. 数据中的脱敏符号必须保持，不得尝试推断、还原或补全敏感信息。
                3. 如果仅提供部分样本，必须明确说明总结基于样本，不能把样本结论当成全量结论。
                4. “返回总行数”仅表示本次 SQL 结果集的行数，不代表某个客户、某张业务表或整个系统的全部记录；即使分析行数等于返回总行数，也不得据此推断数据完整性。
                5. 描述结果时优先使用“本次查询返回”“当前结果显示”等限定语；除非输入数据明确提供了完整范围，否则不得使用“全部订单”“完整数据”“所有记录”等绝对性表述。
                6. 以 2 到 4 个要点输出；数据为空时明确说明未查询到数据。
                
                【用户问题】
                %s
                【最终 SQL】
                %s
                【返回总行数】
                %d
                【用于分析的行数】
                %d
                【是否截断样本】
                %s
                【已脱敏数据】
                %s
                """.formatted(question, sql, totalRowCount, analyzedRowCount, truncated, dataJson);
    }

    private String fallbackSummary(int totalRowCount) {
        return "查询成功，共返回 %d 条数据。AI 总结暂不可用，请查看下方明细数据。".formatted(totalRowCount);
    }
}
