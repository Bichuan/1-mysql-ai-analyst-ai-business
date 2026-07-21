# 第30天：Token 预算、80%硬阈值与压力压缩

## 1. 今天完成了什么

第30天在第29天“滚动摘要 + 最近3轮”的基础上增加模型上下文安全阀：

```text
构建完整 Prompt
    ↓
本地保守估算 Token
    ↓
输入 + 预留输出 + 安全余量 > 模型窗口的80%？
    ├─ 否：允许调用模型
    └─ 是：压缩早期摘要/即将离窗的旧轮次
              ↓
           先保存摘要，再删除旧原文
              ↓
           重新构建并估算
              ├─ 未超限：调用模型
              └─ 仍超限：拒绝调用，提示缩短问题或新建会话
```

所有 DeepSeek 调用都经过最终预算检查，不仅是上下文规划，还包括 Text-to-SQL、SQL 纠错和结果总结。

## 2. 为什么不能只判断字符串长度

模型限制的是 Token，而不是 Java 字符数。中文、英文单词、数字、标点和 JSON 的分词比例不同；同时模型上下文通常还要容纳输出，因此不能写成：

```java
if (prompt.length() > 10000) { ... }
```

当前项目没有依赖远程模型的私有 tokenizer，而是使用本地保守估算：

- 非 ASCII 字符按 2 Token 估算；
- 连续 ASCII 字母和数字按约 4 字符 1 Token；
- ASCII 标点按 1 Token；
- 对上下文对象和每轮消息增加固定结构开销。

这个结果用于安全预算和监控，不是模型厂商的精确计费值。保守高估比低估导致请求超过窗口更安全。

## 3. 80%预算如何计算

默认配置：

```yaml
ai:
  deepseek:
    context-window-tokens: 32768
    context-usage-limit: 0.80
    max-tokens: 2048
    token-safety-margin: 256
```

计算公式：

```text
80%硬阈值 = floor(32768 × 0.80)
           = 26214

最大Prompt预算 = 26214 - 预留输出2048 - 安全余量256
               = 23910 Token
```

判断条件是：

```text
预计Prompt Token + 2048 + 256 > 26214
```

也就是说，不会等输入本身达到窗口的80%才处理，因为那样没有给模型回答留下空间。

模型窗口必须与实际网关配置保持一致，可通过环境变量覆盖：

```text
DEEPSEEK_CONTEXT_WINDOW_TOKENS
DEEPSEEK_CONTEXT_USAGE_LIMIT
DEEPSEEK_MAX_TOKENS
DEEPSEEK_TOKEN_SAFETY_MARGIN
```

未知模型默认按32K窗口保守处理，不能因为供应商宣传更大窗口就擅自写死更高数值。

## 4. 两层 Token 防线

### 4.1 业务层压力压缩

`DeepSeekConversationQuestionResolver` 在发送上下文规划 Prompt 前预估预算。如果超过80%：

1. 选择已经存在的滚动摘要和本轮后应离开最近窗口的最早轮次；
2. 使用独立压缩 Prompt 生成不超过目标预算的摘要；
3. 通过 MySQL `version` 乐观锁保存摘要和 `estimated_tokens`；
4. Redis Lua 在版本一致时原子更新 Hash 并裁剪 List；
5. 使用压缩后的快照重新构建规划 Prompt；
6. 重新估算，合格后才能调用规划模型。

压力压缩默认要求滚动摘要不超过1024个估算 Token：

```yaml
app:
  conversation:
    rolling-summary-target-tokens: 1024
```

### 4.2 模型调用层硬拒绝

`DeepSeekChatServiceImpl` 是所有模型调用的统一出口。即使上层遗漏预算判断，它仍会在网络请求前重新计算；超限时抛出 `CONTEXT_WINDOW_EXCEEDED`，不会把超大 Prompt 发给外部模型。

因此：

```text
业务层负责“能压缩就压缩”
统一模型层负责“任何情况下都不能越过硬阈值”
```

## 5. 最近3轮会不会被破坏

不会静默丢弃受保护的最近轮次。

正常情况下，发起新问题前最多压缩即将离开窗口的最早轮次；当前问题成功后，Redis List 重新保持最近3个完整成功轮次。

如果出现极端情况：滚动摘要已经压缩，受保护的最近轮次和当前问题本身仍然超过80%，系统会拒绝本次模型调用，而不是继续裁剪最近轮次或截断用户问题。用户需要缩短问题或新建会话。

这是一条重要取舍：

```text
信息完整性优先于强行生成一个可能误解上下文的答案
```

## 6. `estimated_tokens` 保存的是什么

### conversation_session.estimated_tokens

保存当前可复用上下文数据的估算值：

```text
rollingSummary
+ structuredState
+ Redis最近成功轮次的原始问题
+ 独立问题
+ 回答摘要
+ 结构开销
```

它不包含每个具体 Prompt 的固定系统规则和输出预留。真实模型调用前仍要对完整 Prompt 重新估算，不能只依赖数据库字段。

