# Day27 五个核心技术点与面试回答

## 0. 今天的目标

Day26 解决的是“代码为什么这样组织”，Day27 解决的是“如何在面试中把它讲清楚”。

今天不再扩展功能，而是完成三件事：

1. 用 1 分钟说明项目解决了什么问题；
2. 用五个核心技术点证明自己理解设计与取舍；
3. 面对追问时，能够从业务问题讲到源码、安全边界和后续优化。

面试回答统一使用下面的顺序：

```text
业务问题 → 方案选择 → 关键实现 → 异常与安全 → 取舍和改进
```

不要只报技术名词，也不要把尚未完成的能力说成已经实现。

---

## 1. 项目介绍怎么说

### 1.1 30 秒版本

> 我做的是一个 AI 企业数据分析助手。用户输入自然语言问题，系统结合业务元数据让大模型生成 SQL，经过 Prompt 注入防护和基于 JSqlParser AST 的安全审核后，使用只读数据源查询业务库。结果会先脱敏，再返回动态表格和 AI 业务总结，同时保存可追溯的查询历史。项目采用 Spring Boot 3、Vue 3、MySQL、Redis、LangChain4j 和 DeepSeek，核心重点不是聊天，而是让 AI 生成的 SQL 能够安全、可控、可降级地查询真实业务数据。

### 1.2 1 分钟版本

> 这个项目解决的是企业人员不会写 SQL，但又希望通过自然语言查询真实业务数据的问题。完整链路是：JWT 鉴权后，先做 Prompt 注入、写操作意图、语义和限流校验；然后查询 Redis 精确缓存，未命中时把 YAML 业务元数据和用户问题交给 DeepSeek 生成 SQL。模型输出始终按不可信输入处理，必须经过 JSqlParser AST 审核、表白名单和 LIMIT 控制，之后才会通过独立的只读 JdbcTemplate 查询业务库。原始结果先脱敏，脱敏副本才允许进入 AI 总结、前端、Redis 和审计历史。系统还针对 SQL 语法或字段错误提供最多两次 AI 自纠错，并对缓存、总结和异步历史采用不同的降级策略。第一期采用模块化单体，因为当前只有一条完整业务闭环，优先保证安全、可测试和可交付。

### 1.3 一句话调用链

```text
JWT 身份 → 请求守卫 → Redis → Text-to-SQL → AST 审核 → 只读查询
→ 数据脱敏 → AI 总结 → 缓存响应 → 异步审计
```

---

## 2. 核心技术点一：业务元数据驱动的 Text-to-SQL

### 2.1 标准回答

> 第一个核心点是业务元数据驱动的 Text-to-SQL。用户问题不能直接交给模型，因为模型既不了解本地表结构，也不知道“销售额”“有效订单”等业务口径。我把允许查询的表、字段、关联关系和业务术语维护在 YAML 中，由 `BusinessMetadataService` 转成 Prompt 上下文，再由 `TextToSqlPromptBuilder` 加入固定规则和问题边界，最后通过 LangChain4j 调用 DeepSeek。模型输出只负责提供候选 SQL，不能直接执行，后面必须经过独立审核。这样把业务知识从 Java 代码中分离出来，同时让 Prompt 使用的表信息和 SQL 审核白名单来自同一份元数据，降低配置不一致风险。

### 2.2 为什么不能只把数据库 DDL 给模型

DDL 能告诉模型物理结构，却不能完整表达业务语义。例如：

- “销售额”可能只统计已支付或已完成订单；
- “客户等级”可能对应固定枚举；
- “今年”需要结合日期字段和当前时间；
- 某些表虽然存在，但不允许 AI 查询。

因此元数据至少需要表达：

```text
物理结构 + 表关联 + 业务术语 + 查询约束
```

### 2.3 关键实现

- [business-metadata.yml](../src/main/resources/business-metadata.yml)：表、字段、关联和术语配置；
- [BusinessMetadataServiceImpl](../src/main/java/com/aianalyst/service/impl/BusinessMetadataServiceImpl.java)：把配置转换为模型上下文；
- [TextToSqlPromptBuilder](../src/main/java/com/aianalyst/service/prompt/TextToSqlPromptBuilder.java)：组装固定规则并隔离用户问题；
- [DeepSeekChatServiceImpl](../src/main/java/com/aianalyst/service/impl/DeepSeekChatServiceImpl.java)：通过 LangChain4j 调用兼容 OpenAI 协议的模型；
- [TextToSqlServiceImpl](../src/main/java/com/aianalyst/service/impl/TextToSqlServiceImpl.java)：生成 SQL、清理 Markdown 代码块并进入审核。

