# 生活习惯助手 Agent 后端

基于 Spring Boot 3、Java 21 与 MySQL 的 REST 后端。支持多用户账号体系（RBAC）：账号密码登录、每日习惯记录、趋势分析、规则型建议、AI 多轮对话、周报/月报及 Excel/PDF 导出。

## 架构与边界

```text
common/  统一响应 Result、错误码与全局异常处理
pojo/    JPA Entity、请求 DTO、响应 VO
server/
  controller/ HTTP 接口、参数校验
  service/    业务编排、事务与报告/建议计算
  dao/        Spring Data JPA Repository
config/   JWT/会话、Spring Security、CORS 与演示数据配置
```

- 所有普通 JSON 接口返回 `{"code": 1, "message": "success", "data": ...}`；导出接口返回文件流。
- 密码使用 BCrypt 哈希；短期无状态 access token + 可轮换的落库 refresh token（一次性使用，重用即撤销整个会话）；JWT 解析后的当前用户决定每一条查询和写入的归属。
- 会话按设备独立（`sessions`/`refresh_tokens` 表），登出或撤销只影响目标会话；用户角色 USER/ADMIN，管理端点统一要求 ADMIN，系统始终保留至少一名有效管理员。
- 同一用户每天只有一条记录（`user_id + record_date` 唯一约束）；重复提交更新原记录。
- 每日健康目标可按用户自定义（`daily_goals` 表）；未设置时回落到全局阈值，统计达标、规则建议与 AI 提示词统一读取生效目标。
- 周报、月报按请求即时聚合，不保存冗余报告快照；同一用户同周期在 TTL 内复用内存缓存，习惯/目标/AI 解读变化后立即失效。

## 启动

前置条件：JDK 21、Maven、MySQL 8。

1. 创建空数据库 `life_habit_assistant`；Flyway 会在应用启动时自动执行迁移。
2. 设置环境变量：`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`。
3. 运行：

   ```bash
   mvn spring-boot:run
   ```

4. 打开 Swagger：<http://localhost:8080/swagger-ui.html>。

测试命令：

```bash
mvn test
```

### 演示数据

启用 `demo` Profile 会生成账号 `demo`、密码 `demo123456` 和最近 35 天记录；已有账号不会被覆盖。

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

## 接口

先调用注册或登录，随后在 Swagger 的 **Authorize** 中填写 `Bearer <token>`。

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 认证 | `POST /api/auth/register` | 注册，返回 access token + refresh token + 会话标识；可携带 `email`/`deviceName`/`deviceId` |
| 认证 | `POST /api/auth/login` | 登录，返回 access token + refresh token + 会话标识；禁用账号返回 403 |
| 每日记录 | `POST /api/habits` | 新建或更新当日记录（饮品改由明细接口维护） |
| 每日记录 | `GET /api/habits` | 分页和日期范围查询 |
| 每日记录 | `GET /api/habits/{date}` | 查询单日记录 |
| 每日记录 | `DELETE /api/habits/{date}` | 删除单日记录 |
| 每日目标 | `GET /api/goals` | 查询当前用户目标；未设置返回全局默认值 |
| 每日目标 | `PUT /api/goals` | 新建或更新当前用户目标 |
| 每日目标 | `DELETE /api/goals` | 重置为全局默认值 |
| 饮品明细 | `GET/POST /api/habits/{date}/drink-records` | 查询或新增当天饮品明细 |
| 饮品明细 | `PUT/DELETE /api/habits/{date}/drink-records/{id}` | 修改或删除一条饮品明细 |
| 趋势 | `GET /api/trends?days=7` | 睡眠、饮食、运动、饮水与连续天数 |
| 建议 | `POST /api/analyses?days=7` | 规则型风险和建议 |
| AI 建议 | `POST /api/ai/analyses?days=7` | 显式触发的 OpenAI 个性化解读；未启用或失败时自动返回规则建议 |
| AI 建议 | `POST /api/ai/reports/weekly?week=YYYY-MM-DD` | 自然周 AI 解读 |
| AI 建议 | `POST /api/ai/reports/monthly?month=YYYY-MM` | 自然月 AI 解读 |
| 报告 | `GET /api/reports/weekly?week=YYYY-MM-DD` | 自然周报告 |
| 报告 | `GET /api/reports/monthly?month=YYYY-MM` | 自然月报告 |
| 导出 | `GET /api/reports/weekly/export?week=...&format=xlsx|pdf` | 下载周报 |
| 导出 | `GET /api/reports/monthly/export?month=...&format=xlsx|pdf` | 下载月报 |
| 异步导出 | `POST /api/export-tasks?type=weekly|monthly|custom&format=xlsx|pdf` | 创建导出任务，返回 202 与任务 ID；custom 需 `start`/`end`，最长 5 年 |
| 异步导出 | `GET /api/export-tasks/{id}` | 轮询任务状态（PENDING/RUNNING/SUCCEEDED/FAILED） |
| 异步导出 | `GET /api/export-tasks/{id}/download` | 任务成功后下载文件；未完成或失败返回统一错误 |

