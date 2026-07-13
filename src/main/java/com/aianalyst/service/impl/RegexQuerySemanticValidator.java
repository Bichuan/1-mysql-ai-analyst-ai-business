package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.service.QuerySemanticValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第一阶段的自然语言语义校验器：只识别“排名/数量”这一类明确规则。
 * 规则必须带有排名上下文，不能简单禁止负数，否则会误伤“销售额小于 -1 万”等合法条件。
 */
@Component
public class RegexQuerySemanticValidator implements QuerySemanticValidator {

    private static final long MIN_QUERY_COUNT = 1L;
    private static final long MAX_QUERY_COUNT = 1_000L;

    private static final List<Pattern> RANK_COUNT_PATTERNS = List.of(
            // 例如：前 -1 个客户、排名前 0 名、前 1001 条订单。
            Pattern.compile("(?:排名|排行)?前\\s*([+-]?\\d+)(?:\\s*(?:个|名|条|位|家|项))?(?=\\s*(?:客户|用户|订单|产品|记录|数据|公司|$|[，,。！？?]))"),
            // 例如：销售额最高的 -1 个客户、最少的 0 条订单。
            Pattern.compile("(?:最高|最低|最多|最少)的?\\s*([+-]?\\d+)\\s*(?:个|名|条|位|家|项)(?=\\s*(?:客户|用户|订单|产品|记录|数据|公司|$|[，,。！？?]))"),
            // 英文排名表达不强制要求名词，例如 Top -1、first 1001。
            Pattern.compile("(?i)\\b(?:top|first)\\s*([+-]?\\d+)\\b"),
            // 显式写出 LIMIT 时也按同一个数量范围校验。
            Pattern.compile("(?i)\\blimit\\s*([+-]?\\d+)\\b")
    );

    @Override
    public void validate(String question) {
        for (Pattern pattern : RANK_COUNT_PATTERNS) {
            Matcher matcher = pattern.matcher(question);
            while (matcher.find()) {
                validateCount(matcher.group(1));
            }
        }
    }

    private void validateCount(String rawCount) {
        final long count;
        try {
            count = Long.parseLong(rawCount);
        } catch (NumberFormatException exception) {
            // 超出 Long 表示范围也不是一个可执行的查询数量。
            throw new BusinessException(ResultCode.PARAM_ERROR, "查询数量必须是 1 到 1000 之间的整数");
        }

        if (count < MIN_QUERY_COUNT || count > MAX_QUERY_COUNT) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "查询数量必须是 1 到 1000 之间的整数");
        }
    }
}
