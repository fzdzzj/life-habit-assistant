# 提案：后端演进第一批 —— 任务生命周期 + 身份权限管理后台 + AI 对话与扩展预留

## Why

**背景**：
- 项目已完成功能闭环（记录 → 趋势 → 报告 → 导出 → AI）与可靠性收口（限流、配额硬约束、缓存、异步导出等，Issue #44–#66），122 项测试全绿，CI 稳定。
- 目标同时服务真实产品化上线与求职/学习作品展示：上线需要可运维、可管理、可找回密码；作品需要展示 RBAC、会话轮换、多端会话等有深度的后端设计。

**当前状态**：
- 异步导出任务生成后文件永久保存在数据库 LONGBLOB：无分页列表、无取消、无失败重试、无保留期清理，长期运行数据库只增不减。
- 认证只有单一短期 JWT：无 refresh token、无登出失效、无会话概念，无法支撑 Web/移动端同时登录。
- 系统是“单一普通用户”模型：无角色/权限、无管理后台 API，运营侧无法管理用户、AI 配额与导出任务。
- 无密码找回流程，账号一旦忘记密码即不可恢复。

**期望状态**：
- 导出任务有完整生命周期：列表可查、未完成任务可取消、失败任务可重试、成功文件按保留期自动清理。
- 认证支持多端会话：短期 access token + 可轮换的 refresh token，登出与设备撤销即时生效。
- RBAC 落地：USER/ADMIN 角色与统一鉴权，管理后台 API 覆盖用户、AI 配额与导出任务管理。
- 密码找回走一次性短时效令牌，且重置后强制所有会话失效。

## What Changes

本提案交付四个能力（规范见 `specs/`）：

1. **异步导出任务生命周期补全**（可直接开发）
   - 分页任务列表（按状态过滤、用户隔离、按创建时间倒序）。
   - 任务取消：PENDING/RUNNING → CANCELLED，原子流转，与 worker 认领互斥。
   - 失败重试：FAILED → PENDING，清空错误信息后重新排队。
   - 保留期清理：定时删除超过保留期的成功任务及其文件，保留期可配置。
   - 状态机扩展为 PENDING → RUNNING → SUCCEEDED / FAILED / CANCELLED。

2. **身份、权限、管理后台与多端**（可直接开发）
   - RBAC：用户默认 USER 角色，管理员可授予/撤销 ADMIN；管理端点统一要求 ADMIN。
   - 认证演进：登录签发 access + refresh；refresh 一次性轮换；登出失效；支持多设备独立会话与指定会话撤销。
   - 密码找回：一次性短时效重置令牌，经配置的邮件通道发送；重置后使该用户所有会话失效。
   - 管理后台 API（仅后端，前端界面另行提案）：用户管理（分页/概览/启用禁用/角色调整）、AI 配额查看与调整、导出任务查看与取消、系统概览统计。
   - API 版本化策略：新增端点统一使用 `/api/v1` 前缀，现有 `/api` 端点保持不变。

3. **AI 多轮对话**（可直接开发）
   - 会话与消息：创建/列表/删除对话会话；发送消息时携带该会话最近 N 轮上下文，保存用户消息与 AI 回复。
   - 配额与脱敏：与报告解读共享日/月配额并原子扣减；上下文只含脱敏聚合指标与规则结论，不传用户名、备注或原始记录。
   - 降级：模型调用失败或配额不足时保存并返回规则回复，响应带 `source=RULE_FALLBACK`。

4. **可扩展性预留**（设计约束，本期不实现功能）
   - 社交（好友、动态、社区、排行榜）与 App 端数据（跑步/骑行定位轨迹、用机时长）以独立领域模块与独立表接入，不改既有 `users`、`habit_records` 及子表结构。
   - 微信/QQ 等第三方登录以独立身份映射表接入，不改 `users` 主表与令牌/会话模型；本期不实现登录功能、不建空表。
   - 排行榜复用统一统计服务口径，不引入与趋势/报告不一致的重复计算。
   - 本期不建空表、不写无用代码，只把约束固化进规范，并留架构验证手段。

**后续方向（roadmap，不在本提案规范范围内）**：
- 生产化运维：Dockerfile/compose、定时备份、日志与监控接入。
- AI 能力深化：结构化输出、同周期结果缓存、流式输出（对话本身已在本次交付）。
- 性能演进预案：统计预聚合、keyset 分页、POI SXSSF 流式导出。
- 导出文件外置：LONGBLOB 迁移到本地磁盘或对象存储。
- 管理后台前端界面。
- 社交与 App 端功能本身（预留已完成，具体功能另行提案）。
- 微信/QQ 第三方登录（预留已完成；需开放平台资质与回调配置，可纳入身份权限后续能力）。

## Impact

### 受影响的规范
- 本仓库当前没有 `spec/specs/` 基线；本次以 README、`docs/optimization-plan.md`、`docs/project-review.md` 记录的现有行为为隐式基线。
- `spec/changes/update-backend-evolution/specs/async-export-lifecycle/spec-delta.md` - 新增任务生命周期需求，修改现有状态机。
- `spec/changes/update-backend-evolution/specs/identity-access-admin/spec-delta.md` - 新增 RBAC、会话、密码找回、管理后台与版本化需求。
- `spec/changes/update-backend-evolution/specs/ai-conversation/spec-delta.md` - 新增 AI 多轮对话需求，修改配额适用范围。
- `spec/changes/update-backend-evolution/specs/extensibility-groundwork/spec-delta.md` - 新增社交与 App 端数据的扩展性设计约束。

