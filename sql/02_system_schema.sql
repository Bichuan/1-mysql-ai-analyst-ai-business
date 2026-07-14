USE ai_analyst;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt密码哈希',
    nickname VARCHAR(50) NOT NULL COMMENT '昵称',
    email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用/1启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    UNIQUE KEY uk_sys_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS query_history (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    natural_language TEXT NOT NULL COMMENT '自然语言问题',
    generated_sql TEXT DEFAULT NULL COMMENT '最终生成SQL',
    sql_audit_result VARCHAR(20) DEFAULT NULL COMMENT '审核结果：PASS/REJECT',
    sql_audit_reason VARCHAR(500) DEFAULT NULL COMMENT '审核原因',
    query_result LONGTEXT DEFAULT NULL COMMENT '脱敏后的查询结果JSON',
    ai_summary TEXT DEFAULT NULL COMMENT 'AI分析摘要',
    execution_time INT DEFAULT NULL COMMENT 'SQL执行耗时（毫秒）',
    status VARCHAR(20) NOT NULL COMMENT 'SUCCESS/FAIL/AUDIT_REJECT',
    error_message TEXT DEFAULT NULL COMMENT '错误摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_query_history_user_created (user_id, created_at DESC),
    KEY idx_query_history_status_created (status, created_at DESC),
    CONSTRAINT fk_query_history_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自然语言查询审计历史';
