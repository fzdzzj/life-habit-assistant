# 规范差异：对话流式输出

本文件定义在现有“AI 多轮对话”基础上新增 SSE 流式输出的规范变更（现有行为以 `update-backend-evolution` 的 ai-conversation spec-delta 为准）。

## ADDED Requirements

### Requirement: 对话流式输出
WHEN 已认证用户对属于自己的会话发起流式消息请求,
系统 SHALL 以 SSE 增量返回模型输出，并在输出结束后持久化完整助手消息；IF 模型不可用、调用失败或配额不足, 系统 SHALL 返回降级事件并按既有配额语义处理。

#### Scenario: 正常流式回复
GIVEN 会话属于当前用户且 AI 服务可用
WHEN 用户发起流式消息请求
THEN 响应为 text/event-stream
AND 先返回开始事件
AND 先返回增量文本事件
AND 输出结束后返回完成事件（含完整消息与实时配额）
AND 完整助手消息被持久化且标记为计费

#### Scenario: 配额不足降级
GIVEN 用户日或月配额已耗尽
WHEN 用户发起流式消息请求
THEN 返回降级事件
AND 不调用模型
AND 不扣减配额
AND 持久化规则降级消息

#### Scenario: 模型调用失败降级
GIVEN 模型在流式过程中发生供应商异常或超时
WHEN 用户发起流式消息请求
THEN 返回降级事件
AND 该次调用计入配额
AND 持久化规则降级消息

#### Scenario: 客户端断开
GIVEN 客户端在输出未完成时断开连接
WHEN 服务端检测到断开
THEN 取消模型订阅
AND 已收到非空文本时将该文本持久化为助手消息
AND 未收到任何文本时持久化规则降级消息

#### Scenario: 越权访问
GIVEN 会话不存在或属于另一用户
WHEN 用户发起流式消息请求
THEN 返回 404
AND 不保存任何消息

---

### Requirement: 流式任务互斥与取消
WHEN 用户对会话发起新的生成请求（流式或同步发送）或主动取消,
系统 SHALL 保证同一会话同一时间仅存在一个进行中生成任务，并取消该会话已存在的旧流式任务；取消时已收到非空文本则保存为助手消息，否则保存规则降级。

#### Scenario: 同会话新请求取消旧任务
GIVEN 该会话已有进行中的流式生成
WHEN 用户对该会话发起新的流式请求或同步发送
THEN 旧流式任务被取消
AND 旧任务已收到的非空文本被保存为助手消息，未收到文本则保存规则降级
AND 新请求正常开始

#### Scenario: 主动取消成功
GIVEN 该会话存在进行中的流式生成且已收到部分文本
WHEN 用户请求取消该会话的生成任务
THEN 模型订阅被取消
AND 已收到文本被保存为助手消息
AND 返回取消成功
AND 后续事件以 cancelled 结束

#### Scenario: 无任务可取消
GIVEN 该会话当前没有进行中的生成任务
WHEN 用户请求取消
THEN 返回冲突错误
AND 不产生任何消息写入

#### Scenario: 取消他人会话任务
GIVEN 进行中的生成属于另一用户的会话
WHEN 当前用户请求取消
THEN 返回 404
AND 该会话生成任务不受影响

---

### Requirement: 流式请求配额语义
WHEN 用户发起流式消息请求,
系统 SHALL 在开始调用模型前原子扣减日/月配额；模型调用失败计为已使用，规则降级不计数。

#### Scenario: 先扣后调
GIVEN 用户剩余配额为 1
WHEN 用户发起流式消息请求
THEN 系统在调用模型前完成日/月配额扣减
AND 完成事件返回实时剩余配额

#### Scenario: 失败尝试计数
GIVEN 模型调用发生异常
WHEN 请求结束
THEN 该次尝试计入配额使用
AND 返回降级事件

#### Scenario: 降级不计数
GIVEN 配额已耗尽
WHEN 用户发起流式消息请求
THEN 配额使用量保持不变
AND 返回降级事件

---

## 备注

- 流式端点鉴权沿用现有 `Authorization: Bearer` 头，不引入 query token 或独立会话体系。
- 报告解读保持同步请求，不在本 capability 范围内提供流式。
- 非流式发送消息接口 `POST /api/v1/ai/conversations/{id}/messages` 行为保持不变。
- 流式状态机与事件协议参考 `D:\code\rag\back\RAG` 的 `RagStreamSessionManager` 设计（start/delta/complete/error/cancelled），仅借鉴设计不迁移实现。