### 2.4 面试追问

**问：为什么不让模型直接读取数据库结构？**

答：第一期使用显式元数据，权限边界更清楚，Prompt 内容更稳定，也方便把业务术语和允许查询范围一起维护。自动读取数据库结构可以作为后续能力，但仍要经过筛选，不能把所有库表无差别暴露给模型。

**问：怎样降低模型幻觉？**

答：生成阶段通过明确的表字段、关系和业务规则约束模型；生成后再用 AST、表白名单、LIMIT 和只读账号兜底。不能指望只靠 Prompt 消除幻觉。

**问：元数据更新不及时怎么办？**

答：当前代价是 YAML 需要人工和数据库结构同步。后续可以增加启动期元数据校验、Schema 版本号或自动比对任务，但自动同步后的内容仍需要权限审核。

### 2.5 不要说错

- 不要说模型“理解了整个数据库”，它只获得允许提供的元数据；
- 不要说 Prompt 可以保证安全，Prompt 只能改善生成质量；
- 不要说当前使用了 RAG 或向量库，项目没有实现这两项能力。

---

## 3. 核心技术点二：AI SQL 的纵深安全与双数据源隔离

### 3.1 标准回答

> 第二个核心点是对 AI 生成 SQL 做纵深防御。模型输出本质上是不可信输入，所以我没有只检查它是不是以 SELECT 开头，而是建立了多层防线：模型调用前拦截 Prompt 注入和写操作意图；模型调用后用 JSqlParser 把 SQL 解析成 AST，只接受单条 SELECT；从 AST 提取所有表名并与业务元数据白名单比对；拒绝 UNION、文件导出以及 SLEEP、BENCHMARK 等危险能力；没有 LIMIT 时补 1000，超过 1000 时压缩。执行阶段再增加 10 秒查询超时，并通过独立只读账号访问业务库。即使应用层审核遗漏，数据库账号也没有写权限。

### 3.2 安全链路

```text
Prompt 注入拦截
    ↓
写操作意图和语义校验
    ↓
Prompt 生成约束
    ↓
JSqlParser 单语句、SELECT、结构审核
    ↓
表白名单、危险能力、LIMIT ≤ 1000
    ↓
具名 businessJdbcTemplate + 10 秒超时
    ↓
MySQL 只读账号最终兜底
```

任何一层都不能替代其他层：

- Prompt 约束可能被模型忽略；
- 正则难以正确理解嵌套 SQL 和注释；
- AST 能理解结构，但不能代替数据库最小权限；
- 只读账号能阻止写入，但不能阻止读取未授权表或超大结果集。

### 3.3 为什么使用双数据源

| 数据源 | 数据库 | 权限 | 访问方式 |
|---|---|---|---|
| 系统数据源 | `ai_analyst` | 正常读写 | MyBatis Plus |
| 业务数据源 | `ai_business` | 仅 `SELECT` | 具名 `businessJdbcTemplate` |

这不是 MySQL 主从读写分离，而是按职责、账号权限和访问方式进行隔离。

业务查询的返回列随问题变化，无法提前定义固定 Entity，因此使用 `List<Map<String, Object>>`；用户和查询历史结构固定，适合使用 Mapper。

### 3.4 关键实现

- [DefaultQueryRequestGuard](../src/main/java/com/aianalyst/service/impl/DefaultQueryRequestGuard.java)：模型调用前的守卫顺序；
- [RegexQueryPromptInjectionValidator](../src/main/java/com/aianalyst/service/impl/RegexQueryPromptInjectionValidator.java)：Prompt 注入拦截；
- [SqlAuditServiceImpl](../src/main/java/com/aianalyst/service/impl/SqlAuditServiceImpl.java)：AST、表白名单和 LIMIT；
- [DataSourceConfig](../src/main/java/com/aianalyst/config/DataSourceConfig.java)：隔离系统库和业务库；
- [SqlExecutionServiceImpl](../src/main/java/com/aianalyst/service/impl/SqlExecutionServiceImpl.java)：审核后执行和查询超时。

