# 规范差异：导出文件外置

本文件细化了异步导出任务的文件存储与生命周期行为（基线为 README 与 `update-backend-evolution/specs/async-export-lifecycle/spec-delta.md`）。

## ADDED Requirements

### Requirement: 导出文件存储外置
WHEN 导出任务成功生成文件,
系统 SHALL 将文件字节写入配置的导出文件存储（本地磁盘或 S3 兼容对象存储），在 `export_tasks.file_path` 中记录存储引用，且 SHALL NOT 将文件字节写入 `file_content`。

#### Scenario: 本地磁盘存储
GIVEN `app.export.storage.type=local` 且目录可写
WHEN worker 成功生成文件
THEN 文件写入本地目录
AND `file_path` 保存相对根目录的存储键
AND `file_content` 保持 NULL

#### Scenario: S3 兼容对象存储
GIVEN `app.export.storage.type=s3` 且 endpoint/密钥/bucket 配置有效
WHEN worker 成功生成文件
THEN 文件以存储键写入 bucket
AND `file_path` 保存该对象键

#### Scenario: 非法存储配置
GIVEN `app.export.storage.type=s3` 但 endpoint、access-key、secret-key 或 bucket 缺失
WHEN 应用启动
THEN 应用快速失败并给出明确配置错误

#### Scenario: 路径穿越防护
GIVEN 本地磁盘存储收到含 `..` 或绝对路径的存储键
WHEN 执行 store/load/delete
THEN 操作被拒绝
AND 文件不会落在根目录之外

---

### Requirement: 存量 LONGBLOB 数据迁移
WHEN 应用启动且存在 `file_content` 非空而 `file_path` 为空的历史导出任务,
系统 SHALL 分批将该任务文件写入导出文件存储、回填 `file_path` 并清空 `file_content`。

#### Scenario: 迁移成功
GIVEN 存在 3 条历史 LONGBLOB 任务且存储可用
WHEN 应用启动执行迁移
THEN 3 条任务均写入存储
AND 每条 `file_path` 被回填
AND 每条 `file_content` 被清空

#### Scenario: 单条迁移失败
GIVEN 其中一条任务写入存储失败
WHEN 应用启动执行迁移
THEN 该条任务保持原状并记录错误日志
AND 其余任务继续迁移
AND 应用正常启动

#### Scenario: 迁移开关关闭
GIVEN `app.export.backfill-enabled=false`
WHEN 应用启动
THEN 不执行存量迁移
AND 历史数据保持不变

#### Scenario: 多实例并发回填
GIVEN 两个实例同时迁移同一条历史任务
WHEN 两者都写入存储并执行条件回填
THEN 只有一个回填成功
AND 后到者删除自己写入的文件
AND 任务 `file_path` 有效且 `file_content` 为空

---

### Requirement: 导出文件流式下载
WHEN 已认证用户请求 SUCCEEDED 导出任务的下载,
系统 SHALL 按 `file_path` 从导出文件存储流式返回文件内容，且 SHALL NOT 将完整文件读入内存。

#### Scenario: 正常下载
GIVEN 任务 SUCCEEDED 且存储中存在对应文件
WHEN 用户请求下载
THEN 响应为附件流
AND 文件头与 Content-Type 正确

#### Scenario: 存储文件缺失
GIVEN 任务 SUCCEEDED 但 `file_path` 为空或存储中文件不存在
WHEN 用户请求下载
THEN 系统返回 404 统一错误

#### Scenario: 未完成任务下载
GIVEN 任务处于 PENDING、RUNNING、FAILED 或 CANCELLED 状态
WHEN 用户请求下载
THEN 系统返回 409 统一错误
AND 不访问存储

---

### Requirement: 清理同步删除存储文件
WHEN 定时清理删除超过保留期的 SUCCEEDED 导出任务,
系统 SHALL 先删除该任务在导出文件存储中的文件，再删除数据库行；IF 文件删除失败, 系统 SHALL 保留该任务行并记录错误日志。

#### Scenario: 正常清理
GIVEN 任务 SUCCEEDED、超过保留期且存储文件存在
WHEN 定时清理执行
THEN 存储文件被删除
AND 任务行被删除

#### Scenario: 文件删除失败
GIVEN 任务 SUCCEEDED、超过保留期但存储删除抛错
WHEN 定时清理执行
THEN 任务行保留
AND 错误被记录
AND 下次清理可再次尝试

#### Scenario: 无存储引用的旧行
GIVEN 任务 SUCCEEDED、超过保留期且 `file_path` 为空
WHEN 定时清理执行
THEN 直接删除任务行
AND 不访问存储

---

## MODIFIED Requirements

### Requirement: 导出任务保留期清理
**Previous**：系统定时删除超过保留期的 SUCCEEDED 任务及其数据库中的文件内容。

WHEN 系统执行定时清理且存在 SUCCEEDED 且超过配置保留期的导出任务,
系统 SHALL 先删除其在导出文件存储中的文件（IF 存在），再删除数据库行，并记录清理日志；IF 存储文件删除失败, 系统 SHALL 保留对应任务行以便下次重试。

#### Scenario: 超期任务被清理
GIVEN 任务处于 SUCCEEDED 且创建时间早于保留期窗口
WHEN 定时清理执行
THEN 存储文件被删除
AND 任务行被删除
AND 任务列表不再返回该任务
AND 下载请求返回 404

#### Scenario: 未超期任务保留
GIVEN 任务处于 SUCCEEDED 且创建时间在保留期内
WHEN 定时清理执行
THEN 任务行与存储文件均保留
AND 下载仍可成功

---

## 备注

- 现有下载、任务列表 API 路径与响应结构不变。
- `file_content` 列本期保留供存量迁移读取；迁移完成并确认后可在后续独立迁移中下线该列，不随本提案直接 DROP 以防旧库丢数据。
- 新增存储配置项：`app.export.storage.type`、`app.export.storage.local.directory`、`app.export.storage.s3.*`、`app.export.backfill-enabled`。
