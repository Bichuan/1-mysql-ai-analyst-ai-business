# AI 企业数据分析助手架构说明

## 1. 设计目标

系统面向企业内部数据分析场景：用户提交自然语言问题，服务端生成并审核 SQL，使用最小权限账号查询业务数据库，对结果脱敏后生成业务总结，同时保留可追溯的审计记录。

第一期采用模块化单体架构。一个月项目的重点是完成安全、可运行、可解释的业务闭环，不引入服务注册、分布式事务等当前规模不需要的复杂度。

## 2. 系统组件

```mermaid
flowchart TB
    subgraph Client[客户端]
        Web[Vue 3 Web]
        Doc[Knife4j]
    end

    subgraph Application[Spring Boot 应用]
        Security[Spring Security / JWT]
        Controller[Controller]
        Orchestrator[DataQueryService]
        Guard[意图与语义校验]
        Text2SQL[Text-to-SQL]
        Audit[SQL AST 审核]
        Execute[只读 SQL 执行]
        Mask[结果脱敏]
        Summary[AI 结果总结]
        History[异步审计历史]
    end

    Redis[(Redis DB 2)]
    SystemDB[(ai_analyst\n系统库)]
    BusinessDB[(ai_business\n业务库)]
    LLM[DeepSeek API]

    Client --> Security --> Controller --> Orchestrator
    Orchestrator --> Guard --> Redis
    Orchestrator --> Text2SQL --> LLM
    Text2SQL --> Audit --> Execute --> BusinessDB
    Execute --> Mask --> Summary --> LLM
    Orchestrator -. CompletableFuture .-> History --> SystemDB
    Orchestrator <--> Redis
```

## 3. 后端分层职责

| 层次 | 主要职责 | 约束 |
|---|---|---|
| Controller | 接收请求、参数校验、读取当前用户、返回统一结果 | 不编写业务流程和数据库逻辑 |
| Service | 单项业务能力与跨服务流程编排 | 核心逻辑可单元测试 |
| Mapper | 用户、查询历史等系统库访问 | 只操作固定表结构 |
| JdbcTemplate | 执行审核后的动态业务 SQL | 只注入具名只读数据源 |
| DTO / VO | 定义外部请求和安全响应 | Entity 不直接返回前端 |
| Security / Filter | JWT 认证、权限控制、Trace ID | 无状态，不创建 HTTP Session |
| Handler | 参数、业务、SQL 与系统异常的统一转换 | 不向客户端泄露数据库原始异常 |

`DataQueryServiceImpl` 是查询用例的编排层，不直接实现每项安全能力。意图校验、缓存、SQL 生成、执行、脱敏、总结和历史分别通过独立接口注入，方便替换与测试。

## 4. 双数据源与权限边界

| 数据源 | 数据库 | 账号权限 | 访问方式 | 保存内容 |
|---|---|---|---|---|
| systemDataSource | `ai_analyst` | 系统表正常读写 | MyBatis Plus | 用户、脱敏后的查询历史 |
| businessDataSource | `ai_business` | 仅 `SELECT` | 具名 `businessJdbcTemplate` | 客户、订单、商品、订单明细 |

动态 SQL 的返回列不固定，因此不建立 `BusinessDataMapper`。即使上层审核发生遗漏，MySQL `app_readonly` 账号也无法修改业务数据，这是数据库层最后一道防线。

## 5. SQL 安全防线

```mermaid
flowchart LR
    Q[自然语言] --> J[Prompt 注入拦截]
    J --> I[写操作意图拦截]
    I --> P[Prompt 约束]
    P --> AST[JSqlParser AST]
    AST --> W[表白名单]
    W --> L[强制 LIMIT ≤ 1000]
    L --> T[JDBC 查询超时]
    T --> RO[MySQL 只读账号]
```

在调用模型前，独立校验器会直接拒绝索取系统 Prompt、覆盖系统指令、角色劫持、jailbreak 和要求生成危险 DDL/DML 等攻击，避免浪费模型费用。SQL 审核只接受单条 `SELECT`，拒绝多语句、未知表、`UNION`、文件读取或导出、休眠和基准测试等危险能力。正则只作为危险能力补充检查，语句类型和表引用以 AST 结果为准。

## 6. SQL 自纠错边界