### 3.5 面试追问

**问：为什么 SQL 审核不用正则？**

答：正则适合补充识别危险函数，但无法可靠处理大小写、注释、子查询、JOIN 和多语句。语句类型、表引用和 LIMIT 等结构必须基于 AST 判断。

**问：SELECT 为什么也不安全？**

答：SELECT 仍可能访问系统表或未授权表，调用文件读取、休眠函数，拼接多语句，或者返回海量数据拖垮连接、内存和网络。

**问：为什么表白名单不直接硬编码在审核类里？**

答：Prompt 和审核如果维护两份表集合，扩表时容易不一致。当前从业务元数据派生白名单，让模型可见范围和审核允许范围保持同源。

**问：当前 SQL 审核还有什么不足？**

答：当前完成了表级白名单，还没有完整的 AST 字段级白名单，也没有全面禁止普通 `SELECT *`。后续要处理表别名、结果别名、子查询、CTE 和同名字段歧义。

### 3.6 不要说错

- 不要把双数据源说成主从复制；
- 不要说正则是 SQL 安全的主防线；
- 不要说只读账号能代替应用审核；
- 不要说已经实现字段级白名单。

---

## 4. 核心技术点三：Redis 精确缓存与原子限流

### 4.1 标准回答

> 第三个核心点是 Redis 的性能优化和流量治理。缓存方面，我会先对问题做 trim、合并空格和小写化，再计算 MD5，并把用户 ID 放进 Key，格式是 `query_cache:v1:{userId}:{questionHash}`。缓存 30 分钟，只保存已经审核、执行、脱敏并生成总结的完整响应。当前是归一化文本的精确缓存，不是向量语义缓存。限流方面使用 `rate_limit:{userId}`，通过 Lua 把读取、初始化、扣减和过期放在 Redis 内原子执行，实现每用户每分钟 5 次的固定窗口。缓存失败允许降级，限流失败不能静默放行，因为前者只影响性能，后者保护系统流量和模型费用。

### 4.2 为什么缓存 Key 必须带 userId

即使两个用户问题相同，也不能默认共享结果，因为未来可能存在：

- 不同用户的数据权限；
- 不同租户的业务数据；
- 不同用户的缓存失效策略；
- 缓存结果中的审计和展示差异。

把 `userId` 纳入 Key 是当前最基本的隔离边界。

### 4.3 为什么限流在缓存之前

缓存命中仍会消耗：

- Web 请求线程；
- Redis 连接；
- 序列化和反序列化；
- 审计历史任务。

如果先查缓存再限流，攻击者可以利用热点问题绕过流量保护。因此当前顺序是：

```text
请求安全校验 → 限流 → 查询缓存
```

### 4.4 两种 Redis 故障策略

| 场景 | Redis 故障后的行为 | 原因 |
|---|---|---|
| 读缓存失败 | 当作未命中，继续主流程 | 缓存不是正确性前提 |
| 写缓存失败 | 返回成功结果，只记录日志 | 查询已经成功 |
| 限流失败 | 请求失败，不静默放行 | 保护系统和模型费用 |

### 4.5 关键实现

- [RedisQueryCacheServiceImpl](../src/main/java/com/aianalyst/service/impl/RedisQueryCacheServiceImpl.java)：Key、TTL、序列化和缓存降级；
- [RedisRateLimitServiceImpl](../src/main/java/com/aianalyst/service/impl/RedisRateLimitServiceImpl.java)：Lua 固定窗口；
- [DataQueryServiceImpl](../src/main/java/com/aianalyst/service/impl/DataQueryServiceImpl.java)：限流、缓存和主流程的先后顺序。

### 4.6 面试追问

**问：为什么 MD5 可以用于缓存 Key？**

答：这里使用 MD5 只是缩短 Key 并避免直接暴露完整问题文本，不用于密码、签名或安全认证，因此不依赖它的抗碰撞安全性。

**问：当前缓存为什么不是真正的语义缓存？**

答：它只对归一化文本做精确 Hash，没有 Embedding、向量索引和相似度阈值。“前十名客户”和“最好的十个客户”语义接近，但不会自动命中。

