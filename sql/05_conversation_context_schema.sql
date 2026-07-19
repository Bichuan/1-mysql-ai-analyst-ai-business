-- Day 28 migration for databases that were created before conversation context was added.
-- Run with the ai_analyst system database selected, using an account with DDL privileges.
USE ai_analyst;

CREATE TABLE IF NOT EXISTS conversation_session (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id CHAR(36) NOT NULL COMMENT '对外暴露的UUID会话ID',
    user_id BIGINT NOT NULL COMMENT 'JWT用户ID',
    title VARCHAR(120) DEFAULT NULL COMMENT '首个问题生成的会话标题',
    rolling_summary TEXT DEFAULT NULL COMMENT '早期对话滚动摘要',
    summary_until_turn BIGINT NOT NULL DEFAULT 0 COMMENT '摘要已覆盖的轮次',
    current_turn BIGINT NOT NULL DEFAULT 0 COMMENT '当前最大轮次',
    structured_state TEXT DEFAULT NULL COMMENT '结构化查询状态JSON',
    estimated_tokens INT NOT NULL DEFAULT 0 COMMENT '当前上下文估算Token数',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '并发更新版本',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
    last_active_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_session_conversation_id (conversation_id),
    KEY idx_conversation_session_user_active (user_id, last_active_at DESC),
    CONSTRAINT fk_conversation_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户对话会话';

CREATE TABLE IF NOT EXISTS conversation_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id BIGINT NOT NULL COMMENT '会话数据库主键',
    turn_id BIGINT NOT NULL COMMENT '会话内轮次',
    role VARCHAR(20) NOT NULL COMMENT 'USER/ASSISTANT',
    original_content TEXT DEFAULT NULL COMMENT '用户原始问题，不保存结果明细',
    standalone_question TEXT DEFAULT NULL COMMENT '结合上下文改写后的独立问题',
    answer_summary TEXT DEFAULT NULL COMMENT '脱敏后的回答摘要',
    query_history_id BIGINT DEFAULT NULL COMMENT '关联查询审计记录',
    status VARCHAR(20) NOT NULL COMMENT 'SUCCESS/FAIL',
    estimated_tokens INT DEFAULT NULL COMMENT '消息估算Token数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_message_turn_role (session_id, turn_id, role),
    KEY idx_conversation_message_session_turn (session_id, turn_id DESC),
    KEY idx_conversation_message_query_history (query_history_id),
    CONSTRAINT fk_conversation_message_session FOREIGN KEY (session_id)
        REFERENCES conversation_session (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_message_query_history FOREIGN KEY (query_history_id)
        REFERENCES query_history (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息历史';
