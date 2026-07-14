package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.service.QueryIntentSafetyValidator;
import com.aianalyst.service.QueryRequestGuard;
import com.aianalyst.service.QuerySemanticValidator;
import com.aianalyst.service.RateLimitService;
import org.springframework.stereotype.Service;

/**
 * 用户查询的统一入口防线：先拒绝非法意图和参数，再消耗 Redis 限流令牌。
 */
@Service
public class DefaultQueryRequestGuard implements QueryRequestGuard {

    private final QueryIntentSafetyValidator queryIntentSafetyValidator;
    private final QuerySemanticValidator querySemanticValidator;
    private final RateLimitService rateLimitService;

    public DefaultQueryRequestGuard(QueryIntentSafetyValidator queryIntentSafetyValidator,
                                    QuerySemanticValidator querySemanticValidator,
                                    RateLimitService rateLimitService) {
        this.queryIntentSafetyValidator = queryIntentSafetyValidator;
        this.querySemanticValidator = querySemanticValidator;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public void validateAndAcquire(Long userId, String question) {
        // 不合法的写操作意图和语义参数不能消耗宝贵的模型调用额度或 Redis 令牌。
        queryIntentSafetyValidator.validateReadOnlyIntent(question);
        querySemanticValidator.validate(question);
        // 限流在缓存前执行：缓存命中虽然不调用模型，也不能被恶意请求无限放大接口流量。
        if (!rateLimitService.tryAcquire(userId)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
    }
}