**问：当前限流为什么不是令牌桶？**

答：当前 Key 在首次访问时设置 60 秒 TTL，窗口内递减剩余次数，没有持续补充令牌的速率和桶容量模型，所以是固定窗口。它实现简单，但窗口交界处可能产生突发流量。

**问：为什么使用 Lua？**

答：如果 Java 分多次调用 GET、判断、DECR 和 EXPIRE，并发请求可能同时读到相同额度并超额放行。Lua 让整个判断和扣减在 Redis 内原子完成。

### 4.7 不要说错

- 不要把精确缓存说成向量语义缓存；
- 不要把固定窗口说成令牌桶；
- 不要说 Redis 故障时所有能力都统一放行或统一失败；
- 不要说不同用户会共享缓存结果。

---

## 5. 核心技术点四：有限 AI 自纠错与分级降级

### 5.1 标准回答

> 第四个核心点是对 AI 和外部依赖做有限自纠错与分级降级。模型生成的 SQL 可能字段名错误或语法不符合 MySQL，因此执行失败后，我会沿异常链分类，只有排除权限错误后的 `BadSqlGrammarException` 才允许模型修复，最多两次。网络、超时、权限不足和安全审核拒绝都不会重试，因为重新生成 SQL 无法解决这些问题。每次修复后的 SQL 必须重新经过完整安全审核，防止纠错通道成为绕过审核的入口。除此之外，缓存故障会降级为未命中，AI 总结失败会返回降级文案但保留查询数据，异步历史失败只记录日志。不同依赖根据是否影响核心结果采用不同策略。

### 5.2 为什么不能执行失败就全部重试

| 异常 | AI 修复 | 原因 |
|---|---|---|
| 排除权限问题后的 SQL 语法/字段错误 | 最多 2 次 | 模型可能修复 |
| SQL 安全审核拒绝 | 不修复 | 安全拒绝不是语法问题 |
| 连接失败 | 不修复 | 生成新 SQL 不能恢复网络 |
| 查询超时 | 不修复 | 盲目重试可能继续占用资源 |
| 权限不足 | 不修复 | 不能让模型尝试绕过权限 |

MySQL 某些权限错误可能使用 SQLState `42000`，并被 Spring 包装成 `BadSqlGrammarException`。因此代码会优先检查：

- `PermissionDeniedDataAccessException`；
- SQLState `28xxx`；
- MySQL 权限错误码 `1044/1045/1142/1143/1227/1370`。

只有排除权限问题后，才判断是否可纠错。

### 5.3 核心、增强和旁路能力

| 能力 | 定位 | 故障行为 |
|---|---|---|
| SQL 生成、审核和执行 | 核心能力 | 失败则查询失败 |
| AI 结果总结 | 增强能力 | 返回降级总结，保留数据 |
| Redis 查询缓存 | 性能能力 | 降级为正常查询 |
| 查询历史 | 旁路能力 | 记录日志，不改变响应 |
| Redis 限流 | 保护能力 | 故障时拒绝，避免失控放行 |

### 5.4 关键实现

- [DataQueryServiceImpl](../src/main/java/com/aianalyst/service/impl/DataQueryServiceImpl.java)：查询编排、异常分类和最多两次纠错；
- [TextToSqlServiceImpl](../src/main/java/com/aianalyst/service/impl/TextToSqlServiceImpl.java)：生成与修复后重新审核；
- [ResultAnalysisServiceImpl](../src/main/java/com/aianalyst/service/impl/ResultAnalysisServiceImpl.java)：总结采样和降级；
- [GlobalExceptionHandler](../src/main/java/com/aianalyst/handler/GlobalExceptionHandler.java)：不向前端泄露原始数据库异常。

### 5.5 面试追问

**问：为什么最多两次？**

答：纠错次数需要在成功率、响应时间和模型费用之间取舍。无限循环可能让错误 Prompt 或不可修复问题持续消耗资源，两次可以展示恢复能力，又保持明确上限。

**问：为什么纠错后还要重新审核？**

答：纠错模型输出仍然是不可信输入。如果修复结果直接执行，就等于给模型提供了一条绕过主审核流程的通道。

**问：AI 总结失败为什么还返回成功？**

