# Day24 核心 Service 单元测试报告

## 1. 今日目标

Day24 不新增业务功能，重点是为核心 Service 补齐可重复、无需外部服务的单元测试，避免后续加入 RabbitMQ 或调整流程时出现回归。

本次遵循三个原则：

1. 测试业务行为，而不是为了覆盖率测试 getter/setter。
2. 使用 Mockito 隔离 MySQL、Redis 和大模型网络调用，让失败能够快速定位到当前类。
3. 优先覆盖安全边界、异常分支和容易在面试中被追问的设计决策。

## 2. 新增覆盖矩阵

| 模块 | 新增/增强场景 | 关键断言 |
|---|---|---|
| `AuthServiceImpl` | 注册成功、用户名重复、登录成功、用户不存在、密码错误、账号禁用 | 密码必须 BCrypt 编码；服务端固定普通用户权限；失败时不签发 JWT |
| `UserServiceImpl` | 查询当前用户、用户不存在 | 只返回安全用户 VO；不存在时返回业务异常 |
| `JwtTokenService` | 正常签发解析、Token 篡改、非法格式、错误签发方、过期、短密钥 | 签名、issuer、exp 和最短 32 字节密钥约束有效 |
| `RedisRateLimitServiceImpl` | Lua 放行、额度耗尽、空结果、用户隔离、空用户 | Redis Key 按用户隔离；参数固定为每分钟 5 次；异常结果默认拒绝 |
| `BusinessMetadataServiceImpl` | YAML 元数据转 Prompt、可选列表为空 | 表、字段、关系和业务术语能稳定进入 Prompt；空配置不触发异常 |
| `DeepSeekChatServiceImpl` | 空 Prompt、缺少 API Key | 参数和配置错误在建立网络请求前失败 |
| `SqlAuditServiceImpl` | JOIN、CTE、表白名单、危险函数、UNION、非法 LIMIT、语法错误 | 只允许受控查询；自动补齐/收紧 `LIMIT 1000`；保留安全 CTE 能力 |

本次净新增 22 个测试用例，项目测试总数由 72 增加到 94。

## 3. 重点设计说明

### 3.1 为什么认证测试要验证“没有调用”

只判断异常信息还不够。例如账号禁用时，即使最终接口返回失败，如果代码已经签发了 Token，仍然属于安全缺陷。因此测试通过 Mockito 验证密码错误、用户不存在或账号禁用时不会调用 `JwtTokenService#createToken`。

### 3.2 为什么 Redis 限流使用 Mock 而不连接 Redis

单元测试关注当前 Service 是否使用正确的用户 Key、Lua 脚本参数和失败策略；Redis 本身是否可连接属于集成测试。二者拆开后，单元测试在离线环境也能快速运行，真实 Redis 测试则保留为按需启用的 IntegrationTest。

### 3.3 为什么 SQL 审核同时测试允许与拒绝

安全审核不能只验证“坏 SQL 被拒绝”，否则可能出现所有复杂 SQL 都被误杀的情况。本次同时验证合法 JOIN、合法 CTE 可以通过，以及 DML、多语句、非白名单表、危险函数、UNION 和非法 LIMIT 会被拒绝。

## 4. 执行结果

执行环境：Java 17，Maven 离线模式。

核心定向测试：

```text
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

项目全量测试：

```text
Tests run: 94, Failures: 0, Errors: 0, Skipped: 14
BUILD SUCCESS
```

14 个跳过项均为已有的按需集成测试，需要真实 MySQL、Redis、DeepSeek 或完整应用环境；它们不是失败用例。Day24 新增的单元测试全部执行并通过。

## 5. 本地复现

在 IDEA Terminal 进入项目根目录，确认使用 Java 17 后执行：

```bat
mvnw.cmd test
```

若只想运行本次重点测试，可在 IDEA 中分别运行以下测试类：

- `AuthServiceImplTest`
- `UserServiceImplTest`
- `JwtTokenServiceTest`
- `RedisRateLimitServiceImplTest`
- `BusinessMetadataServiceImplTest`
- `DeepSeekChatServiceImplTest`
- `SqlAuditServiceImplTest`

## 6. 面试表达

可以这样说明：

> 我把单元测试和集成测试分层。核心 Service 使用 JUnit 5、Mockito 和 AssertJ 隔离外部依赖，重点覆盖认证安全、JWT 边界、Redis Lua 限流和 SQL AST 审核；真实 MySQL、Redis、DeepSeek 调用保留为按需集成测试。这样本地和 CI 可以快速回归，同时不会丢失真实环境验证能力。
