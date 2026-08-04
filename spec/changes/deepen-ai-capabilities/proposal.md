# 提案：AI 能力深化 —— 对话流式输出 + 报告解读结构化输出 + 同周期结果缓存

## Why

**背景**：
- 对话发送消息是同步请求：模型生成期间请求线程阻塞，前端只能等待完整回复，长回答体验差且容易碰到 30 秒超时。
- 报告解读依赖提示词“尽量输出 JSON”，服务端解析宽松：字段缺失时填空、围栏剥离后解析，无法保证结构完整，也没有服务端 schema 校验。
- 同一周期（analysis 最近 N 天、周报、月报）每次请求都重新调用模型、重新扣配额、写入新历史；报告本身已有 TTL 缓存，AI 解读没有。

**当前状态**：
- `POST /api/v1/ai/conversations/{id}/messages` 同步返回完整回复，无增量输出。
- `AiAdviceContentParser` 对模型文本做宽松 JSON 解析，缺字段补空字符串。
- 每次解读都走 `quotaService.occupy` + 模型调用 + 新 history 记录，无同周期复用。

**期望状态**：
- 对话支持 SSE 流式输出，边生成边返回；流式与同步共用配额、脱敏与降级语义。
- 报告解读要求模型输出既定 JSON schema，服务端结构化转换与字段校验，失败一律降级，不返回“残缺”的半结构化文本。
- 同一周期重复请求命中内存缓存：不调模型、不扣配额、不新增历史；数据变化后缓存失效。

## What Changes

1. **对话流式输出（SSE）**（新增）
   - 新增 `POST /api/v1/ai/conversations/{id}/messages/stream`，响应 `text/event-stream`。
   - 事件协议：`start`（开始）、`delta`（增量文本）、`complete`（完整消息 + 实时配额）、`fallback`（降级回复）、`error`（协议错误）、`cancelled`（被取消）。
   - 每会话同一时间仅允许一个进行中生成任务：新流式请求或同步发送到达时取消该会话旧流式任务；新增取消端点 `POST /api/v1/ai/conversations/{id}/messages/cancel`，支持前端主动停止生成。
   - 配额先扣后调、失败计费、降级不计数，与现有规则一致；独立流式超时配置（默认 300 秒，可配置）。
   - 客户端断开、超时或主动取消时取消模型订阅：已收到非空文本则保存为助手消息，否则保存规则降级。
   - 鉴权沿用现有 `Authorization: Bearer`，不引入 query token。

2. **报告解读结构化输出**（修改生成路径）
   - 模型输出必须符合既定 JSON schema；服务端通过结构化输出转换器严格解析（`entity`/`responseFormat` + 提示词 v2）。
   - 必填字段缺失、解析失败或模型不可用 → 规则降级并保持既有配额语义（调用失败计费）。
   - 现有 `AiAdviceResponse` 响应结构不变；`AiAdviceContentParser` 保留，继续用于读取历史快照（报告/导出）。

3. **同周期结果缓存**（新增）
   - 新增内存 `AiAdviceCache`（TTL 与上限可配置，默认 10 分钟 / 128 条，与报告缓存一致）。
   - 只缓存 `source=AI` 的成功结果；命中时返回 `cached=true`，不调模型、不扣配额、不新增 history，配额快照实时返回。
   - 旧 `/api/ai/analyses`、`/api/ai/reports/weekly|monthly` 新增可选 `refresh` 参数（默认 false）强制重新生成并更新缓存；响应新增 `cached` 字段（向后兼容新增）。
   - 抽取统一用户缓存失效点：习惯、饮料、运动、睡眠、目标写路径同时清除报告缓存与 AI 解读缓存。

**取舍**：
- 流式只做对话；报告解读保持同步。结构化解读输出的是整段 JSON，流式收益低、实现成本高，本轮不做。
- 缓存只缓存 AI 成功结果，不缓存规则降级，避免 AI 恢复后仍被旧降级挡住。
- 流式会话协调、取消端点与事件协议参考 `D:\code\rag\back\RAG` 的 RAG 流式设计（`RagStreamSessionManager`），只借鉴状态机与协议，不迁移其 LangChain4j 实现。
- 无数据库迁移、无新表、不改已发布迁移。

## Impact

### 受影响的规范
- `spec/changes/update-backend-evolution/specs/ai-conversation/spec-delta.md` - 对话能力扩展（流式）；本仓库无 `spec/specs/` 基线，以现有 spec-delta 与 README 为隐式基线。

### 受影响的代码
- `server/service/OpenAiChatClient` 与 `SpringAiOpenAiChatClient` - 新增流式与结构化输出方法。
- `server/service/AiConversationService` 与 `server/controller/AiConversationController` - 流式生命周期与 SSE 端点。
- `server/service/AiAdviceService` - 结构化输出 + 同周期缓存。
- `config/AiAdviceConfig` - 结构化输出选项与流式 ChatClient/超时；新增 `AiAdviceCache` 与配置属性。
- `pojo/AiAdviceDtos`、`pojo/AiConversationDtos` - 响应 `cached` 字段、流式事件 DTO。
- 写路径服务（Habit/Drink/Exercise/Sleep/Goal） - 接入统一缓存失效点。

### 用户影响
- 对话可边生成边显示，长回答不易超时；断线/失败时仍能获得可解释的降级回复。
- 重复查看同一周期解读不再重复扣配额；响应多一个 `cached` 标记。
- 旧 `/api/ai/*` 端点路径与主要字段不变，新增可选 `refresh` 参数与 `cached` 字段。

### API 变更
- 新增 `POST /api/v1/ai/conversations/{id}/messages/stream`（SSE）。
- 新增 `POST /api/v1/ai/conversations/{id}/messages/cancel`（取消该会话进行中的生成任务；无进行中任务返回 409/400）。
- `POST /api/ai/analyses`、`POST /api/ai/reports/weekly`、`POST /api/ai/reports/monthly`：新增可选 `refresh` 查询参数；响应新增 `cached` 字段。
- 现有同步对话与报告解读端点路径、请求、主要响应结构保持不变。

### 需要迁移
- [ ] 数据库迁移（无，本期不建表）
- [ ] API 版本提升（新增端点直接走 `/api/v1`）
- [x] 用户沟通（`cached` 字段与 `refresh` 参数说明）
- [ ] 文档更新（README、optimization-plan、OpenAPI）

## 时间线评估

中（约 1–2 周兼职工作量），按 capability 拆 2 个 PR：
- PR1：对话流式输出（适配层 + 服务 + SSE 端点 + 测试）。
- PR2：报告解读结构化输出 + 同周期结果缓存（共享 `AiAdviceService` 改动，合并交付更稳）。

## 风险

- **SSE 长连接与客户端断开**：取消订阅、超时回调、断开时保存部分文本；用单元测试与集成测试覆盖断开与超时路径。
- **同会话并发生成**：新请求/同步发送取消旧流式任务，每会话一个进行中任务，避免消息顺序与持久化互相覆盖；取消端点与状态机测试覆盖。
- **结构化输出对模型/网关兼容性**：不支持 response_format 的兼容网关可能失败，解析失败一律降级，不改变旧端点语义；提示词版本升级为 v2。
- **缓存与数据一致性**：所有写路径统一失效 + TTL 兜底；`refresh` 保证可强制重新生成。
- **命中缓存不写新 history**：报告/导出快照继续引用首次生成的历史记录，语义在文档中说明。
- **配额语义回归**：流式与缓存都保持“先扣后调、失败计费、降级不计数”，用既有配额测试模式回归。
