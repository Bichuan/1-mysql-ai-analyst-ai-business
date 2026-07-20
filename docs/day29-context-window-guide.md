# 第29天：最近窗口、追问改写与滚动摘要

## 1. 今天完成了什么

第29天让第28天保存的会话真正参与查询推理。现在同一个 `conversationId` 下，系统会使用：

```text
滚动摘要 + 最近 3 个完整成功轮次 + 当前问题
                    ↓
一次上下文规划模型调用
                    ↓
独立问题 + 是否切换主题 + 结构化状态 + 新滚动摘要
                    ↓
二次安全校验
                    ↓
缓存 / Text-to-SQL / SQL审核 / 只读执行
```

例如：

```text
第1轮：查询今年销售额最高的10个客户
第2轮：那华东呢？
```

第2轮进入 Text-to-SQL 的不再是含义不完整的“那华东呢？”，而是类似：

```text
查询今年华东地区销售额最高的10个客户
```

响应中的 `question` 仍回显“那华东呢？”，数据库会同时保存原始问题与独立问题，便于审计和排查。

## 2. 滚动摘要和滑动窗口是什么关系

它们是配合关系，但不是同一个概念：

- 滑动窗口决定哪些最近轮次保留原文；
- 滚动摘要负责承接离开窗口的早期信息；
- 当前实现固定保留最近 3 个完整成功轮次；
- 第4个新问题到来前，先把最早的第1轮合并进摘要，确认摘要保存成功后再删除其 Redis 原文；
- 本轮查询成功后追加为新的完整轮次，窗口重新保持 3 轮。

因此，不能把流程写成“List 超长就直接 `LTRIM`”。如果先删除再摘要，模型超时或摘要保存失败时，第1轮信息会永久丢失。

### 五轮对话示例

| 当前阶段 | 滚动摘要覆盖 | Redis 最近完整轮次 |
|---|---:|---|
| 第1轮完成 | 无 | 1 |
| 第2轮完成 | 无 | 1、2 |
| 第3轮完成 | 无 | 1、2、3 |
| 第4轮规划后、执行前 | 1 | 2、3 |
| 第4轮完成 | 1 | 2、3、4 |
| 第5轮规划后、执行前 | 1～2 | 3、4 |
| 第5轮完成 | 1～2 | 3、4、5 |

活动上下文始终是：

```text
摘要(1～2) + 完整轮次(3、4、5)
```

## 3. 一次规划调用完成四件事

后续轮次不会分别调用四次模型，而是通过一次受约束的 JSON 输出同时完成：

1. `standaloneQuestion`：把追问改写成独立问题；
2. `topicChanged`：判断是不是已经切换到无关新主题；
3. `structuredState`：提取指标、维度、筛选、时间、排序和数量；
4. `rollingSummary`：仅在需要时合并即将离开窗口的轮次。

首轮没有历史上下文，直接返回原始问题，不额外调用规划模型，避免增加延迟和模型费用。

## 4. 为什么还要保存结构化状态

仅靠自然语言摘要容易把条件混在一段文字中。`structuredState` 使用 JSON 保存当前查询状态，例如：

```json
{
  "metric": "销售额",
  "dimensions": ["客户"],
  "filters": [
    {"field": "地区", "operator": "=", "value": "华东"}
  ],
  "timeRange": "今年",
  "orderBy": "销售额 DESC",
  "limit": 10
}
```

用户说“换成去年”时，只替换 `timeRange`，其余条件继续继承。自然语言摘要负责长期语义，结构化状态负责稳定传递查询参数，两者互补。

## 5. 新话题如何处理

如果模型判断当前问题完整且与旧分析无关，会返回 `topicChanged=true`。系统随后：

- 清空旧滚动摘要；
- 清空旧结构化状态并写入新状态；
- 删除 Redis 最近轮次；
- 将 `summaryUntilTurn` 推进到旧会话的当前轮次；
- 当前新问题成功后成为新主题的第一个完整轮次。

这样“查询客户总数”不会错误继承此前“华东、去年、前10名”等条件。

## 6. 安全边界

### 6.1 两次校验，只扣一次限流额度

处理顺序是：

```text
原始问题：意图校验 + Prompt注入校验 + 消耗1次限流额度
独立问题：意图校验 + Prompt注入校验，不再次扣额度
```

原因是独立问题属于模型输出，不能因为来自内部模型就直接信任；但用户只发出了一次 HTTP 请求，不应该重复消耗每分钟额度。

### 6.2 历史上下文按非可信数据处理

规划 Prompt 明确规定 `context_json` 只是非可信业务数据，不允许把其中任何文本当成指令。传给规划器的历史轮次只包含：

```text
turnId
originalQuestion
standaloneQuestion
answerSummary
```

不会传完整结果行、SQL、异常堆栈或敏感字段。最终独立问题仍要经过现有 Prompt 注入拦截；后续 SQL 仍要经过 JSqlParser、白名单、危险能力和只读数据库账号防线。

## 7. 为什么缓存使用独立问题

缓存 Key 使用 `standaloneQuestion`，而不是含义不完整的原话。

例如下面两次追问在不同上下文中含义可能不同：

```text
那华东呢？
```

如果直接使用原话作为 Key，系统可能把“华东客户数”的结果错误返回给“华东销售额”。改写后 Key 会变成完整语义，例如：

```text
查询今年华东地区销售额最高的10个客户
```