答：用户的核心目标是得到真实查询数据；总结只是增强能力。第三方模型故障不应该抹掉已经成功取得并脱敏的数据。

**问：为什么把编排放在 DataQueryService，而不是 Controller？**

答：Controller 只负责 HTTP 协议适配。流程顺序、重试、降级和失败审计属于业务用例，集中在编排 Service 后更容易单元测试，也避免 Controller 变成不可维护的大方法。

---

## 6. 核心技术点五：敏感数据边界、异步审计与可观测性

### 6.1 标准回答

> 第五个核心点是把敏感数据边界和审计链路落实到数据流中。业务库原始结果只在当前查询方法内短暂存在，随后立即创建脱敏副本；只有脱敏数据才能进入 AI 总结、HTTP 响应、Redis 和查询历史，避免敏感数据扩散到第三方模型或其他存储。成功、失败、缓存命中和安全拒绝都会形成审计记录，用户 ID 只能来自验证后的 JWT Principal，不能由前端传入。历史写入属于旁路任务，使用独立有界线程池异步执行，队列满时告警并拒绝任务，不使用 `CallerRunsPolicy` 反向拖慢主请求。系统还用 Request ID、MDC、Micrometer 和 Actuator 记录端到端耗时、缓存命中和线程池状态，但不把问题、SQL 和结果作为指标标签。

### 6.2 脱敏必须放在哪里

正确的数据流：

```text
业务库原始结果
    ↓
创建脱敏副本
    ├─→ AI 总结
    ├─→ HTTP 响应
    ├─→ Redis 缓存
    └─→ 查询历史
```

如果先缓存、总结或保存历史，再做脱敏，原始手机号、邮箱、身份证号或银行卡号就可能进入第三方模型、Redis、系统库和前端，扩大泄露面。

### 6.3 为什么历史记录异步保存

用户同步需要的是 SQL、数据和总结；历史记录是旁路审计，不应该增加主请求时延。

线程池使用：

```text
corePoolSize = 2
maxPoolSize = 4
queueCapacity = 200
rejectionPolicy = AbortPolicy
shutdownWait = 10 秒
```

不使用无界队列，是为了避免积压耗尽内存；不使用 `CallerRunsPolicy`，是为了避免请求线程在队列满时亲自落库，从而把旁路压力传递回主链路。

### 6.4 JWT 与审计身份边界

- JWT 登录成功后由服务端签发；
- 后续请求验证签名、issuer 和过期时间；
- 验签后再次查询用户状态，让被禁用账号的旧 Token 立即失效；
- Controller 从 `@AuthenticationPrincipal` 取得当前用户；
- 查询历史只能按这个服务端 `userId` 查询，不能接收前端指定的 userId。

### 6.5 关键实现

- [RegexDataMaskingService](../src/main/java/com/aianalyst/service/impl/RegexDataMaskingService.java)：动态结果值脱敏；
- [QueryHistoryServiceImpl](../src/main/java/com/aianalyst/service/impl/QueryHistoryServiceImpl.java)：异步保存脱敏历史和用户隔离；
- [QueryHistoryExecutorConfig](../src/main/java/com/aianalyst/config/QueryHistoryExecutorConfig.java)：有界线程池；
- [JwtAuthenticationFilter](../src/main/java/com/aianalyst/security/JwtAuthenticationFilter.java)：Token 与用户状态校验；
- [RequestIdFilter](../src/main/java/com/aianalyst/filter/RequestIdFilter.java)：Trace ID 和 MDC；
- [MicrometerQueryMetricsService](../src/main/java/com/aianalyst/service/impl/MicrometerQueryMetricsService.java)：查询与线程池指标。

### 6.6 面试追问

**问：正则脱敏有什么局限？**

答：优点是适配动态列，不依赖固定字段名；缺点是可能误判或漏判。后续可以结合列元数据、字段敏感级别和规则引擎进行增强，但动态 SQL 场景仍需要值级兜底。

**问：异步历史会不会丢？**

答：当前极端队列满或进程异常时可能丢失单条记录，这是保护主链路的明确取舍。如果审计升级为强合规能力，应采用消息队列或事务 Outbox，并设计幂等消费和失败补偿。

**问：为什么指标标签不能放 SQL 或问题？**

