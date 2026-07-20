# 第28天：对话上下文分层存储

## 1. 今天完成了什么

第28天完成的是后续上下文窗口和 Token 预算的存储基础，不是多轮语义理解本身。

已经实现：

- `/queries/query` 支持可选的 `conversationId`；
- 第一次查询由后端生成 UUID，并在响应中返回；
- 后续请求可以携带相同 UUID 继续同一个会话；
- `conversationId` 必须和 JWT 中的 `userId` 共同校验；
- MySQL 保存完整的会话和消息记录；
- Redis 使用 Hash 保存会话状态，使用 List 保存最近 3 轮精简对话；
- Redis 未命中时从 MySQL 恢复并重新预热；
- Redis Key 设置 2 小时 TTL，每次访问都会刷新；
- 查询审计写入完成后，将 `query_history.id` 关联到对话消息；
- 被安全守卫拒绝的输入不会创建或污染可复用上下文；
- 前端会在当前页面生命周期内复用后端返回的 `conversationId`。

第28天交付时还没有实现：

- 不会把最近 3 轮发送给 LLM；
- 不会把“那华东呢”改写成完整问题；
- 不会生成滚动摘要；
- 不会计算上下文 Token；
- 不会在达到 80% 时压缩。

这些能力当前已经分别在第29天和第30天完成；这里保留的是分阶段建设过程，避免一次改动同时改变存储、Prompt 和模型行为。

## 2. 当前请求链路

```text
用户问题 + 可选 conversationId
        ↓
从 JWT 获取 userId
        ↓
写操作、语义、Prompt 注入检查 + 限流
        ↓
创建或恢复 userId 所属会话
        ↓
原有查询缓存、Text-to-SQL、安全审核、只读执行、脱敏、总结
        ↓
异步写入 query_history，取得生成的 historyId
        ↓
MySQL 写入 USER/ASSISTANT 两条会话消息
        ↓
Redis 更新最近 3 轮工作集
```

安全守卫位于会话创建之前，因此恶意输入只能进入原有审计记录，不能进入 Redis 最近对话。

## 3. API 协议

### 3.1 第一次查询

请求可以继续使用旧格式：

```json
{
  "question": "查询2025年各地区销售额"
}
```

响应增加 `conversationId`：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "question": "查询2025年各地区销售额",
    "sql": "SELECT ...",
    "rows": [],
    "rowCount": 0,
    "summary": "本次查询没有返回数据。",
    "cacheHit": false,
    "conversationId": "7bc58b98-9b9d-4f6f-9fa5-429d94f2ee4a"
  }
}
```

### 3.2 后续查询

```json
{
  "conversationId": "7bc58b98-9b9d-4f6f-9fa5-429d94f2ee4a",
  "question": "那华东呢？"
}
```

第28天最初只负责把这两个问题保存进同一个会话；当前项目已经继续完成第29天，后端会先把第二句改写为包含上一轮条件的独立问题，再生成 SQL。

### 3.3 向后兼容

旧客户端不传 `conversationId` 仍然可以查询，每次会自动创建一个新会话。服务层原来的双参数调用也保留了默认入口。

## 4. MySQL 持久层

### 4.1 conversation_session

一行代表一个会话，主要字段：

| 字段 | 用途 |
|---|---|
| `conversation_id` | 对外使用的 UUID，全局唯一 |
| `user_id` | 会话所有者，来源于 JWT |
| `rolling_summary` | 第29天使用的滚动摘要槽位 |
| `summary_until_turn` | 摘要覆盖到的轮次 |
| `current_turn` | 当前最大轮次 |
| `structured_state` | 后续保存指标、维度和过滤条件 |
| `estimated_tokens` | 第30天使用的 Token 统计槽位 |
| `version` | 并发更新版本 |
| `last_active_at` | 最近活跃时间 |

`conversation_id` 使用全局唯一索引，因此知道其他用户的 UUID 也不能把它注册到自己的账户下。

### 4.2 conversation_message

每一轮保存两行：

```text
turn 1 / USER
turn 1 / ASSISTANT
```

USER 行保存：

- 用户原始问题；
- 独立问题；
- 状态。

ASSISTANT 行保存：

- 脱敏后的回答摘要；
- `query_history_id`；
- 状态。

这里不复制完整结果行。查询明细仍然只存在于原来的查询响应和 `query_history` 脱敏副本中，避免上下文存储再次扩散数据。

### 4.3 为什么要关联 query_history

`conversation_message` 负责回答“用户第几轮问了什么”，`query_history` 负责回答“生成了什么 SQL、是否通过审核、执行是否成功”。

两者通过 `query_history_id` 关联，既不重复存储 SQL 审计详情，又能从一轮对话定位到完整审计记录。

## 5. Redis 热数据层

### 5.1 会话状态 Hash

```text
conversation:v1:{userId:conversationId}:meta
```

例如：

```text
conversation:v1:{7:7bc58b98-9b9d-4f6f-9fa5-429d94f2ee4a}:meta
```

Hash 字段包括：

```text
userId
conversationId
rollingSummary
summaryUntilTurn
structuredState
estimatedTokens
currentTurn
version
lastActiveTime
```

### 5.2 最近对话 List

```text
conversation:v1:{userId:conversationId}:turns
```

List 的一个元素代表一整轮，而不是一条消息，防止裁剪后只剩问题或只剩回答。

默认保留 3 轮，可以配置为 3～5 轮：

```yaml
app:
  conversation:
    redis-ttl: 2h
    recent-turn-count: 3
