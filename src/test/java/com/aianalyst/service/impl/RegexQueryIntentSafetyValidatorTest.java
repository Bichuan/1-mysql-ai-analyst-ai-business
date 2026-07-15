package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegexQueryIntentSafetyValidatorTest {

    private final RegexQueryIntentSafetyValidator validator = new RegexQueryIntentSafetyValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "删除第1个客户的订单",
            "帮我把第1个客户的订单删除",
            "我想更新订单状态",
            "新增一个客户",
            "DROP TABLE biz_order",
            "please delete customer 1"
    })
    void shouldRejectWriteIntent(String question) {
        assertThatThrownBy(() -> validator.validateReadOnlyIntent(question))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.READ_ONLY_QUERY_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "查询今年销售额最高的10个客户",
            "查询已删除订单的数量",
            "查询销售额小于-1万的客户"
    })
    void shouldAllowReadOnlyQuestionContainingSimilarWords(String question) {
        assertThatCode(() -> validator.validateReadOnlyIntent(question)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "查询客户名称中包含忽略两个字的记录",
            "查询哪些角色拥有删除订单的权限"
    })
    void shouldNotRejectBenignQuestionWithOnlyOneRiskMarker(String question) {
        assertThatCode(() -> validator.validateReadOnlyIntent(question)).doesNotThrowAnyException();
    }
}