答：SQL 和问题可能包含敏感信息，而且高基数标签会导致监控系统时间序列数量膨胀。指标只记录计数、耗时和状态，详细上下文通过受控日志和 Request ID 关联。

### 6.7 关于 RocketMQ 的诚实表达

RocketMQ 当前只完成了本地环境安装，尚未接入项目代码。面试可以说：

> 当前审计历史使用本地有界线程池异步保存。下一步计划用 RocketMQ 承担查询完成和安全拒绝事件，消费者幂等落库；如果要求数据库状态与消息严格一致，还需要引入事务 Outbox，不能只发送一条消息就宣称绝对可靠。

不能说“项目已经使用 RocketMQ”。原文档曾规划 RabbitMQ，当前候选选型改为 RocketMQ，但只有真正完成生产者、消费者、重试、幂等和验收后，才算项目已接入。

---

## 7. 三分钟项目回答模板

> 我做的是一个 AI 企业数据分析助手，目标是让不熟悉 SQL 的业务人员通过自然语言查询企业数据。后端采用 Spring Boot 3，前端使用 Vue 3，模型通过 LangChain4j 调用 DeepSeek，系统数据和业务数据分别存放在两个 MySQL 数据库中，Redis 用于缓存和限流。
>
> 一次查询首先经过 JWT 身份校验，然后做 Prompt 注入、写操作意图、语义和每用户限流。缓存未命中时，系统将 YAML 中维护的表、字段、关联和业务术语放入 Prompt，让模型生成候选 SQL。模型输出不能直接执行，而是通过 JSqlParser 做单语句、SELECT、表白名单、危险能力和 LIMIT 审核，再使用独立只读账号查询业务库。
>
> 我重点处理了几个工程问题。第一，Prompt 约束、AST 审核、查询超时和只读账号形成纵深安全，避免把安全寄托在模型上。第二，Redis 缓存按用户隔离，并明确它只是归一化文本的精确缓存；限流通过 Lua 原子实现固定窗口，而且缓存故障可降级、限流故障不放行。第三，只对排除权限错误后的 SQL 语法或字段异常做最多两次 AI 自纠错，每次修复都重新审核。第四，原始结果先脱敏，脱敏副本才能进入模型、前端、缓存和审计。第五，AI 总结失败会降级但保留数据，历史记录使用独立有界线程池异步写入。
>
> 第一期选择模块化单体，因为只有一条明确业务闭环，优先保证调用链清晰和可测试。当前不足是只完成表级白名单、精确缓存和本地异步审计，后续准备补充字段级 AST 审核、向量语义缓存，以及使用 RocketMQ 承担可恢复的异步审计事件。

---

## 8. 高频综合问题

### 8.1 这个项目最难的点是什么

> 最难的不是调用大模型，而是让模型输出能够进入真实数据库查询链路。模型有幻觉、输出格式不稳定，还可能受到 Prompt 注入，所以我把模型定位为候选 SQL 生成器，正确性和安全性由业务元数据、AST 审核、只读数据源、脱敏和异常分类共同保证。工程上真正有价值的是这些边界和失败策略，而不只是一次 API 调用。

### 8.2 为什么不直接做微服务

> 当前只有一个自然语言查询闭环，模块化单体已经可以通过接口划分职责。过早拆微服务会引入注册中心、远程调用、分布式事务和部署复杂度，却不会直接提升业务价值。后续如果报告生成、审计和通知形成独立扩缩容需求，再按业务边界拆分更合理。

### 8.3 为什么不用 MyBatis 执行业务动态 SQL

> MyBatis Mapper 适合结构固定的查询，而 AI SQL 的返回列由用户问题决定，无法提前定义固定 Entity。项目先统一审核 SQL，再通过具名的只读 `businessJdbcTemplate` 返回 `List<Map<String,Object>>`，同时确保动态 SQL 永远不会误用系统库读写账号。

### 8.4 如何证明项目不是简单套壳

> 模型调用只是链路中的两个节点：生成 SQL 和总结结果。项目还实现了业务元数据、Prompt 注入拦截、AST 安全审核、双数据源、只读账号、Redis 缓存和限流、有限自纠错、数据脱敏、异步审计、统一异常以及指标监控。这些能力决定模型能否安全进入企业数据场景。

### 8.5 如果重新设计，你最先改什么

