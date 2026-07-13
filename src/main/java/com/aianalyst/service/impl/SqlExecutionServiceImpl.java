package com.aianalyst.service.impl;

import com.aianalyst.common.SqlExecutionException;
import com.aianalyst.service.SqlExecutionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 动态 SQL 只能通过业务库的具名只读 JdbcTemplate 执行，而不能走普通 Mapper。
 * 上游已完成 AST 审核，数据库账号的 SELECT 权限则是最后一道不可绕过的防线。
 */
@Service
public class SqlExecutionServiceImpl implements SqlExecutionService {

    private static final int QUERY_TIMEOUT_SECONDS = 10;

    private final JdbcTemplate businessJdbcTemplate;

    public SqlExecutionServiceImpl(@Qualifier("businessJdbcTemplate") JdbcTemplate businessJdbcTemplate) {
        this.businessJdbcTemplate = businessJdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> executeAuditedSelect(String auditedSql) {
        try {
            return businessJdbcTemplate.query(
                    auditedSql,
                    // 限制慢 SQL 占用连接池；超时后交由统一异常处理返回安全提示。
                    statement -> statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS),
                    // 查询列由 LLM 动态决定，使用 Map 返回可避免为每种报表预先创建实体类。
                    new ColumnMapRowMapper());
        } catch (DataAccessException exception) {
            // 保留原始异常供自纠错判断，但绝不把数据库细节直接返回给前端。
            throw new SqlExecutionException(auditedSql, exception);
        }
    }
}
