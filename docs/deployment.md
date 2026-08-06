# 生产化部署指南

> 目标：在一台 Linux/Windows 主机上用 Docker Compose 一键跑起 MySQL + 应用 + 定时备份，并具备可排查的结构化日志与可选的指标/日志监控栈。单机单实例，不引入 Redis、消息队列或 Kubernetes。

## 1. 架构总览

```text
┌─ docker compose ──────────────────────────────────────────────┐
│  mysql(3306) ◄── app(8080) ──► /app/data(导出文件卷)          │
│      ▲            │                /app/logs(日志卷)          │
│      │            │                                           │
│  backup(定时 mysqldump → /backups 卷)                         │
│                                                               │
│  ── 可选 monitoring profile ──                                 │
│  prometheus ◄──/actuator/prometheus                          │
│  promtail  ──读取日志卷──► loki                               │
│  grafana(看指标 + 日志)                                        │
└───────────────────────────────────────────────────────────────┘
```

## 2. 前置条件

- Docker Engine 24+（含 Docker Compose v2 插件，`docker compose version` 可验证）。
- 端口 8080（app）、3307（容器 MySQL 映射，可改）未被占用。
- 一个 `.env` 文件：`Copy-Item .env.example .env`，至少填写 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`JWT_SECRET`（`DB_*` 留给本地 Maven 直跑，compose 会改用容器专用账号 `MYSQL_USER`/`MYSQL_PASSWORD`）。

> `.env` 已被 Git 忽略，请勿提交真实密码。`DB_HOST`/`DB_PORT` 在本部署中会被 compose 自动覆盖为 `mysql`/`3306`，本地直跑 Maven 时才使用 localhost 值。

## 3. 启动与停止

```bash
# 构建镜像并启动（首次会拉取 mysql:8.0 与构建依赖，耗时较长）
docker compose up -d --build

# 查看状态：三个服务都应为 healthy/running
docker compose ps

# 应用日志（ECS JSON）
docker compose logs -f app

# 停止（保留数据卷）
docker compose down

# 停止并删除数据卷（会清空数据库、导出文件、日志、备份，谨慎）
docker compose down -v
```

访问：应用 API `http://localhost:8080`，健康检查 `http://localhost:8080/actuator/health`；prod 已关闭 Swagger/OpenAPI。

### 国内网络：镜像加速

若主机无法直连 Docker Hub（拉镜像或构建超时），不需要改 Docker 配置，在 `.env` 指定镜像源前缀即可：

```env
BUILD_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21
RUNTIME_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre-jammy
MYSQL_IMAGE=docker.m.daocloud.io/library/mysql:8.0
BACKUP_IMAGE=docker.m.daocloud.io/library/alpine:3.20
PROMETHEUS_IMAGE=docker.m.daocloud.io/prom/prometheus:v2.55.1
LOKI_IMAGE=docker.m.daocloud.io/grafana/loki:3.4.2
PROMTAIL_IMAGE=docker.m.daocloud.io/grafana/promtail:3.4.2
GRAFANA_IMAGE=docker.m.daocloud.io/grafana/grafana:11.6.0
```

正常网络环境保持这些变量为空即可。`docker manifest inspect` 直连 registry 失败不一定是镜像不存在，可用 `docker pull <加速器前缀>/<镜像>:<tag>` 验证。

## 4. 数据卷

| 卷 | 挂载点 | 内容 |
| --- | --- | --- |
| `mysql_data` | `/var/lib/mysql` | 数据库文件 |
| `export_data` | `/app/data` | 导出文件（`data/exports`） |
| `app_logs` | `/app/logs` | 应用滚动日志 |
| `backup_data` | `/backups` | 数据库备份压缩包 |

备份卷独立于应用，恢复数据库不依赖应用容器。

## 5. 数据库定时备份

backup 容器基于 MySQL 官方客户端镜像（完整支持 MySQL 8 的 `caching_sha2_password` 认证），用 cronie 每天按 `BACKUP_CRON`（默认每天 03:00）执行全量 `mysqldump`：

- `--single-transaction`：InnoDB 一致性快照，备份期间业务可继续写。
- `--routines --triggers`：保留存储过程与触发器。
- `--hex-blob`：二进制列（遗留 `file_content`）安全导出；`--no-tablespaces` 免除 PROCESS 权限（用户库备份不需要表空间元数据）。
- 输出 `gzip` 压缩并做完整性校验（`gzip -t`），按 `BACKUP_KEEP_DAYS`（默认 7）删除过期文件。

手动触发一次备份：

```bash
docker compose exec backup backup.sh
docker compose exec backup ls -lh /backups
```

恢复（将备份导入运行中的 MySQL，会覆盖同名库）：

