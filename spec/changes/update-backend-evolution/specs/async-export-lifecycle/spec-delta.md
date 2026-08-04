# 规范差异：异步导出任务生命周期

本文件包含对现有导出任务能力的规范变更（当前行为以 README 与 Issue #66 实现为准，仓库暂无 `spec/specs/` 基线）。

## ADDED Requirements

### Requirement: 导出任务列表查询
WHEN 已认证用户请求导出任务列表,
系统 SHALL 返回仅属于该用户的任务分页列表，按创建时间倒序排列，并支持按状态过滤。

#### Scenario: 正常列表
GIVEN 当前用户拥有 3 个不同状态的导出任务
WHEN 用户请求第 1 页且每页 10 条
THEN 系统返回 3 条任务
AND 结果按创建时间倒序排列
AND 结果仅包含当前用户的任务

#### Scenario: 按状态过滤
GIVEN 当前用户同时拥有 FAILED 与 SUCCEEDED 任务
WHEN 用户请求 status=FAILED 的列表
THEN 系统只返回 FAILED 状态的任务

#### Scenario: 分页越界
GIVEN 请求的 offset 超过系统配置的分页上限
WHEN 用户请求该页
THEN 系统返回 400 统一错误
AND 不执行查询

#### Scenario: 用户隔离
GIVEN 另一用户拥有导出任务
WHEN 当前用户请求任意分页的任务列表
THEN 另一用户的任务不出现于任何结果中

---

### Requirement: 导出任务取消
WHEN 用户对处于 PENDING 或 RUNNING 状态的导出任务发起取消,
系统 SHALL 将任务原子流转为 CANCELLED，且该任务不得再生成或提供下载文件。

#### Scenario: 取消待处理任务
GIVEN 任务处于 PENDING 状态
WHEN 用户发起取消
THEN 任务状态变为 CANCELLED
AND 后续轮询返回 CANCELLED
AND 下载请求返回 409 统一错误

#### Scenario: 取消运行中任务
GIVEN 任务处于 RUNNING 状态
WHEN 用户发起取消且 worker 尚未完成
THEN 任务状态变为 CANCELLED
AND worker 完成前不再写入文件内容

#### Scenario: 已结束任务不可取消
GIVEN 任务处于 SUCCEEDED、FAILED 或 CANCELLED 状态
WHEN 用户发起取消
THEN 系统返回 409 统一错误
AND 任务状态保持不变

#### Scenario: 取消他人任务
GIVEN 任务不属于当前用户
WHEN 用户发起取消
THEN 系统返回 404

#### Scenario: 取消与执行竞争
GIVEN worker 正在认领同一任务
WHEN 取消请求与认领同时发生
THEN 二者只有一个生效
AND 任务最终要么 RUNNING 完成后落文件，要么保持 CANCELLED 且不落文件

---

### Requirement: 导出任务失败重试
WHEN 用户对 FAILED 状态的导出任务发起重试,
系统 SHALL 将任务重新置为 PENDING 并清空错误信息；IF 该用户待处理任务数已达上限, 系统 SHALL 拒绝重试。

#### Scenario: 重试成功
GIVEN 任务处于 FAILED 状态且该用户待处理任务未达上限
WHEN 用户发起重试
THEN 任务状态变为 PENDING
AND 错误信息被清空
AND 任务可再次被 worker 执行并最终 SUCCEEDED

#### Scenario: 非失败任务不可重试
GIVEN 任务处于 SUCCEEDED、CANCELLED、RUNNING 或 PENDING 状态
WHEN 用户发起重试
THEN 系统返回 409 统一错误
AND 任务状态保持不变

#### Scenario: 待处理任务达上限
GIVEN 该用户已有 5 个 PENDING 状态任务（配置上限）
WHEN 用户对 FAILED 任务发起重试
THEN 系统返回 429
AND 任务保持 FAILED

#### Scenario: 重试他人任务
GIVEN 任务不属于当前用户
WHEN 用户发起重试
THEN 系统返回 404

---

### Requirement: 导出任务保留期清理
WHEN 系统执行定时清理且存在 SUCCEEDED 且超过配置保留期的导出任务,
系统 SHALL 删除这些任务及其存储的文件，并记录清理日志。

#### Scenario: 超期任务被清理
GIVEN 任务处于 SUCCEEDED 且创建时间早于保留期窗口
WHEN 定时清理执行
THEN 任务被删除
AND 任务列表不再返回该任务
AND 下载请求返回 404

#### Scenario: 未超期任务保留
GIVEN 任务处于 SUCCEEDED 且创建时间在保留期内
WHEN 定时清理执行
THEN 任务与文件保留
AND 下载仍可成功

#### Scenario: 非成功状态不受影响
GIVEN 任务处于 PENDING、RUNNING、FAILED 或 CANCELLED 状态且创建时间超过保留期
WHEN 定时清理执行
THEN 这些任务不被删除

#### Scenario: 非法保留期配置
GIVEN 保留期配置为 0 或负数
WHEN 应用启动
THEN 系统快速失败并给出明确配置错误

---

## MODIFIED Requirements

### Requirement: 导出任务状态机
**Previous**：任务状态仅在 PENDING、RUNNING、SUCCEEDED、FAILED 之间流转，创建后只能等待完成或失败。

WHEN 导出任务被创建、执行、取消或重试,
系统 SHALL 按 PENDING → RUNNING → SUCCEEDED | FAILED | CANCELLED 流转；其中 CANCELLED 为终态，FAILED 可经重试回到 PENDING。

#### Scenario: 正常完成
GIVEN 任务处于 PENDING 状态
WHEN worker 认领并成功生成文件
THEN 状态流转为 RUNNING 再变为 SUCCEEDED
AND 下载返回文件

#### Scenario: 失败后重试完成
GIVEN 任务处于 FAILED 状态
WHEN 用户重试且 worker 再次执行成功
THEN 状态按 FAILED → PENDING → RUNNING → SUCCEEDED 流转

#### Scenario: 取消为终态
GIVEN 任务处于 CANCELLED 状态
WHEN 用户尝试重试或下载
THEN 重试返回 409
AND 下载返回 409
AND 状态不再变化

#### Scenario: 下载仅限成功任务
GIVEN 任务处于 PENDING、RUNNING、FAILED 或 CANCELLED 状态
WHEN 用户请求下载
THEN 系统返回 409 统一错误
AND 不返回文件内容

---

## 备注

- 现有 `GET /api/export-tasks/{id}` 与 `GET /api/export-tasks/{id}/download` 行为保持不变。
- 新增端点统一使用 `/api/v1` 前缀（见身份权限规范的版本化需求）。
- 取消、重试、认领全部使用原子状态流转，禁止“先查后改”的竞态实现。
