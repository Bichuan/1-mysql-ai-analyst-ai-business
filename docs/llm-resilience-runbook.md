# 模型熔断、超时与线程池隔离运维手册

## 1. 保护边界

系统把阻塞式模型调用统一放入有界 `@Async` 线程池，再按业务阶段经过
`CircuitBreaker + TimeLimiter`。模型输出解析、SQL AST 审核和数据库执行位于保护边界之外，
因此本地格式或业务校验失败不会误伤模型熔断器。

| 阶段 | 熔断器 | 默认超时 | 线程池 | 熔断/超时后的行为 |
|---|---|---:|---|---|
| 上下文规划 | `llm-context-planning` | 15s | `llm-core` | 完整问题按单轮处理；省略式追问提示补全条件 |
| Text-to-SQL（含有界纠错） | `llm-text-to-sql` | 20s | `llm-core` | 返回 HTTP 503，不进入 SQL 审核和数据库执行 |
| 上下文压力压缩 | `llm-context-compression` | 15s | `llm-core` | 当前查询失败，原始上下文不更新、不裁剪 |
| 结果总结 | `llm-result-analysis` | 10s | `llm-analysis` | 保留 SQL 和脱敏数据，返回“AI 总结暂不可用” |

TimeLimiter 结束的是调用方等待时间，不能假设所有第三方 HTTP 客户端都能立即中断底层
阻塞 I/O。因此 DeepSeek 原生 HTTP 超时仍保留为 25 秒最终停止线，有界线程池负责限制残留
任务数量。

## 2. 熔断参数

四个实例共享以下默认配置：

- 计数滑动窗口 20 次，至少 10 次调用后才计算；
- 失败率达到 50% 或慢调用率达到 60% 时进入 OPEN；
- 慢调用阈值 15 秒；
- OPEN 保持 30 秒，之后允许 3 个 HALF_OPEN 探测调用；
- 自动从 OPEN 进入 HALF_OPEN。

只记录模型超时、网络 I/O、认证失败、429 和 5xx 等供应商可用性故障。业务异常、Prompt
预算拒绝、非法参数和线程池拒绝会被排除出调用窗口，既不算失败，也不伪装成成功样本。

## 3. 可调整环境变量

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `DEEPSEEK_TIMEOUT` | `25s` | 模型 HTTP 客户端最终超时 |
| `LLM_CONTEXT_PLANNING_TIMEOUT` | `15s` | 上下文规划等待上限 |
| `LLM_TEXT_TO_SQL_TIMEOUT` | `20s` | SQL 生成与纠错等待上限 |
| `LLM_CONTEXT_COMPRESSION_TIMEOUT` | `15s` | 压缩等待上限 |
| `LLM_RESULT_ANALYSIS_TIMEOUT` | `10s` | 结果总结等待上限 |
| `LLM_CORE_POOL_SIZE` / `LLM_CORE_MAX_POOL_SIZE` | `4` / `6` | 核心模型线程数 |
| `LLM_CORE_QUEUE_CAPACITY` | `20` | 核心模型等待队列 |
| `LLM_ANALYSIS_POOL_SIZE` / `LLM_ANALYSIS_MAX_POOL_SIZE` | `1` / `2` | 分析线程数 |
| `LLM_ANALYSIS_QUEUE_CAPACITY` | `10` | 分析等待队列 |

不要只放大队列来处理模型变慢。大队列会把快速失败变成长时间排队，并增加超时后仍在底层
运行的任务数量。扩容前应同时观察供应商配额、模型延迟和实例 CPU/内存。

## 4. Actuator 观察入口

除健康检查外，下列入口均要求 ADMIN JWT：

```text
GET /api/actuator/circuitbreakers
GET /api/actuator/circuitbreakerevents
GET /api/actuator/timelimiters
GET /api/actuator/timelimiterevents
GET /api/actuator/metrics/resilience4j.circuitbreaker.calls
GET /api/actuator/metrics/resilience4j.circuitbreaker.state
GET /api/actuator/metrics/resilience4j.circuitbreaker.failure.rate
GET /api/actuator/metrics/resilience4j.timelimiter.calls
GET /api/actuator/metrics/ai.model.executor.active
GET /api/actuator/metrics/ai.model.executor.queue.size
GET /api/actuator/metrics/ai.model.executor.queue.remaining
```

Resilience4j 使用 `name` 区分四个业务阶段；线程池指标使用低基数 `pool=core|analysis|orchestration`
标签。指标中不写入用户 ID、问题、Prompt、SQL 或查询结果。

建议设置以下告警：

- 任一核心熔断器进入 OPEN 立即告警；
- Text-to-SQL TimeLimiter 超时持续增长立即告警；
- `core` 队列使用率连续 5 分钟超过 80% 告警；
- `analysis` 队列拥塞仅降级总结，但仍应告警并核对模型延迟；
- 503 比例增加时同时检查熔断事件和线程池剩余容量，区分供应商故障与本机过载。

## 5. 自动化故障验收

以下测试全部使用 Mock 模型，不访问真实 DeepSeek，也不会产生费用：

```powershell
.\mvnw.cmd -Dtest=ModelResilienceGatewayIntegrationTest test
.\mvnw.cmd -Dtest=AsyncModelInvokerImplTest test
.\mvnw.cmd -Dtest=ModelExecutorMetricsTest test
```

验收覆盖：连续超时触发 OPEN、OPEN 后快速拒绝、永不完成的 Future 被 TimeLimiter 截止、
线程池拒绝不污染熔断窗口、核心池饱和时分析池仍可工作，以及线程池指标按固定标签注册。

不要直接用真实模型对 `/queries/query` 做高并发故障演练。完整链路压测应使用 Mock 模型、
独立测试数据库、独立账号和明确的并发上限，避免模型费用与业务数据库风险。
