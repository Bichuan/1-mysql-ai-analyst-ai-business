package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegexQuerySemanticValidatorTest {

    private final RegexQuerySemanticValidator validator = new RegexQuerySemanticValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "查询今年销售额最高的-1个客户",
            "查询前 0 个客户",
            "查询排名前1001名客户",
            "Top -1 customers",
            "LIMIT 1001"
    })
    void shouldRejectRankCountOutsideOneToOneThousand(String question) {
        assertThatThrownBy(() -> validator.validate(question))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.PARAM_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "查询今年销售额最高的10个客户",
            "查询前 1000 条订单",
            "Top 5 customers by sales",
            "查询销售额小于-1万的客户",
            "查询2024年订单"
    })
    void shouldAllowValidCountOrNonRankingNegativeCondition(String question) {
        assertThatCode(() -> validator.validate(question)).doesNotThrowAnyException();
    }
}