### conversation_message.estimated_tokens

USER 消息记录原始问题和独立问题的估算；ASSISTANT 消息记录回答摘要的估算。失败消息可以保留自身估算用于审计，但不会累加进会话的可复用上下文估算。

## 7. 为什么仍然要“先摘要，再删除”

压力压缩也遵循第29天的一致性原则：

```text
模型生成压缩摘要
    ↓
MySQL乐观锁保存摘要、覆盖轮次和Token估算
    ↓
Redis Lua更新Hash并LTRIM List
```

如果模型压缩失败，旧轮次不会删除；如果 MySQL 版本冲突，重新加载后重试；如果 Redis 更新失败，淘汰热缓存并从 MySQL 恢复。

不能为了节省 Token 先 `LTRIM` 再调用模型，否则模型超时会导致早期信息永久丢失。

## 8. 指标与告警入口

新增 Micrometer 指标：

| 指标 | 含义 |
|---|---|
| `ai.model.prompt.tokens.estimated` | 实际发起模型调用的 Prompt 估算分布 |
| `ai.model.token.budget.compression` | 因超过阈值触发压力压缩的次数 |
| `ai.model.token.budget.rejected` | 压缩后仍超限或统一出口拒绝的次数 |

可通过 Actuator 查看：

```text
GET /api/actuator/metrics/ai.model.prompt.tokens.estimated
GET /api/actuator/metrics/ai.model.token.budget.compression
GET /api/actuator/metrics/ai.model.token.budget.rejected
```

生产环境建议为 `rejected` 持续增长以及 Prompt Token 的高分位接近上限配置监控告警。指标不包含用户问题、SQL、用户ID或结果数据。

## 9. 主要源码

| 职责 | 源码 |
|---|---|
| 保守 Token 估算 | `service/impl/ConservativeTokenEstimator.java` |
| Token预算服务接口 | `service/TokenBudgetService.java` |
| 80%预算计算与硬拒绝 | `service/impl/TokenBudgetServiceImpl.java` |
| 预算结果 | `dto/TokenBudgetAssessment.java` |
| 上下文压力压缩 | `service/impl/DeepSeekConversationQuestionResolver.java` |
| 压缩 Prompt | `service/prompt/ConversationContextPromptBuilder.java` |
| 所有模型调用统一出口 | `service/impl/DeepSeekChatServiceImpl.java` |
| MySQL Token 持久化 | `service/impl/ConversationPersistenceService.java` |
| Redis Token 与 Lua 更新 | `service/impl/RedisConversationContextStore.java` |
| Token 指标 | `service/impl/MicrometerQueryMetricsService.java` |

## 10. 验收方法

正常配置下完成几轮查询后检查 MySQL：

```sql
SELECT conversation_id, current_turn, summary_until_turn,
       estimated_tokens, version
FROM conversation_session
ORDER BY id DESC
LIMIT 1;

SELECT turn_id, role, estimated_tokens, status
FROM conversation_message
WHERE session_id = <会话id>
ORDER BY turn_id, id;
```

检查 Redis：

```text
HGETALL conversation:v1:{<userId>:<conversationId>}:meta
LRANGE conversation:v1:{<userId>:<conversationId>}:turns 0 -1
```

`estimatedTokens` 应大于0，并随新成功轮次、滚动摘要和主题切换而重新计算。

为了在本机快速观察压力压缩，可以临时使用更小但仍合法的测试预算：

```text
DEEPSEEK_CONTEXT_WINDOW_TOKENS=4096
DEEPSEEK_MAX_TOKENS=512
DEEPSEEK_TOKEN_SAFETY_MARGIN=128
```

重启后端后进行多轮长问题测试。测试结束应恢复真实模型窗口配置；测试值只用于触发分支，不代表模型真实上限。

## 11. 面试回答

> 为防止长会话超过模型上下文，我在最近3轮滑动窗口之外增加了 Token 硬预算。系统使用本地保守估算器计算完整 Prompt，并把最大输出和安全余量一起计入预算；默认超过模型窗口80%就触发压力压缩。压缩只处理滚动摘要和即将离窗的早期轮次，先通过模型生成受限摘要，再用 MySQL 乐观锁保存，最后由 Redis Lua 原子裁剪。压缩后重新估算，如果受保护的最近轮次本身仍超限，就拒绝调用而不是静默截断。所有模型请求最终还要经过统一出口的预算检查，并通过 Micrometer 记录 Prompt Token、压缩和拒绝指标。`estimated_tokens` 是安全估算，不冒充供应商的精确计费 Token。

## 12. 第31天可以做什么

第30天已经完成规则式 Token 治理。后续增强方向包括：

- 针对实际模型接入精确 tokenizer，并保留保守回退；
- 按 Prompt 类型设置不同输出预留，而不是统一使用最大输出；
- 将 Token 使用量与模型调用费用写入独立统计表；
- 配置 Prometheus/Grafana 告警规则；
- 支持用户主动新建、归档和查看历史会话。