> 第一优先级是字段级 AST 白名单，因为当前表级白名单仍可能读取表中的敏感列；第二是把审计从本地线程池升级为消息队列加幂等消费，并根据一致性要求考虑 Outbox；第三是用真实问题和模型费用数据判断是否值得引入向量语义缓存，而不是为了技术名词直接增加复杂度。

### 8.6 你怎样测试这条 AI 链路

> 单元测试通过接口注入隔离模型、Redis 和数据库，重点覆盖缓存命中、审核拒绝、语法纠错、权限错误不纠错、总结降级和历史拒绝等分支；安全测试覆盖 Prompt 注入、传统注入和危险 SQL；集成测试按需连接真实 MySQL、Redis 和 DeepSeek。单元测试负责稳定验证异常分支，集成测试负责证明真实依赖可连接，两者不能互相替代。

---

## 9. 简历项目描述参考

可以从下面四条中选择三条，不要全部堆进一段：

- 基于 Spring Boot 3、LangChain4j 与 DeepSeek 构建企业 Text-to-SQL 查询闭环，通过 YAML 业务元数据约束表结构、关联关系与业务口径；
- 基于 JSqlParser AST 实现单语句 SELECT、表白名单、危险能力和 LIMIT 审核，配合双数据源、查询超时与 MySQL 只读账号形成纵深安全；
- 基于 Redis 实现按用户隔离的 30 分钟精确查询缓存，以及 Lua 原子固定窗口限流，并为缓存和限流设计不同故障策略；
- 实现 SQL 语法类错误最多两次 AI 自纠错、结果脱敏、AI 总结降级、异步审计、Request ID 与 Micrometer 指标。

不要在简历中写：

- 向量数据库或真正语义缓存；
- 微服务、分布式事务或多租户；
- 字段级 SQL 白名单；
- 已接入 RabbitMQ 或 RocketMQ；
- MySQL 主从读写分离。

---

## 10. Day27 练习方法

### 第一轮：照稿讲

1. 讲 30 秒项目介绍；
2. 每个核心点控制在 60 至 90 秒；
3. 先保证术语准确，不追求脱稿。

### 第二轮：只看关键词讲

每个技术点只保留五个词：

| 技术点 | 五个关键词 |
|---|---|
| Text-to-SQL | 元数据、业务术语、Prompt 边界、候选 SQL、同源白名单 |
| SQL 安全 | 注入拦截、AST、表白名单、LIMIT、只读账号 |
| Redis | 用户隔离、精确缓存、30 分钟、Lua、固定窗口 |
| 自纠错 | 异常分类、权限优先、最多两次、重新审核、分级降级 |
| 数据边界 | 先脱敏、JWT userId、异步审计、有界队列、Request ID |

### 第三轮：随机追问

不看答案，依次回答：

1. 为什么模型输出 SELECT 仍不能执行？
2. 为什么正则不能承担 SQL 主审核？
3. 双数据源为什么不是主从读写分离？
4. 为什么限流在缓存之前？
5. 缓存和限流的 Redis 故障策略为什么不同？
6. 当前缓存为什么不是向量语义缓存？
7. 当前限流为什么不是令牌桶？
8. 为什么权限错误不能进入 AI 自纠错？
9. 为什么纠错结果还要审核？
10. 为什么总结失败仍然返回查询成功？
11. 为什么原始数据必须先脱敏？
12. 为什么异步线程池不用无界队列和 `CallerRunsPolicy`？
13. 为什么用户 ID 不能由前端传入？
14. 当前最值得改进的三点是什么？
15. RocketMQ 在项目中已经做到什么程度？

---

## 11. Day27 完成标准

完成今天任务后，你应该能够：

- 30 秒和 1 分钟介绍项目；
- 不看文档说出完整调用链；
- 每个核心技术点至少讲 60 秒；
- 回答第 10 节至少 12 道追问；
- 主动说清当前实现的取舍和不足；
- 明确区分已实现、已安装和后续规划；
- 不把精确缓存说成语义缓存；
- 不把固定窗口说成令牌桶；
- 不把双数据源说成主从；
- 不把 RocketMQ 说成已经接入项目。

如果只能背出技术名词，却说不出业务问题、失败策略和设计代价，说明还没有真正完成 Day27。