```bash
docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < <(docker compose exec -T backup sh -c 'zcat /backups/life_habit_assistant_20260806_030000.sql.gz')
```

> 备份验证是运维责任：至少每月抽查一个压缩包，执行 `gzip -t` 并恢复到一个临时库验证表数量。

## 6. 日志

prod Profile 的日志策略：

- **结构化输出**：控制台与文件均为 ECS JSON（`logging.structured.format.console/file=ecs`），每行含 `@timestamp`、`log.level`、`log.logger`、`message`、`service.name`，可直接被 Loki/ELK 等解析。
- **文件滚动**：`logs/app.log` 单文件 50MB、保留 7 份、总量上限 500MB（`LOG_MAX_FILE_SIZE`/`LOG_MAX_HISTORY`/`LOG_TOTAL_SIZE_CAP` 可调）。
- **容器层轮转**：compose 全局 `json-file` driver，单文件 20MB、保留 5 份，防止 `docker logs` 无限增长。

查看方式：

```bash
docker compose logs -f app                 # 容器 stdout（ECS JSON）
docker compose exec app tail -f logs/app.log
```

## 7. 可选监控栈（Prometheus + Grafana + Loki）

默认不启动，避免个人主机空跑四个常驻容器。需要时：

```bash
docker compose --profile monitoring up -d
```

| 服务 | 地址 | 作用 |
| --- | --- | --- |
| Prometheus | `http://localhost:9090` | 抓取 `/actuator/prometheus`（JVM、HTTP、线程等指标），默认保留 7 天 |
| Loki | `http://localhost:3100` | 日志存储（默认保留 7 天） |
| Promtail | 内部 | 读取 `app_logs` 卷的 ECS 日志并推送 Loki |
| Grafana | `http://localhost:3000` | 默认 `admin/admin`（务必在 `.env` 改 `GRAFANA_ADMIN_PASSWORD`），已自动配置 Prometheus/Loki 数据源 |

验证：

```bash
curl http://localhost:9090/api/v1/targets          # app 目标应为 UP
curl http://localhost:3100/ready                   # Loki ready
# Grafana 登录后：Explore → 数据源 Loki → 查询 {job="life-habit-assistant"}
```

说明：`/actuator/prometheus` 未加认证（与 `/actuator/health` 一致），只适合单机内网；对外暴露时应在反向代理加 Basic Auth 或 IP 白名单。

## 8. 导出文件存储（可选 S3）

默认本地卷 `export_data`。若要使用对象存储：

```env
EXPORT_STORAGE_TYPE=s3
EXPORT_S3_ENDPOINT=http://minio:9000
EXPORT_S3_ACCESS_KEY=...
EXPORT_S3_SECRET_KEY=...
EXPORT_S3_BUCKET=life-habit-exports
```

本 compose 不含 MinIO 服务；可自行在 compose 中追加 MinIO 或复用已有 S3 兼容服务。切换后数据库只存 `file_path` 引用，历史本地文件不再迁移到 S3（如需迁移需另行方案）。

## 9. 升级应用

```bash
git pull
docker compose build app
docker compose up -d
```

Flyway 在应用启动时自动执行新迁移；数据库卷不删除即可原地升级。若迁移失败，app 会反复重启，查看 `docker compose logs app` 定位 SQL 问题。

## 10. 常见故障

| 症状 | 原因与处理 |
| --- | --- |
| app 一直 starting，healthcheck 不通过 | 看 `docker compose logs app`：多半是 DB 连接失败或 Flyway 迁移报错；确认 mysql healthy |
| MySQL 首次初始化失败 | `MYSQL_ROOT_PASSWORD` 为空：在 `.env` 补值后 `docker compose down -v`（首次初始化未完成时数据卷没有价值）再 `up -d` |
| 8080/3307 端口冲突 | `.env` 改 `APP_PORT`/`MYSQL_HOST_PORT` 后重启 |
| 备份文件为空或 mysqldump 报错 | `docker compose logs backup`；检查 `.env` 的 `DB_USERNAME`/`DB_PASSWORD` 与 MySQL 用户权限（至少 `SELECT, LOCK TABLES, SHOW VIEW, EVENT, TRIGGER`） |
| Grafana 登录失败 | 首次启动后 `grafana_data` 卷已按 `.env` 初始化；改密码后需 `docker compose down` 并删除 `grafana_data` 卷（或保持首次一致） |
| Prometheus 目标 DOWN | `curl http://localhost:8080/actuator/prometheus` 是否可访问；app 健康后自动恢复 |
| 本地 Maven 与 compose 混用 | 本地直跑用 `.env` 的 `DB_HOST=localhost`；compose 自动覆盖为服务名，互不影响；MySQL 端口默认错开（3307） |
