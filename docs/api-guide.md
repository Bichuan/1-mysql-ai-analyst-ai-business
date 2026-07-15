# Knife4j 接口调用指南

## 1. 访问地址

本地启动后端后打开：

```text
http://localhost:8080/api/doc.html
```

原始 OpenAPI JSON：

```text
http://localhost:8080/api/v3/api-docs
```

生产配置默认关闭 Knife4j 和 OpenAPI，避免公开内部接口结构。

## 2. 统一响应格式

所有业务接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

HTTP 状态码表示协议层结果，`code` 表示业务结果。调用方不能只判断 HTTP 200，也需要检查 `code` 是否为 `0`。

## 3. 推荐测试顺序

### 第一步：注册

调用 `POST /auth/register`：

```json
{
  "username": "testuser01",
  "password": "Test123456",
  "nickname": "测试用户",
  "email": "testuser01@example.com"
}
```

用户名只能包含字母、数字和下划线，长度为 4 到 50 个字符；密码长度为 8 到 64 个字符。

### 第二步：登录

调用 `POST /auth/login`：

```json
{
  "username": "testuser01",
  "password": "Test123456"
}
```

成功响应中的 `data.accessToken` 是服务器签发的 JWT，默认有效期为 7200 秒。

### 第三步：配置 Bearer Token

1. 点击 Knife4j 页面右上角的授权按钮。
2. 找到 `BearerAuth`。
3. 输入 `Bearer`、一个空格，再粘贴 `accessToken`：

```text
Bearer <accessToken>
```

这里必须包含 `Bearer ` 前缀，不能只粘贴 Token。

### 第四步：确认当前用户

调用 `GET /users/me`。返回当前用户 ID、用户名、昵称、邮箱和角色，不返回密码哈希。

### 第五步：执行完整查询

调用 `POST /queries/query`：

```json
{
  "question": "查询今年销售额最高的10个客户"
}
```

响应包含：

- `question`：原始自然语言问题；
- `sql`：审核并规范化后的只读 SQL；
- `rows`：脱敏后的动态查询结果；
- `rowCount`：返回行数；
- `summary`：基于脱敏数据生成的 AI 总结；
- `cacheHit`：是否命中 Redis 缓存。

再次提交相同用户的相同问题，可以观察 `cacheHit` 变为 `true`。

### 第六步：查看审计历史

调用：

```text
GET /query-histories?page=1&size=10
```

分页结果只包含当前 JWT 用户的历史，不允许通过请求参数查看其他用户数据。

## 4. 安全场景测试

### 写操作意图

```json
{
  "question": "删除第1个客户的订单"
}
```

预期：HTTP 400，业务码 `40003`，不会调用模型生成 SQL，也不会修改数据库。

### 非法 TopN

```json
{
  "question": "查询今年销售额最高的-1个客户"
}
```

预期：参数语义校验拒绝。

### 未携带 JWT

直接调用 `/users/me`、`/queries/**` 或 `/query-histories`。

预期：HTTP 401，业务码 `40100`。

### 普通用户访问管理员指标

```text
GET /actuator/metrics/ai.query.cache.hit
```

预期：HTTP 403，只有 `ADMIN` 可以查看详细指标。

## 5. 常用业务码

| HTTP | 业务码 | 含义 |
|---:|---:|---|
| 200 | 0 | 成功 |
| 400 | 40001 | 请求参数不合法 |
| 400 | 40002 | SQL 安全审核未通过 |
| 400 | 40003 | 仅支持只读查询 |
| 401 | 40100 | 未登录或 Token 失效 |
| 403 | 40300 | 无访问权限 |
| 404 | 40400 | 请求资源不存在 |
| 429 | 42900 | 查询过于频繁 |
| 500 | 50002 | SQL 执行失败 |
| 500 | 50000 | 系统异常 |

## 6. 接口分组

| 分组 | 接口 | 是否需要 JWT |
|---|---|---|
| 系统状态 | `GET /health`、`GET /actuator/health` | 否 |
| 认证管理 | `POST /auth/register`、`POST /auth/login` | 否 |
| 用户管理 | `GET /users/me` | 是 |
| AI 数据查询 | `POST /queries/generate-sql`、`POST /queries/query` | 是 |
| 查询历史 | `GET /query-histories` | 是 |
| 管理员指标 | `GET /actuator/metrics/**` | ADMIN |

## 7. 常见问题

### 显示“未登录或登录已失效”

- 检查授权内容是否为 `Bearer <accessToken>`；
- 检查 `Bearer` 后是否有一个空格；
- Token 默认两小时过期，过期后重新调用登录接口；
- 重启后端但修改了 JWT 密钥时，旧 Token 会立即失效。

### SQL 生成接口与完整查询接口有什么区别

- `/queries/generate-sql` 用于单独观察 Text-to-SQL 和审核后的 SQL，不执行数据库查询；
- `/queries/query` 执行完整链路，并返回数据、AI 总结、缓存状态及异步审计历史。

### 为什么查询历史不会立即出现

历史记录使用独立线程池异步保存，通常很快可见。客户端可在短暂间隔后刷新，不应假设查询响应返回前历史一定已经落库。
