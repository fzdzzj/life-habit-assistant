# 规范差异：AI 多轮对话

本文件定义在现有“单次显式 AI 解读”基础上新增多轮对话能力的规范变更（当前行为以 README 与 `docs/ai-advice.md` 为准）。

## ADDED Requirements

### Requirement: 对话会话管理
WHEN 已认证用户创建 AI 对话会话,
系统 SHALL 创建仅属于该用户的会话并返回会话标识；WHEN 用户请求会话列表, 系统 SHALL 按最近活动时间倒序返回该用户自己的会话。

#### Scenario: 创建会话
GIVEN 用户已登录
WHEN 用户创建新的 AI 对话会话
THEN 系统返回会话标识
AND 会话归属当前用户

#### Scenario: 会话列表倒序且隔离
GIVEN 当前用户有多个会话且另一用户也有会话
WHEN 当前用户请求会话列表
THEN 系统按最近活动时间倒序返回当前用户自己的会话
AND 另一用户的会话不出现于结果中

#### Scenario: 列表分页
GIVEN 当前用户拥有超过一页的会话
WHEN 用户请求第 1 页
THEN 系统返回分页结果
AND 分页边界遵循统一的分页上限校验

---

### Requirement: 对话消息发送与多轮上下文
WHEN 用户向会话发送消息,
系统 SHALL 保存用户消息，携带该会话最近 N 轮消息作为上下文调用模型，保存 AI 回复并返回；IF 模型调用失败或配额不足, 系统 SHALL 保存并返回降级回复且标记来源。

#### Scenario: 正常多轮回复
GIVEN 会话已存在且用户已登录
WHEN 用户发送消息
THEN 系统保存用户消息
AND 携带最近 N 轮消息作为上下文调用模型
AND 保存并返回 AI 回复，来源标记为 AI

#### Scenario: 上下文窗口限制
GIVEN 会话消息数超过 N 轮
WHEN 用户发送消息
THEN 系统仅携带最近 N 轮消息
AND 更早的消息不进入上下文

#### Scenario: 模型失败降级
GIVEN 模型调用超时或供应商异常
WHEN 用户发送消息
THEN 系统保存并返回规则型降级回复
AND 响应来源标记为 RULE_FALLBACK

#### Scenario: 配额不足降级
GIVEN 用户日或月配额已耗尽
WHEN 用户发送消息
THEN 系统保存并返回规则型降级回复
AND 响应来源标记为 RULE_FALLBACK
AND 不扣减配额

#### Scenario: 向不存在或他人会话发送
GIVEN 会话不存在或不属于当前用户
WHEN 用户发送消息
THEN 系统返回 404
AND 不保存任何消息

---

### Requirement: 对话上下文脱敏
WHEN 系统构造对话上下文,
系统 SHALL 仅包含脱敏聚合指标与规则结论，不包含用户名、账号 ID、备注或原始记录。

#### Scenario: 上下文无敏感字段
GIVEN 用户发送消息且系统准备调用模型
WHEN 系统构造上下文
THEN 上下文只包含脱敏统计指标与规则结论
AND 不包含用户名、账号 ID、备注或原始记录

#### Scenario: 多轮历史同样脱敏
GIVEN 会话存在多轮历史消息
WHEN 系统携带历史消息进入上下文
THEN 历史消息同样不包含原始记录与个人标识

---

### Requirement: 会话删除
WHEN 用户删除自己的对话会话,
系统 SHALL 删除该会话及其全部消息。

#### Scenario: 删除成功
GIVEN 会话属于当前用户且包含多条消息
WHEN 用户删除该会话
THEN 会话被删除
AND 其全部消息被删除
AND 后续查询该会话返回 404

#### Scenario: 删除他人会话
GIVEN 会话属于另一用户
WHEN 当前用户尝试删除
THEN 系统返回 404
AND 会话保持不变

---

## MODIFIED Requirements

### Requirement: AI 配额适用范围
**Previous**：AI 配额仅对显式报告解读请求扣减，对话功能不存在。

WHEN 任何 AI 模型调用（报告解读或对话回复）被触发,
系统 SHALL 统一从同一用户的日/月配额原子扣减；模型调用失败计为已使用，规则降级不计数。

#### Scenario: 解读与对话共享配额
GIVEN 用户当日剩余配额为 1
WHEN 用户先请求报告解读再发送对话消息
THEN 两个请求共享同一日配额
AND 第二次模型调用因配额耗尽返回降级

#### Scenario: 失败尝试计数
GIVEN 模型调用发生供应商异常
WHEN 请求结束
THEN 该次尝试计入配额使用
AND 响应降级为规则回复

#### Scenario: 降级不计数
GIVEN 配额已耗尽
WHEN 用户发送对话消息
THEN 系统返回规则降级回复
AND 配额使用量保持不变

---

## 备注

- 现有 `POST /api/ai/analyses`、`POST /api/ai/reports/weekly|monthly` 保持不变，只与对话共享配额与脱敏边界。
- 对话消息与报告解读历史分属不同领域模型，不互相复用业务状态。
- 流式输出、同周期结果缓存列为后续方向，不在本规范内。
