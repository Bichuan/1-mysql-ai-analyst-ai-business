package com.aianalyst.service.impl;

import com.aianalyst.service.DataMaskingService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 基于数据值特征的结果脱敏服务。动态 SQL 的返回列不固定，不能只依赖字段名判断敏感字段。
 * 本实现只创建脱敏副本，不修改 JdbcTemplate 返回的原始行数据，避免副作用扩散。
 */
@Service
public class RegexDataMaskingService implements DataMaskingService {

    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
            "(?<![0-9Xx])(\\d{4})\\d{10}([0-9Xx]{4})(?![0-9Xx])");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{4})\\d{8,11}(\\d{4})(?!\\d)");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])([A-Z0-9])(?:[A-Z0-9._%+-]*)(@[A-Z0-9.-]+\\.[A-Z]{2,})(?![A-Z0-9._%+-])");

    @Override
    public List<Map<String, Object>> maskRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::maskRow).toList();
    }

    private Map<String, Object> maskRow(Map<String, Object> row) {
        Map<String, Object> maskedRow = new LinkedHashMap<>();
        row.forEach((column, value) -> maskedRow.put(column, maskValue(value)));
        return maskedRow;
    }

    private Object maskValue(Object value) {
        if (!(value instanceof CharSequence)) {
            return value;
        }

        // 先处理身份证，避免 18 位身份证又被银行卡规则错误识别。
        String masked = ID_CARD_PATTERN.matcher(value.toString()).replaceAll("$1**********$2");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("$1****$2");
        masked = BANK_CARD_PATTERN.matcher(masked).replaceAll("$1********$2");
        return EMAIL_PATTERN.matcher(masked).replaceAll("$1***$2");
    }
}
