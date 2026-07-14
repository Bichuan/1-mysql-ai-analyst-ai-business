package com.aianalyst.service;

import java.util.List;
import java.util.Map;

/**
 * 基于已脱敏查询结果生成面向业务用户的简要结论。
 * 调用方只能传入脱敏行数据，原始数据库结果不属于该服务的输入边界。
 */
public interface ResultAnalysisService {

    String analyze(String question, String sql, List<Map<String, Object>> maskedRows, int totalRowCount);
}
