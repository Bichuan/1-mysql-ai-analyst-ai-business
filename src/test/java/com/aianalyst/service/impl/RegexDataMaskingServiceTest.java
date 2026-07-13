package com.aianalyst.service.impl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegexDataMaskingServiceTest {

    private final RegexDataMaskingService dataMaskingService = new RegexDataMaskingService();

    @Test
    void shouldMaskSensitiveValuesWithoutMutatingOriginalRows() {
        Map<String, Object> rawRow = new LinkedHashMap<>();
        rawRow.put("phone", "13812345678");
        rawRow.put("id_card", "110101199001011234");
        rawRow.put("email", "test@gmail.com");
        rawRow.put("bank_card", "6222021234567890");
        rawRow.put("customer_name", "企业客户001");
        rawRow.put("sales", 19081.2D);

        Map<String, Object> maskedRow = dataMaskingService.maskRows(List.of(rawRow)).get(0);

        assertThat(maskedRow).containsEntry("phone", "138****5678")
                .containsEntry("id_card", "1101**********1234")
                .containsEntry("email", "t***@gmail.com")
                .containsEntry("bank_card", "6222********7890")
                .containsEntry("customer_name", "企业客户001")
                .containsEntry("sales", 19081.2D);
        assertThat(rawRow).containsEntry("phone", "13812345678")
                .containsEntry("id_card", "110101199001011234")
                .containsEntry("email", "test@gmail.com")
                .containsEntry("bank_card", "6222021234567890");
    }

    @Test
    void shouldReturnEmptyListForNoRows() {
        assertThat(dataMaskingService.maskRows(List.of())).isEmpty();
    }
}