系统先沿异常链检查 Spring 的 `PermissionDeniedDataAccessException`、SQLState `28xxx` 和 MySQL 权限错误码，再判断是否存在 `BadSqlGrammarException`。只有排除权限问题后的语法或字段类错误，才会把原 SQL、截断后的错误摘要和业务元数据交给模型修复，最多两次。每次修复后的 SQL 都必须重新经过完整审核。

以下情况直接终止，不调用模型重试：

- 安全审核不通过；
- 数据库连接失败或查询超时；
- 权限不足；
- 非语法类数据访问异常。

这样可以防止无限重试、模型费用失控，以及使用“纠错流程”绕过安全审核。

## 7. Redis 设计

| 能力 | Key | 策略 |
|---|---|---|
| 用户查询限流 | `rate_limit:{userId}` | Lua 原子执行，固定窗口每分钟 5 次 |
| 查询结果缓存 | `query_cache:v1:{userId}:{questionHash}` | 用户隔离，问题归一化，TTL 30 分钟 |

缓存内容是已审核、已执行、已脱敏并包含 AI 总结的完整响应。Redis 不可用时，查询缓存会降级为正常查询流程；限流属于费用与流量保护，不静默放行。

## 8. 脱敏与大模型数据边界

业务库原始结果仅在当前请求方法内短暂存在。`DataMaskingService` 先处理手机号、邮箱、身份证号和银行卡号等信息，之后才允许数据进入以下位置：

- AI 结果总结 Prompt；
- HTTP 响应；
- Redis 查询缓存；
- `query_history` 审计记录。

为控制模型上下文和费用，结果总结最多采样 100 行、序列化内容最多约 12000 个字符。

## 9. 并发与审计历史

主查询链路同步返回 SQL、数据和 AI 总结，便于前端一次展示完整结果。历史记录不影响响应，因此通过 `CompletableFuture.runAsync` 写入独立线程池：

- 核心线程数：2；
- 最大线程数：4；
- 有界队列：200；
- 拒绝策略：`AbortPolicy`，记录告警但不反向阻塞主请求；
- 应用关闭时最多等待 10 秒完成队列任务。

## 10. 数据模型

```mermaid
erDiagram
    SYS_USER ||--o{ QUERY_HISTORY : creates
    BIZ_CUSTOMER ||--o{ BIZ_ORDER : owns
    BIZ_ORDER ||--o{ BIZ_ORDER_ITEM : contains
    BIZ_PRODUCT ||--o{ BIZ_ORDER_ITEM : referenced_by

    SYS_USER {
        bigint id PK
        varchar username UK
        varchar password
        varchar role
    }
    QUERY_HISTORY {
        bigint id PK
        bigint user_id FK
        text natural_language
        text generated_sql
        varchar status
    }
    BIZ_CUSTOMER {
        bigint id PK
        varchar customer_name
        varchar customer_level
        varchar region
    }
    BIZ_ORDER {
        bigint id PK
        bigint customer_id FK
        decimal amount
        varchar status
        date order_date
    }
    BIZ_PRODUCT {
        bigint id PK
        varchar product_name
        varchar category
        decimal price
    }
    BIZ_ORDER_ITEM {
        bigint id PK
        bigint order_id FK
        bigint product_id FK
        int quantity
        decimal subtotal
    }
```

## 11. 可观测性

- `RequestIdFilter` 为请求生成或透传 Trace ID，并写入 MDC 日志。
- Actuator 暴露健康状态和 Micrometer 指标。
- 普通用户只能匿名查看不含内部细节的健康状态。
- `/actuator/metrics/**` 只允许 `ADMIN` 访问。
- 指标覆盖查询总量、成功/失败、缓存命中、请求耗时以及历史线程池活跃数和队列长度。

## 12. 后续扩展边界

- 字段级白名单：第二期基于 JSqlParser AST 校验所有物理列引用，解析表别名和查询结果别名，禁止普通 `SELECT *`/`table.*`，但允许不读取具体列值的 `COUNT(*)`；字段集合继续由业务元数据 YAML 统一提供。
- RabbitMQ：适合承担耗时分析任务、审计事件或通知，但第一期同步查询链路不依赖消息队列。
- Nginx 与 Docker Compose：计划在 RabbitMQ 接入后统一完成完整容器部署。
- 多租户：可在用户、元数据、缓存 Key 和业务数据源上增加 tenantId 隔离。
- 向量语义缓存：只有在问题规模和模型费用证明有必要时再引入 Embedding 与相似度检索。