因此缓存复用的是完整查询语义，而不是表面文本。

## 8. 一致性与并发处理

### 8.1 会话先同步落库

`query_history` 仍异步记录，但 USER/ASSISTANT 会话轮次会在 HTTP 响应前同步写入 MySQL 和 Redis，写入时 `query_history_id` 暂为空。异步审计记录完成后，再回填 ASSISTANT 消息的外键。

如果反过来等待异步历史记录完成后才保存会话，用户刚收到第1轮响应就立刻发第2轮时，第2轮可能读取不到第1轮。

### 8.2 MySQL 是权威数据源

上下文状态使用 `version` 做乐观锁：

```text
UPDATE ...
WHERE user_id = ? AND conversation_id = ? AND version = expectedVersion
```

只有版本匹配才能更新摘要和结构化状态。Redis Lua 同样检查期望版本，再原子完成 Hash 更新和 List 裁剪。

成功轮次追加也使用版本 CAS。如果两个请求写 MySQL 的顺序与写 Redis 的顺序不一致，系统不会让旧请求覆盖新版本，而是淘汰 Redis 热副本；下一次请求从 MySQL 重建正确上下文。

## 9. 降级策略

- 规划模型失败且问题明显依赖上下文，例如“那华东呢”：拒绝继续猜测，提示用户补全条件；
- 规划模型失败但问题本身完整，例如“查询本月客户总数”：清理旧上下文并按单轮查询降级；
- 摘要更新发生版本冲突：重新加载最新上下文并重试一次；
- Text-to-SQL 输出多语句或无法解析：最多进行 1 次格式纠错并重新完整审核；它与后续执行语法纠错共享最多 2 次总预算；
- 未授权表、危险函数、非 SELECT、权限、超时或连接失败：不进入模型纠错；
- Redis 更新失败：MySQL 已保存权威状态，淘汰 Redis，后续自动恢复；
- 查询失败或被安全规则拒绝：会保存审计轮次，但不会进入可复用的最近窗口。

## 10. 主要源码位置

| 职责 | 源码 |
|---|---|
| 主查询编排与双重校验 | `service/impl/DataQueryServiceImpl.java` |
| 追问改写、主题识别与滚动摘要 | `service/impl/DeepSeekConversationQuestionResolver.java` |
| 上下文规划 Prompt | `service/prompt/ConversationContextPromptBuilder.java` |
| Redis Hash/List 与 Lua CAS | `service/impl/RedisConversationContextStore.java` |
| MySQL 会话状态与消息事务 | `service/impl/ConversationPersistenceService.java` |
| MySQL 状态乐观锁 | `mapper/ConversationSessionMapper.java` |
| 异步审计外键回填 | `mapper/ConversationMessageMapper.java` |
| 上下文快照 | `dto/ConversationContextSnapshot.java` |
| 状态更新命令 | `dto/ConversationContextUpdateCommand.java` |

## 11. 手动验收步骤

确保 MySQL、Redis、后端和前端已启动，并按顺序在同一页面查询：

```text
1. 查询今年销售额最高的10个客户
2. 那华东呢？
3. 换成去年
4. 只看前5名
```

验收点：

1. 四次响应返回相同 `conversationId`；
2. 第2～4次生成的 SQL 能继承此前未修改的条件；
3. `conversation_message.standalone_question` 是完整问题；
4. 第4次查询后，Redis List 仍只有最近 3 个成功轮次；
5. `conversation_session.rolling_summary` 已覆盖第1轮；
6. `summary_until_turn=1`；
7. 重新查询完整新主题“查询客户总数”，不继承销售额条件。

MySQL 检查：

```sql
SELECT conversation_id, rolling_summary, summary_until_turn,
       structured_state, current_turn, version
FROM conversation_session
ORDER BY id DESC
LIMIT 1;

SELECT turn_id, role, original_content, standalone_question,
       answer_summary, query_history_id, status
FROM conversation_message
WHERE session_id = <上一步的id>
ORDER BY turn_id, id;
```

Redis 检查：

```text
HGETALL conversation:v1:{<userId>:<conversationId>}:meta
LRANGE conversation:v1:{<userId>:<conversationId>}:turns 0 -1
```

## 12. 面试回答

> 为了防止多轮问答的上下文不断膨胀，我没有把全部聊天记录直接传给模型，而是采用“滚动摘要 + 最近3个完整成功轮次 + 当前问题”的混合窗口。后续问题先经过一次上下文规划调用，同时完成独立问题改写、主题切换判断、结构化状态提取和旧轮次摘要。窗口溢出时一定先用版本乐观锁把最早轮次合并进摘要，保存成功后再通过 Redis Lua 原子裁剪，避免先删后摘要造成信息丢失。独立问题还会再次经过安全校验，并作为缓存、Text-to-SQL 和审计的统一语义输入。MySQL 是权威存储，Redis 是热上下文；并发乱序时通过版本 CAS 拒绝旧写入并从 MySQL 重建。

## 13. 第30天衔接

第29天解决的是“基于轮次数量的基础窗口”。当前项目已经继续完成第30天：调用前会估算完整 Prompt、预留输出和安全余量；预计超过模型窗口 80% 时先压缩早期上下文，仍超限则安全拒绝。实现与验收见 [第30天 Token 预算指南](day30-token-budget-guide.md)。

最近 3 轮仍是日常滑动窗口，80% 是防止超出模型上下文的安全阀，两者不能互相替代。
