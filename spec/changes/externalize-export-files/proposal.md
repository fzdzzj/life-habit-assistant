# 提案：导出文件外置 —— LONGBLOB 迁移到本地磁盘/S3 兼容对象存储

## Why

**背景**：
- 异步导出任务成功后将文件字节直接写入 `export_tasks.file_content`（LONGBLOB），数据库随导出量只增不减。
- 下载时把整份文件从数据库读入内存再返回；大区间导出文件越大，数据库 IO 与请求内存压力越明显。
- 清理任务只删除数据库行，一旦决定外置，文件生命周期需要与存储位置统一管理。

**当前状态**：
- 生成链路：`ExportTaskWorker` 产出 `byte[]` → `markSucceeded(file_content)` 写入 LONGBLOB。
- 下载链路：`ExportTaskService.file` 读 `task.getFileContent()` → 控制器整体返回。
- 清理链路：`cleanupExpired` 直接批量删除超过保留期的任务行。

**期望状态**：
- 新增存储抽象：`local`（本地磁盘，默认）与 `s3`（S3 兼容对象存储，MinIO 客户端）两种实现，配置切换，不改业务代码。
- 新生成文件只写存储，数据库仅保留 `file_path` 引用；`file_content` 不再写入。
- 存量 LONGBLOB 行在应用启动时幂等迁移到存储并清空，迁移可配置开关、失败不影响启动。
- 下载流式输出，不把整份文件读入内存；清理时先删存储文件再删行，文件删除失败保留行等待下次重试。

## What Changes

1. **存储抽象与配置**（新增）
   - `ExportFileStorage` 接口：`store` / `load` / `delete`。
   - `LocalExportFileStorage`：按 `app.export.storage.local.directory` 落盘，校验路径穿越。
   - `S3ExportFileStorage`：MinIO 客户端连接 S3 兼容服务（AWS S3、MinIO、COS 等），bucket 由配置指定。
   - `app.export.storage.type=local|s3`（默认 local）；S3 缺少 endpoint/access-key/secret-key/bucket 时启动快速失败。

2. **数据库迁移 V12**（修改）
   - `export_tasks` 新增 `file_path VARCHAR(500) NULL`。
   - `markSucceeded` 改为写 `file_path` 并清空 `file_content`；实体不再映射 `fileContent`。
   - `file_content` 列本期保留（供存量迁移读取），生产确认迁移完成后可单独下线，不放入本迁移以免旧库丢数据。

3. **存量数据迁移**（新增）
   - `ExportFileMigrator`：启动时按批次读取 `file_path IS NULL AND file_content IS NOT NULL` 的行，写入存储、回填 `file_path`、清空 `file_content`。
   - 幂等：回填采用条件更新；多实例并发时后到者删除自己写入的文件。
   - `app.export.backfill-enabled`（默认 true）控制是否自动执行；单条失败记录日志并继续，不阻塞启动。

4. **下载流式化**（修改）
   - `ExportTaskService.file` 改为按 `file_path` 打开存储输入流，返回 `Resource` 流式输出。
   - 存储引用缺失或文件不存在时返回 404 统一错误；未完成/失败/取消任务语义保持不变。

5. **清理同步删除存储文件**（修改）
   - `cleanupExpired` 对每个超期 SUCCEEDED 任务先删存储文件（幂等），再批量删行。
   - 文件删除失败时保留该任务行并记录错误，等待下次清理重试。

**取舍**：
- 外置优先解决数据库膨胀与下载内存压力；本地磁盘为默认零配置路径，S3 通过 MinIO 客户端兼容主流对象存储，不绑定云厂商 SDK。
- `file_content` 列不在本期迁移中直接 DROP：旧库升级时应用迁移器尚未运行，直接删列会丢数据；保留列并在迁移后置空，文档标注后续可下线。
- 下载用 `Resource` 流式而不是 SSE/大文件分片；导出文件为报表文档，量级远小于分片需求，本期不引入 Range 支持。
- 不引入 Redis/消息队列，保持单实例约束；迁移与清理均为幂等，多实例部署安全。

## Impact

### 受影响的规范
- `spec/changes/update-backend-evolution/specs/async-export-lifecycle/spec-delta.md` - 保留期清理与下载行为被本提案细化。
- 本仓库无 `spec/specs/` 基线，以 README 与既有 spec-delta 为隐式基线。

### 受影响的代码
- `config/ExportProperties`、`config/ExportConfig`（或新增存储配置）- 存储类型与连接配置、条件装配。
- `server/service/ExportFileStorage`（新增接口）、`LocalExportFileStorage`、`S3ExportFileStorage`、`ExportFileMigrator`。
- `server/service/ExportTaskWorker` - 生成后写存储再提交状态；取消竞争时回删文件。
- `server/service/ExportTaskService` - 下载流式、清理先删文件。
- `server/dao/ExportTaskRepository` - `markSucceeded` 参数改为 `filePath`，新增存量迁移查询与条件更新。
- `pojo/ExportTask` - 新增 `filePath`，移除 `fileContent` 映射。
- `src/main/resources/db/migration/V12__add_export_file_path.sql` - 新增迁移。
- `pom.xml` - 新增 `io.minio:minio` 依赖。

### 用户影响
- 下载、任务列表等 API 路径与响应结构不变。
- 数据库不再保存导出文件字节，长期运行体积稳定。
- 部署方需决定存储类型：默认本地磁盘无需额外配置；对象存储需提供 endpoint/密钥/bucket。

### API 变更
- 无端点路径、请求或响应结构变更；下载响应仍为附件流，实现由“读库”改为“读存储”。

### 需要迁移
- [ ] 数据库迁移（V12：新增 `file_path`）
- [ ] 存量数据迁移（启动迁移器，自动执行；`EXPORT_STORAGE_BACKFILL_ENABLED=false` 可关）
- [ ] 文档更新（README、optimization-plan、.env.example、application.yml）

## 时间线评估

小（约 1 周兼职工作量），单 PR 交付：存储抽象 → 迁移与改造 → 存量迁移器 → 测试与文档。

## 风险

- **迁移中断**：写入存储成功但回填失败时，文件留在存储成为孤儿；条件更新保证行状态不脏，孤儿由下次迁移覆盖或人工清理，风险可接受。
- **清理误删**：只清理 SUCCEEDED 且超过保留期的任务；先删文件后删行，文件删除失败则保留行，避免“行没了文件还在”的不可追踪状态。
- **对象存储不可用**：S3 配置缺失或连接失败时 worker 任务 FAILED 并带原因；local 为默认值，开发环境零配置。
- **LONGBLOB 列残留**：旧库升级后列仍在但数据逐步清空；文档标注生产确认后可执行下线迁移，不阻塞本次交付。
