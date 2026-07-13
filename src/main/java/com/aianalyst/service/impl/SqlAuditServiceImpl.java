package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.config.BusinessMetadataProperties;
import com.aianalyst.service.SqlAuditService;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 JSqlParser AST 的 SQL 结构化审核。模型输出始终按不可信输入处理，
 * 不能只靠正则判断关键字，否则容易被大小写、嵌套查询或注释绕过。
 * 第一版只接受简单单条 SELECT，以可控性优先于复杂 SQL 表达能力。
 */
@Service
public class SqlAuditServiceImpl implements SqlAuditService {

    private static final long MAX_ROW_LIMIT = 1000L;
    private static final Pattern DANGEROUS_FUNCTION_PATTERN = Pattern.compile(
            "(?i)\\b(LOAD_FILE|SLEEP|BENCHMARK)\\s*\\(");
    private static final Pattern DANGEROUS_EXPORT_PATTERN = Pattern.compile(
            "(?i)\\bINTO\\s+(OUTFILE|DUMPFILE)\\b");

    private final Set<String> allowedTables;

    public SqlAuditServiceImpl(BusinessMetadataProperties metadata) {
        // 表白名单由业务元数据派生，新增业务表无需在安全代码中重复硬编码。
        this.allowedTables = metadata.getTables().stream()
                .map(BusinessMetadataProperties.Table::getName)
                .map(this::normalizeIdentifier)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String auditAndNormalize(String sql) {
        if (!StringUtils.hasText(sql)) {
            throw reject("SQL 不能为空");
        }
        String candidate = sql.trim();
        // AST 是主防线；危险函数/文件导出采用额外关键词拦截，形成纵深防御。
        rejectDangerousKeywords(candidate);

        Statement statement = parseSingleStatement(candidate);
        if (!(statement instanceof Select select)) {
            throw reject("只允许 SELECT 查询");
        }

        PlainSelect plainSelect = select.getPlainSelect();
        if (plainSelect == null) {
            throw reject("仅支持单个 SELECT 查询，不支持 UNION 等组合查询");
        }

        verifyTableWhitelist(statement);
        enforceLimit(plainSelect);
        return statement.toString();
    }

    private Statement parseSingleStatement(String sql) {
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            // 多语句可能在一条合法 SELECT 后拼接写操作，因此必须从 AST 层拒绝。
            if (statements.getStatements().size() != 1) {
                throw reject("只允许单条 SQL 语句");
            }
            return statements.getStatements().get(0);
        } catch (JSQLParserException exception) {
            throw reject("SQL 语法解析失败");
        }
    }

    private void verifyTableWhitelist(Statement statement) {
        Set<String> referencedTables = new TablesNamesFinder().getTableList(statement).stream()
                .map(this::normalizeIdentifier)
                .collect(Collectors.toSet());
        for (String table : referencedTables) {
            // 即使模型生成 SELECT，也不能读取未授权表，例如系统用户表或其他业务域数据。
            if (!allowedTables.contains(table)) {
                throw reject("引用了未授权的数据表：" + table);
            }
        }
    }

    private void enforceLimit(PlainSelect plainSelect) {
        Limit limit = plainSelect.getLimit();
        if (limit == null) {
            // LLM 遗漏 LIMIT 时自动兜底，防止一次查询返回大量数据并拖垮内存与网络。
            Limit defaultLimit = new Limit();
            defaultLimit.setRowCount(new LongValue(MAX_ROW_LIMIT));
            plainSelect.setLimit(defaultLimit);
            return;
        }

        Expression rowCount = limit.getRowCount();
        if (!(rowCount instanceof LongValue longValue) || longValue.getValue() < 0) {
            throw reject("LIMIT 必须是非负整数");
        }
        if (longValue.getValue() > MAX_ROW_LIMIT) {
            // 不信任模型指定的超大 LIMIT，统一压缩到服务端上限。
            limit.setRowCount(new LongValue(MAX_ROW_LIMIT));
        }
    }

    private void rejectDangerousKeywords(String sql) {
        if (DANGEROUS_FUNCTION_PATTERN.matcher(sql).find()) {
            throw reject("SQL 包含危险函数");
        }
        if (DANGEROUS_EXPORT_PATTERN.matcher(sql).find()) {
            throw reject("SQL 不允许导出文件");
        }
    }

    private String normalizeIdentifier(String value) {
        return value.replace("`", "").trim().toLowerCase(Locale.ROOT);
    }

    private BusinessException reject(String message) {
        return new BusinessException(ResultCode.SQL_AUDIT_FAILED, message);
    }
}