### 受影响的代码
- `config/`：Security 配置、鉴权注解、会话/权限相关配置属性。
- `server/service/`：`ExportTaskService`（列表/取消/重试/清理）、`AuthService`（令牌与会话）、新增管理服务。
- `server/dao/`：新增会话、重置令牌、角色、对话会话/消息相关 Repository；`ExportTaskRepository` 增加原子流转查询。
- `pojo/`：`User` 增加角色；新增 RefreshToken/Session、PasswordResetToken、对话会话/消息实体、管理响应 VO。
- `common/`：错误码扩展（如 CANCELLED 冲突、令牌过期、权限不足等）。
- `src/main/resources/db/migration/`：新增 V9（角色/会话/重置令牌/任务状态）、V10（用户邮箱/启用状态/AI 配额覆盖）与 V11（对话会话/消息）迁移。
- `config/`（扩展预留）：领域包边界与设计约束测试，不新增无功能代码。

### 用户影响
- 普通用户：登录后自动获得 refresh 续期；多设备可同时登录；忘记密码可自助找回；导出现有接口不变，但任务状态新增 CANCELLED；新增 AI 多轮对话能力，与报告解读共享配额。
- 管理员：获得管理 API（本提案不包含管理前端页面）。
- 社交、定位轨迹、用机时长：本期不提供功能，仅确保未来以独立模块接入时无需迁移既有数据。

### API 变更
- 现有端点无破坏性变更（路径、请求、响应结构保持不变）。
- 新增端点：
  - `GET /api/v1/export-tasks`（分页列表）
  - `POST /api/v1/export-tasks/{id}/cancel`
  - `POST /api/v1/export-tasks/{id}/retry`
  - `POST /api/v1/auth/refresh`、`POST /api/v1/auth/logout`
  - `GET /api/v1/auth/sessions`、`DELETE /api/v1/auth/sessions/{id}`
  - `POST /api/v1/auth/password-reset/request`、`POST /api/v1/auth/password-reset/confirm`
  - `GET /api/v1/admin/users`、`PATCH /api/v1/admin/users/{id}`、`POST /api/v1/admin/users/{id}/disable` 等
  - `GET /api/v1/admin/quotas`、`PATCH /api/v1/admin/quotas/{id}`
  - `GET /api/v1/admin/export-tasks`、`POST /api/v1/admin/export-tasks/{id}/cancel`
  - `GET /api/v1/admin/stats`
  - `POST /api/v1/ai/conversations`（创建）、`GET /api/v1/ai/conversations`（列表）
  - `GET /api/v1/ai/conversations/{id}/messages`、`POST /api/v1/ai/conversations/{id}/messages`（发送）
  - `DELETE /api/v1/ai/conversations/{id}`（删除会话及其消息）

### 需要迁移
- [x] 数据库迁移（V9：角色、会话/刷新令牌、重置令牌、任务状态扩展，已完成；V10：用户邮箱/启用状态/AI 配额覆盖，随身份权限 PR；第三方身份映射表留待微信/QQ 登录实施时新增；V11：对话会话/消息）
- [ ] API 版本提升（新增端点走 v1，现有端点冻结）
- [ ] 用户沟通（多端会话行为、登出语义说明）
- [x] 文档更新（README、optimization-plan、OpenAPI）

## 边界变更

- 从“不做 RBAC / 管理后台 / 多端”改为**支持**；本提案交付后端能力，管理前端界面单独提案。
- 社交、社区、排行榜、定位轨迹、用机时长：本期**不实现**，仅通过扩展性设计约束预留空间；避免在无功能需求时建空表。
- 微信/QQ 等第三方登录：本期**不实现**，仅固化“独立身份映射表、不改 `users` 与会话模型”的扩展约束，不建空表；具体登录功能另行提案。
- 继续保持单实例、不引入 Redis/消息队列：会话与清理任务落在数据库 + 内存，足够支撑当前规模。
- 密码找回依赖邮件通道：生产环境必须配置 SMTP；开发环境未配置时令牌写入日志，仅限 dev profile。

## 时间线评估

中大（约 6–8 周兼职工作量，按 capability 拆 PR，每阶段可独立交付）：
- 异步导出任务生命周期：1–2 周（含测试）。
- 身份权限与管理后台：3–4 周（含测试与迁移）。
- AI 多轮对话：1–2 周（含测试与迁移）。
- 扩展性预留：随各能力落地，不单独占排期。

## 风险

- **refresh token 使认证从“纯无状态”变为“有状态”**：access token 保持短时效无状态，refresh token 落库并一次性轮换，兼顾安全与实现复杂度；多实例部署时需共享会话库，已在需求中标注边界。
- **任务取消与 worker 并发竞态**：取消与认领都走原子 `UPDATE ... WHERE status IN (...)`，只有一方成功；worker 完成前再次校验状态，避免已取消任务落文件。
- **清理任务误删在用文件**：只清理 SUCCEEDED 且超过保留期的任务，清理先写日志，保留期配置默认 7 天，避免立即误删。
- **RBAC 越权遗漏**：管理端点统一通过鉴权注解/基类约束 + 集成测试覆盖“USER 访问 403、未认证 401、ADMIN 放行”三类场景。
- **密码找回可用性**：请求接口统一返回成功提示，不泄露账号是否存在；令牌短时效、一次性；重置后撤销全部会话，降低令牌泄露影响。
- **AI 对话成本与滥用**：与报告解读共享日/月配额并原子扣减；上下文长度限制最近 N 轮；对话同样遵守脱敏边界，防止提示词注入携带原始数据。
- **预留空间过度设计**：只固化设计约束，不建空表、不写未使用代码；未来功能上线时按约束新增迁移与模块，避免本期承担无功能代码的维护成本。
- **范围膨胀**：本提案含四个能力，实施按 capability 拆 PR（任务生命周期 → 身份权限 → AI 对话），每个 PR 独立测试、独立合入，降低单次变更风险。
