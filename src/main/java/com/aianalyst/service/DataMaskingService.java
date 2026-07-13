package com.aianalyst.service;

import java.util.List;
import java.util.Map;

/**
 * 对动态查询结果创建可安全对外传播的副本。
 * 返回值可用于前端展示和后续 AI 总结，调用方不应再把原始结果向外传递。
 */
public interface DataMaskingService {

    List<Map<String, Object>> maskRows(List<Map<String, Object>> rows);
}
