# 提案：生产化部署 —— Dockerfile/Compose、定时备份、日志监控

## Why

**背景**：
- 后端功能已收敛（认证/RBAC、AI 对话与流式、报表缓存、异步导出外置），但部署仍依赖本机 JDK/Maven/MySQL 手工启动，没有可重复的生产形态。
- 数据库没有任何备份机制，数据丢失不可恢复。
- prod 日志是普通文本，无法被 Loki/ELK 等工具解析；没有运行指标端点，故障只能靠肉眼翻日志。

**当前状态**：
- 仅 `mvn spring-boot:run` + 本机 MySQL；prod Profile 只关闭 Swagger。
- actuator 只暴露 `/actuator/health`；无 Prometheus 指标。
- 导出文件默认本地磁盘（`./data/exports`），无持久化卷方案。

**期望状态**：
- 一条命令 `docker compose up -d --build` 拉起 MySQL + 应用 + 定时备份，三个服务均有健康检查与重启策略。
- 数据库每日自动全量备份并 gzip、按天保留；备份与恢复命令可复现。
- prod 输出 ECS 结构化日志并滚动；可选 `monitoring` profile 一键起 Prometheus + Grafana + Loki + Promtail，指标与日志有统一查看入口。
- 部署步骤、卷、故障排查集中在一个文档，配置全部由 `.env` 驱动。

## What Changes

1. **容器镜像**（新增）
   - 多阶段 `Dockerfile`：`maven:3.9-eclipse-temurin-21` 构建（`dependency:go-offline` 缓存依赖，`package -DskipTests`）→ `eclipse-temurin:21-jre-jammy` 运行。
   - 运行层：非 root 用户、Asia/Shanghai 时区、`-XX:MaxRAMPercentage=75.0` 容器感知、`JAVA_OPTS` 可注入、`HEALTHCHECK` 调 `/actuator/health`。
   - `.dockerignore` 排除 `.git`/`target`/`data`/`.env` 等，缩小构建上下文。

2. **docker compose**（新增）
   - `mysql`：utf8mb4、健康检查（mysqladmin ping）、`mysql_data` 卷、宿主端口默认 3307 避免与本机 MySQL 冲突。
   - `app`：`prod` Profile、`depends_on` MySQL healthy、导出文件卷 `export_data:/app/data`、日志卷 `app_logs:/app/logs`、curl 健康检查、`restart: unless-stopped`。
   - 全局日志 driver `json-file`（20MB × 5 份），防止 `docker logs` 无限增长。
   - 可选 `monitoring` profile：prometheus/loki/promtail/grafana，默认不启动。

3. **定时备份**（新增）
   - `deploy/backup/`：alpine + mysql-client，entrypoint 把 `BACKUP_CRON`（默认 `0 3 * * *`）写入 crontab 并前台运行 crond。
   - `backup.sh`：`mysqldump --single-transaction --routines --triggers --hex-blob` → gzip → `gzip -t` 校验 → 按 `BACKUP_KEEP_DAYS` 清理；失败不产生残文件。
   - 手动触发：`docker compose exec backup backup.sh`；恢复命令写入文档。

4. **日志与指标**（修改）
   - `application-prod.yml`：ECS 结构化日志（`logging.structured.format.console/file=ecs`）、logback 滚动（50MB/7 份/500MB）、actuator 暴露 `info,prometheus`。
   - `pom.xml` 新增 `micrometer-registry-prometheus`（runtime）；`SecurityConfig` 放行 `/actuator/prometheus`（与 health 同级，内网单机）。

5. **监控栈**（新增，可选）
   - prometheus：抓 `app:8080/actuator/prometheus`，保留 168h。
   - loki：单机文件存储，保留 168h。
   - promtail：读 `app_logs` 卷的 ECS JSON，解析 `@timestamp`/`log.level`/`log.logger`/`message` 后推送 Loki。
   - grafana：自动 provisioning Prometheus/Loki 数据源，管理员账号密码可由 `.env` 配置。

6. **文档与 CI**（修改）
   - 新增 `docs/deployment.md`（架构、启动、卷、备份/恢复、日志、监控、S3、升级、故障表）；README 增加部署入口并修正迁移版本号（V1–V12）；`.env.example` 补部署变量；optimization-plan 追加本轮记录。
   - CI 新增 `docker-build` job，保证 Dockerfile 可构建。

**取舍**：
- 单机单实例，不引入 Redis/消息队列/K8s；备份用 mysqldump + crond 而非商业备份工具，成本最低且可审计。
- 监控栈默认不启动（profile 按需开启），避免个人主机常驻四个容器；日志已结构化，未来迁移 Loki 之外的采集器零成本。
- 镜像本地构建、不打公共注册表；需要多机部署时再补 registry/CI 推送。
- `/actuator/prometheus` 不加密，与 health 同策略；文档明确对外暴露需反向代理加认证。

## Impact

### 受影响的规范
- 无既有 spec-delta 与本提案冲突；README 与 optimization-plan 为隐式基线。

### 受影响的代码
- 新增 `Dockerfile`、`.dockerignore`、`docker-compose.yml`、`deploy/backup/*`、`deploy/monitoring/*`。
- 修改 `src/main/resources/application-prod.yml`、`config/SecurityConfig.java`、`pom.xml`。
- 修改文档：`README.md`、`docs/optimization-plan.md`、`.env.example`；新增 `docs/deployment.md`。
- 修改 `.github/workflows/ci.yml`（新增镜像构建 job）。

### 用户影响
- API 与数据库结构不变；新增部署制品与配置。
- 部署方需要提供 `MYSQL_ROOT_PASSWORD` 等 compose 变量（`.env.example` 已含全部模板）。
- prod 日志变为 ECS JSON：`docker compose logs` 可读性略降，但可被监控栈解析；本地开发仍用 dev Profile 普通日志。

### API 变更
- 无端点变更；新增 `/actuator/prometheus` 指标端点（prod 暴露）。

### 需要迁移
- [ ] 数据迁移：无新 Flyway 迁移（部署形态变化不影响 schema）。
- [ ] 配置迁移：`.env.example` 新增 `MYSQL_ROOT_PASSWORD`、端口/备份/日志/监控变量。
- [ ] 文档更新：deployment.md、README、optimization-plan、.env.example。

## 时间线评估

小（约 1 周兼职工作量），单 PR 交付：镜像与 compose → 备份容器 → 日志/指标 → 监控栈 → 文档与 CI → 本机 compose 冒烟。

## 风险

- **MySQL 初始化失败**：`MYSQL_ROOT_PASSWORD` 缺失或为空时容器首次初始化失败；compose 健康检查与文档故障表覆盖。
- **备份权限**：mysqldump 需要 `SELECT/LOCK TABLES/SHOW VIEW/EVENT/TRIGGER`；失败会写日志且不影响应用。
- **监控 tag 不可用**：镜像 tag 构建时验证；升级 tag 属日常运维。
- **端口冲突**：app 8080、MySQL 3307、监控 9090/3100/3000 均可由 `.env` 调整。
- **日志卷权限**：named volume 首次挂载继承镜像内 `/app/logs` 的 app 用户所有权，promtail 以只读方式读取，不需要写权限。
