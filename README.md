# AI 企业数据分析助手

面向企业业务库的自然语言数据查询系统。后端基于 Spring Boot 3、MySQL、Redis、Spring Security、LangChain4j 与大语言模型构建，重点实现 Text-to-SQL 的安全审核、只读执行、结果脱敏、缓存、限流与审计历史。

## 本地开发

项目要求 JDK 17，并使用仓库中的 Maven Wrapper：

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

本地数据库配置放在 `src/main/resources/application-local.yml`。请先将 `application-local.yml.example` 复制为该文件并填入本机凭据；实际文件已被 Git 忽略，不能提交 JWT 密钥、数据库密码或 DeepSeek Key。

本地接口文档：`http://localhost:8080/api/doc.html`

## Docker Compose 部署（Linux）

本项目提供一套独立的容器化演示环境，包含后端应用、MySQL 8 和 Redis 7。它不会使用或修改你 Windows 本机的 MySQL，也不会占用 Linux 上已有的 Redis `6379` 端口：MySQL 和 Redis 仅在 Docker 内部网络中开放，宿主机只暴露后端 `8080` 端口。

### 1. 前置条件

Linux 服务器需要已启动 Docker Engine，并具备 Docker Compose 插件：

```bash
docker info
docker compose version
```

### 2. 准备环境变量

在项目根目录执行：

```bash
cp .env.example .env
chmod 600 .env
```

编辑 `.env`，至少替换以下五项为真实私密值：

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_SYSTEM_PASSWORD`
- `MYSQL_READONLY_PASSWORD`
- `JWT_SECRET`（至少 32 个字符）
- `DEEPSEEK_API_KEY`

`.env` 已被 Git 忽略。不要将其上传到 GitHub，也不要把真实密钥发到聊天记录中。

### 3. 构建并启动

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

首次启动会拉取镜像、下载 Maven 依赖，并在 MySQL 数据卷为空时依次执行：

1. 创建 `ai_analyst`、`ai_business` 数据库及最小权限账号；
2. 创建系统表和业务表；
3. 导入演示业务数据。

应用、MySQL、Redis 均通过健康检查后，Linux 中可验证：

```bash
curl http://localhost:8080/api/actuator/health
```

从 Windows 访问 Linux 虚拟机时，将 `localhost` 换为虚拟机 IP，例如：

```text
http://192.168.6.7:8080/api/actuator/health
http://192.168.6.7:8080/api/doc.html
```

如果 Windows 无法访问 `8080`，再检查 Linux 防火墙是否放行该端口。

### 4. 常用运维命令

```bash
# 停止容器，但保留 MySQL / Redis 数据卷
docker compose down

# 查看所有容器状态
docker compose ps

# 持续查看后端日志
docker compose logs -f app
```

> `docker compose down -v` 会删除 Docker 中的 MySQL 和 Redis 数据卷，下一次启动会重新初始化演示数据。它不会删除 Windows 本机数据库，但属于清空容器数据的操作，执行前应确认。

## 核心接口

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册普通用户 |
| POST | `/api/auth/login` | 登录并获取 Bearer Token |
| GET | `/api/users/me` | 获取当前用户 |
| POST | `/api/queries/generate-sql` | 生成并审核 SQL |
| POST | `/api/queries/query` | 执行自然语言查询并返回 AI 总结 |
| GET | `/api/query-histories` | 分页查询当前用户的历史记录 |
| GET | `/api/actuator/health` | 服务健康检查，无需登录 |

`/api/actuator/metrics/**` 仅允许角色为 `ADMIN` 的用户访问。

## 数据库初始化（本地手动方式）

若不使用 Docker，使用 MySQL 管理员账号按顺序执行：

1. `sql/01_create_databases_and_users.sql`
2. `sql/02_system_schema.sql`
3. `sql/03_business_schema.sql`
4. `sql/04_business_seed_data.sql`

执行第一个脚本前，必须替换其中的示例密码。

## 前端开发

前端采用 Vue 3 + Vite + Element Plus，已经接入登录、注册、JWT 会话、智能查询、动态结果表格和当前用户查询历史。Token 过期时会提示重新登录，并在登录成功后返回原页面。

先保证后端已启动在 `http://127.0.0.1:8080/api`，再在另一个终端运行：

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

访问 `http://127.0.0.1:5173`。开发服务器会把 `/api` 请求代理给 Spring Boot，因此本地开发不需要额外配置后端 CORS。

前端只在浏览器本地保存 JWT 与用户展示信息，**不会保存用户密码**。生产环境应将前端静态文件部署到 Nginx 或由后端统一托管，并改用 HTTPS。

联调与验收记录：

- Day20 自动化联调结果与演示步骤：[`docs/day20-test-checklist.md`](docs/day20-test-checklist.md)
- Day21 浏览器联调与缺陷收口：[`docs/day21-test-report.md`](docs/day21-test-report.md)