`POST /api/habits` 请求示例：

```json
{
  "recordDate": "2026-07-21",
  "dietScore": 4,
  "note": "晚饭后散步"
}
```

`recordDate` 是当天归属日。先创建每日记录，再通过独立接口维护睡眠片段、运动明细和饮品明细。饮品使用 `GET/POST /api/habits/{date}/drink-records`、`PUT/DELETE /api/habits/{date}/drink-records/{id}`；`hydrationMl` 是按饮品类型计算的有效补水，不再把所有饮料简单等同于饮水。详见 [饮品模块设计](docs/drink-records.md)。

### 异步导出任务

大区间导出（如一年以上）不再同步阻塞请求：`POST /api/export-tasks` 创建任务后立即返回任务 ID，前端轮询 `GET /api/export-tasks/{id}` 直到 `SUCCEEDED`，再调用 `/download` 获取文件。任务按用户隔离，单用户最多 5 个待处理任务（`app.export.max-pending-per-user` 可调）；生成失败时状态为 `FAILED` 并附错误原因。周报/月报仍保留原有同步导出接口。

### 身份、会话与密码找回

新认证能力统一放在 `/api/v1/auth`：

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 会话 | `POST /api/v1/auth/refresh` | 一次性轮换 refresh token；旧令牌二次提交会撤销整个会话 |
| 会话 | `POST /api/v1/auth/logout` | 使当前会话的 refresh token 立即失效（幂等） |
| 会话 | `GET /api/v1/auth/sessions` | 当前用户全部有效会话（设备、IP、UA、登录/最后活跃时间） |
| 会话 | `DELETE /api/v1/auth/sessions/{id}` | 撤销指定设备会话；他人会话返回 404 |
| 密码找回 | `POST /api/v1/auth/password-reset/request` | 按注册邮箱发送一次性短时效重置令牌；账号不存在返回相同提示 |
| 密码找回 | `POST /api/v1/auth/password-reset/confirm` | 校验令牌并重置密码，成功后撤销该用户全部会话 |

access token 保持短时效无状态，撤销会话只立即使 refresh token 失效；`app.security.refresh-token-ttl-minutes`（默认 30 天）与 `app.security.password-reset-ttl-minutes`（默认 30 分钟）可调。未配置 `spring.mail.host` 时（dev），重置令牌写入日志便于本地验收；生产必须配置 SMTP。

### 管理后台 API（仅后端）

管理端点统一要求 ADMIN 角色（未认证 401、普通用户 403），前端界面单独交付：

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 用户 | `GET /api/v1/admin/users` | 分页列表，支持 `search`（用户名/邮箱模糊） |
| 用户 | `GET /api/v1/admin/users/{id}` | 用户概览（记录数、导出任务数、配额用量） |
| 用户 | `PATCH /api/v1/admin/users/{id}` | 调整角色/邮箱；降级或禁用最后一名有效 ADMIN 返回 400 |
| 用户 | `POST /api/v1/admin/users/{id}/disable`、`/enable` | 禁用后全部会话立即失效且禁止登录 |
| 配额 | `GET /api/v1/admin/quotas` | 用户 AI 配额列表（日/月用量与额度） |
| 配额 | `PATCH /api/v1/admin/quotas/{userId}` | 调整日/月额度（传 null 恢复全局默认） |
| 导出任务 | `GET /api/v1/admin/export-tasks` | 任意用户任务列表（按状态/用户过滤） |
| 导出任务 | `POST /api/v1/admin/export-tasks/{id}/cancel` | 取消任意用户的 PENDING/RUNNING 任务 |
| 统计 | `GET /api/v1/admin/stats` | 用户/管理员/导出任务状态/今日 AI 调用概览 |

### 自定义每日目标

`PUT /api/goals` 请求示例：

```json
{
  "minimumSleepMinutes": 480,
  "maximumSleepMinutes": 600,
  "minimumHydrationMl": 2000,
  "minimumExerciseMinutes": 45,
  "minimumDietScore": 4
}
```

目标字段取值：睡眠 180–720 / 360–960 分钟且最小值不得超过最大值、有效补水 500–5000 ml、运动 0–600 分钟、饮食 1–5 分。保存后，每日达标判断、达标率、规则建议（睡眠/饮食/补水/运动风险）和 AI 提示词全部按该用户目标计算；`DELETE /api/goals` 删除后回落全局阈值（`app.health.*`）。

### OpenAI 个性化建议

AI 建议默认关闭，且只在用户显式调用 `POST /api/ai/...` 时触发；打开报告或下载导出**不会**调用模型，只会读取该周期最近一次已保存的解读。规则统计与风险判定始终由本地规则引擎负责，模型只基于脱敏聚合指标生成自然语言解读，不会收到用户名、账号 ID、备注或原始记录。每次显式请求都会保存历史（AI 或规则降级），周报/月报导出会附加对应周期最近一次已保存内容。

