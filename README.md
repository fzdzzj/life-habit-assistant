# 生活习惯助手 Agent 后端

基于 Spring Boot 3、Java 21 与 MySQL 的 REST 后端。项目聚焦单一普通用户：账号密码登录、每日习惯记录、趋势分析、规则型建议、周报/月报及 Excel/PDF 导出。

## 架构与边界

```text
common/  统一响应 Result、错误码与全局异常处理
pojo/    JPA Entity、请求 DTO、响应 VO
server/
  controller/ HTTP 接口、参数校验
  service/    业务编排、事务与报告/建议计算
  dao/        Spring Data JPA Repository
config/   JWT、Spring Security 与演示数据配置
```

- 所有普通 JSON 接口返回 `{"code": 1, "message": "success", "data": ...}`；导出接口返回文件流。
- 密码使用 BCrypt 哈希；JWT 解析后的当前用户决定每一条查询和写入的归属。
- 同一用户每天只有一条记录（`user_id + record_date` 唯一约束）；重复提交更新原记录。
- 周报、月报按请求即时聚合，不保存冗余报告快照。

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
| 认证 | `POST /api/auth/register` | 注册，返回 JWT |
| 认证 | `POST /api/auth/login` | 登录，返回 JWT |
| 每日记录 | `POST /api/habits` | 新建或更新当日记录 |
| 每日记录 | `GET /api/habits` | 分页和日期范围查询 |
| 每日记录 | `GET /api/habits/{date}` | 查询单日记录 |
| 每日记录 | `DELETE /api/habits/{date}` | 删除单日记录 |
| 趋势 | `GET /api/trends?days=7` | 睡眠、饮食、运动、饮水与连续天数 |
| 建议 | `POST /api/analyses?days=7` | 规则型风险和建议 |
| 报告 | `GET /api/reports/weekly?week=YYYY-MM-DD` | 自然周报告 |
| 报告 | `GET /api/reports/monthly?month=YYYY-MM` | 自然月报告 |
| 导出 | `GET /api/reports/weekly/export?week=...&format=xlsx|pdf` | 下载周报 |
| 导出 | `GET /api/reports/monthly/export?month=...&format=xlsx|pdf` | 下载月报 |

`POST /api/habits` 请求示例：

```json
{
  "recordDate": "2026-07-21",
  "dietScore": 4,
  "exerciseMinutes": 45,
  "waterMl": 1800,
  "note": "晚饭后散步"
}
```

`recordDate` 是当天归属日。先创建每日记录，再通过独立接口维护睡眠片段：`GET/POST /api/habits/{date}/sleep-sessions`、`PUT/DELETE /api/habits/{date}/sleep-sessions/{id}`。每段以 `sleepType: NIGHT | NAP` 与完整的 `sleepStartAt`、`wakeAt` 表示；未睡时不创建片段。日记录、趋势和报告自动汇总所有片段的睡眠分钟数。

## 演示顺序

注册/登录 → 录入记录 → 查询趋势 → 生成建议 → 查看周报或月报 → 下载 XLSX/PDF。

## 测试覆盖

- 认证：重复用户名、BCrypt 哈希、错误密码。
- 记录：跨午夜睡眠、同日更新且归属当前用户。
- 分析：均值、总运动、连续记录、阈值达标。
- 报告：自然周、闰年月边界、Excel 工作表和 PDF 可打开性。

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

数据库结构由 Flyway 管理，迁移文件位于 `src/main/resources/db/migration/`。新环境只需先创建空数据库，应用启动时会自动执行 `V1__create_initial_schema.sql` 并记录到 `flyway_schema_history`；不再手工执行 SQL 文件。

对于已经用旧版 `schema.sql` 建过表的数据库：仅在第一次启动前设置 `FLYWAY_BASELINE_ON_MIGRATE=true`，让 Flyway 建立基线而不重复执行 V1；启动成功后应删除该变量或改回 `false`。后续表结构调整只能新增 `V2__...sql`、`V3__...sql`，不能修改已经发布的迁移文件。
