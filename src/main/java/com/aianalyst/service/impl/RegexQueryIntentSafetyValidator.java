package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.service.QueryIntentSafetyValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 针对命令式写操作的轻量级前置拦截器。
 * 只匹配以写操作为目的的句式，不会因为“查询已删除订单”等查询描述而误拦截。
 * SQL AST 审核和 MySQL 只读账号仍是后续独立防线，三者不互相替代。
 */
@Component
public class RegexQueryIntentSafetyValidator implements QueryIntentSafetyValidator {

    private static final List<Pattern> WRITE_INTENT_PATTERNS = List.of(
            // 例如：删除第 1 个客户、请更新订单状态、我想新增一个客户。
            Pattern.compile("^\\s*(?:(?:请|帮我|给我|麻烦|我想|我要|需要)\\s*)?(?:删除|删掉|移除|清空|修改|更新|新增|添加|插入|创建|建表|删表|恢复|撤销)"),
            // 例如：帮我把第 1 个客户的订单删除。
            Pattern.compile("^\\s*(?:(?:请|帮我|给我|麻烦)\\s*)?把.+?(?:删除|删掉|移除|清空|修改|更新|新增|添加|插入|恢复|撤销)"),
            // 英文命令式写操作。
            Pattern.compile("(?i)^\\s*(?:please\\s+)?(?:delete|update|insert|drop|truncate|alter|create)\\b")
    );

    @Override
    public void validateReadOnlyIntent(String question) {
        boolean hasWriteIntent = WRITE_INTENT_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(question).find());
        if (hasWriteIntent) {
            throw new BusinessException(ResultCode.READ_ONLY_QUERY_REQUIRED);
        }
    }
}