启用方式：在 `.env` 中设置 `AI_ADVICE_ENABLED=true`，并填写 `OPENAI_API_KEY` 与 `OPENAI_MODEL`（模型 ID 以 OpenAI 官方文档为准）；配额默认每天 3 次、每月 30 次，可用 `AI_ADVICE_DAILY_LIMIT`、`AI_ADVICE_MONTHLY_LIMIT` 调整。详见 [AI 建议模块设计](docs/ai-advice.md)。

### AI 多轮对话

对话接口统一在 `/api/v1/ai/conversations`（需登录）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/ai/conversations` | 创建会话，可选 `title` |
| `GET` | `/api/v1/ai/conversations` | 分页列表，按最近活动时间倒序 |
| `GET` | `/api/v1/ai/conversations/{id}/messages` | 消息列表（按时间正序） |
| `POST` | `/api/v1/ai/conversations/{id}/messages` | 发送消息，保存用户消息并返回 AI 或规则回复 |
| `DELETE` | `/api/v1/ai/conversations/{id}` | 删除会话及其全部消息 |

发送消息时，系统携带该会话最近 N 轮（默认 10）历史与最近 7 天脱敏聚合指标、规则结论一起调用模型；模型回复或本地规则降级都会落库（`source=AI` / `source=RULE_FALLBACK`）。对话与报告解读共享 `ai_quota_usage` 日/月配额：失败尝试计数、规则降级不计数，管理员用户级额度覆盖同样生效。默认关闭，启用需在 `.env` 设置 `AI_CONVERSATION_ENABLED=true`（仍依赖 `OPENAI_API_KEY`/`OPENAI_MODEL`），可用 `AI_CONVERSATION_CONTEXT_DAYS`、`AI_CONVERSATION_MAX_HISTORY_ROUNDS`、`AI_CONVERSATION_MAX_MESSAGE_LENGTH` 调整。

## 演示顺序

注册/登录 → 录入记录 → 查询趋势 → 生成建议 → 查看周报或月报 → 下载 XLSX/PDF。

## 测试覆盖

- 认证：重复用户名/邮箱、BCrypt 哈希、错误密码、禁用账号、refresh 轮换与重用撤销、多端会话、密码找回。
- 权限：管理端点 401/403/放行、角色调整、最后一名管理员保护、配额调整。
- 记录：跨午夜睡眠、同日更新且归属当前用户。
- 分析：均值、总运动、连续记录、阈值达标。
- 报告：自然周、闰年月边界、Excel 工作表和 PDF 可打开性。
- 报表缓存：TTL 命中/过期、按用户失效、容量上限，以及修改记录后周报立即反映新值。
- 异步导出：任务创建、区间校验、待处理上限、用户隔离、状态流转（含失败原因）与 xlsx/pdf 下载。
- 每日目标：默认回落全局阈值、新建/更新同一行、重置、参数校验、用户隔离，以及自定义目标改变趋势达标与规则建议。
- AI 建议：解析容错、禁用/无数据/供应商失败/日额度/月额度的降级、按用户计费与隔离、报告导出只读取已保存建议。
- AI 对话：会话/消息用户隔离、多轮上下文限制、模型失败与配额耗尽降级、删除级联、共享配额、上下文脱敏。
- 正确性：并发重复提交的唯一约束重试、AI 配额表行级原子扣减与额度上限、异常兜底日志。

## Git 协作

每个模块遵循：`Issue → codex/<issue>-<module> → 测试 → Conventional Commit → Push → PR → 合并 → 关闭 Issue`。

仅使用仓库 `fzdzzj/life-habit-assistant`。

## 本地 MySQL 配置

项目通过 Flyway 迁移初始化 `users` 与 `habit_records` 两张表。

1. 创建本地配置文件：`Copy-Item .env.example .env`
2. 在 `.env` 中填写本机 MySQL 连接信息和 JWT 密钥。
3. 创建空数据库 `life_habit_assistant`，再启动应用让 Flyway 自动迁移。

`.env` 已被 Git 忽略，不能提交；`.env.example` 只保留字段模板，不包含真实密码或密钥。

### 运行环境

- 默认启用 `dev` Profile，并读取项目根目录的本地 `.env`。
- `prod` Profile 关闭 OpenAPI 与 Swagger UI；部署时必须注入 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`。
- 生产启动示例：`SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run`。

### 分页响应

`GET /api/habits` 的 `data` 固定包含 `content`、`page`、`size`、`totalElements`、`totalPages`，不暴露 Spring Data 的内部 `Page` JSON 结构。

## 数据库迁移

数据库结构由 Flyway 管理，迁移文件位于 `src/main/resources/db/migration/`（当前 V1–V11）。新环境只需先创建空数据库，应用启动时会自动执行 `V1__create_initial_schema.sql` 并记录到 `flyway_schema_history`；不再手工执行 SQL 文件。

对于已经用旧版 `schema.sql` 建过表的数据库：仅在第一次启动前设置 `FLYWAY_BASELINE_ON_MIGRATE=true`，让 Flyway 建立基线而不重复执行 V1；启动成功后应删除该变量或改回 `false`。后续表结构调整只能新增 `V2__...sql`、`V3__...sql`，不能修改已经发布的迁移文件。