```

### 5.3 为什么 Key 中有花括号

两个 Key 都包含相同的 Hash Tag：

```text
{userId:conversationId}
```

在 Redis Cluster 中，它们会落到同一个哈希槽，Lua 才能原子操作两个 Key。

### 5.4 当前使用的 Lua

Redis 回填和追加最近轮次都使用 Lua：

- MySQL 回填时采用 first-writer-wins，避免两个并发请求删除、覆盖对方的数据；
- 新增成功轮次时，在一次原子操作中更新版本、追加 List、裁剪长度并刷新两个 Key 的 TTL。

Redis 写入失败只记录日志。因为 MySQL 已经是持久化事实来源，查询结果不会因此变成失败。

## 6. Redis 未命中时怎样恢复

```text
读取 meta
    ↓ 未命中
按 conversationId 查询 MySQL
    ↓
校验 session.user_id == JWT userId
    ↓
读取最近 3 轮成功消息
    ↓
原子回填 Redis Hash + List
```

如果会话属于其他用户，直接返回 403；不会用请求参数中的用户 ID，也不会创建同名会话绕过校验。

## 7. 关键源码入口

| 代码 | 职责 |
|---|---|
| `dto/QueryRequest.java` | 接收可选 `conversationId` |
| `vo/QueryResultVO.java` | 返回服务端确认的 `conversationId` |
| `service/impl/DataQueryServiceImpl.java` | 在原查询链路中编排会话创建和消息记录 |
| `service/impl/ConversationContextServiceImpl.java` | Redis 优先、MySQL 回源的会话服务 |
| `service/impl/ConversationPersistenceService.java` | 事务化写入会话和两条消息 |
| `service/impl/RedisConversationContextStore.java` | Hash、List、TTL 与 Lua 原子更新 |
| `entity/ConversationSession.java` | 会话实体 |
| `entity/ConversationMessage.java` | 消息实体 |
| `sql/05_conversation_context_schema.sql` | 已有数据库升级脚本 |

## 8. 本地数据库升级

新建的 Docker 数据卷会自动执行 `05_conversation_context_schema.sql`。已有 MySQL 数据库不会重新执行 Docker 初始化脚本，需要手动运行一次迁移。

推荐使用 MySQL Workbench：

1. 使用有建表权限的管理员账号连接；
2. 打开 `sql/05_conversation_context_schema.sql`；
3. 确认目标库是 `ai_analyst`；
4. 执行整个脚本；
5. 确认出现 `conversation_session` 和 `conversation_message`。

不要删除已有 Docker volume 来触发初始化，那会同时删除现有数据库数据。

## 9. 手工验收

### 9.1 检查 MySQL

```sql
SELECT conversation_id, user_id, current_turn, version, last_active_at
FROM ai_analyst.conversation_session
ORDER BY id DESC
LIMIT 10;

SELECT session_id, turn_id, role, original_content, answer_summary, query_history_id, status
FROM ai_analyst.conversation_message
ORDER BY id DESC
LIMIT 20;
```

### 9.2 检查 Redis

先通过 `SCAN` 找到实际 Key：

```text
SCAN 0 MATCH conversation:v1:* COUNT 100
```

再查看：

```text
HGETALL conversation:v1:{7:<conversationId>}:meta
LRANGE conversation:v1:{7:<conversationId>}:turns 0 -1
TTL conversation:v1:{7:<conversationId>}:meta
```

预期：

- Hash 中有当前轮次和版本；
- List 最多 3 个元素；
- List 只包含问题和回答摘要，没有完整结果行；
- TTL 大约为 7200 秒，并会在继续提问后刷新。

## 10. 自动化测试结果

项目执行：

```text
mvn -o test
```

结果：

```text
Tests run: 143, Failures: 0, Errors: 0, Skipped: 15
BUILD SUCCESS
```

跳过的是需要真实 MySQL、Redis、DeepSeek 或鉴权环境的显式集成测试，不是失败用例。

## 11. 面试回答

> 第28天我先为多轮上下文搭建了分层存储。每个会话使用服务端确认的 UUID，并和 JWT 用户 ID 绑定。MySQL 中的 session 表保存会话状态，message 表保存 USER 和 ASSISTANT 消息，同时通过 queryHistoryId 关联原有 SQL 审计记录。Redis 中使用 Hash 保存摘要、结构化状态、版本和活跃时间，使用 List 保存最近 3 轮精简对话。Redis 未命中时从 MySQL 恢复；两个 Key 使用相同 Hash Tag，并通过 Lua 原子回填和追加。对话存储不保存完整查询结果，也不会让安全校验未通过的输入进入可复用上下文。

## 12. 第29天的衔接点

第29天可以直接基于今天留下的字段继续完成：

```text
rollingSummary
summaryUntilTurn
structuredState
recentTurns
originalQuestion
standaloneQuestion
version
```

下一步的重点是上下文组装、追问改写、新话题识别，以及“先生成摘要、再裁剪旧轮次”。
