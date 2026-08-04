# 扩展性预留：领域边界与接入约定

> 本文档对应 openspec 提案 `update-backend-evolution` 的 `extensibility-groundwork` 能力。
> 目标：未来接入“好友/动态/社区/排行榜”“跑步/骑行定位轨迹”“用机时长”以及微信/QQ 第三方登录时，
> 不需要重构既有聚合根、已发布迁移或认证体系。

## 本期边界

- **本期不实现**好友、动态、社区、排行榜、定位轨迹、用机时长、第三方登录。
- **本期不建空表**、不写未使用代码，只把约束固化进规范、文档与架构回归测试。
- 既有聚合根（`users`、`habit_records` 及睡眠/运动/饮品子表）与已发布迁移（V1–V11）保持不可变。

## 领域边界

```text
auth/identity    users、sessions、refresh_tokens、password_reset_tokens
records          habit_records + sleep/exercise/drink 明细子表（聚合根）
goals            daily_goals
statistics       统一统计口径 HealthStatisticsService（趋势/报告/建议/AI 共用）
export           export_tasks
ai               ai_advice_history、ai_quota_usage、ai_conversations(+messages)
social/app       （预留）未来以独立模块接入
```

## 接入约定

### 1. 社交与 App 端明细：独立表、独立模块

好友关系、好友动态、社区内容、排行榜、跑步/骑行轨迹、用机时长均以**新领域模块 + 新 Flyway 迁移**
接入，通过 `user_id` 外键归属用户：

- 新增模块放 `server/social`、`server/apptracking` 等独立包，不塞进既有服务。
- 新增表放在新迁移 `V12__...sql` 及之后，**不得修改 V1–V11**。
- 轨迹、用机时长等明细以独立表存储并与 `habit_records` 解耦：既有的每日记录写入/查询路径不变。
- 好友、动态等如果复用记录内容，通过轻量快照或独立表生成，不改既有记录表的写入路径。

### 2. 排行榜：复用统一统计口径

排行榜必须与趋势、报告、规则建议共用 `HealthStatisticsService` 的聚合入口：

- 不允许在排行榜模块内自行重算“平均睡眠/饮食/运动/补水”等指标；
- 不允许引入与报告不一致的阈值或口径；
- 新增指标时在统一统计边界内扩展，趋势/报告/排行榜自动获得一致结果。

`ArchitectureConstraintTest` 会扫描生产代码：`HealthStatistics` 聚合对象只允许由
`HealthStatisticsService` 构造，现有消费方（趋势/报告/AI 上下文）必须依赖该服务。

### 3. 端类型仅作会话元数据

App 端接入时复用现有 `/api/v1` 版本化策略与 access + refresh 会话体系，不建独立认证：

- `sessions` 只携带设备名、设备 ID、IP、UA 等元数据；
- 鉴权逻辑不得按端类型（web/app）分支；端类型最多用于会话展示与多端管理；
- 同一个账号在 Web 与 App 各自获得独立会话，互不影响，统一遵守 refresh 轮换与登出语义。

`ArchitectureConstraintTest` 会扫描生产代码：禁止出现 `deviceType`/`clientType` 等鉴权分支字段，
以及独立的 `AppSession`/`MobileSession` 会话体系。

### 4. 迁移与“放行清单”

架构约束测试维护两份放行清单：

- 预留表名（好友/帖子/社区/排行榜/轨迹/用机时长/第三方身份绑定）本期必须为空；
- 迁移版本清单 V1–V11，新增迁移时必须同步更新。

功能立项时的标准动作：

1. 新建独立 openspec 提案，定义独立表结构与模块边界；
2. 新增 `V(n+1)__...sql`，不改已发布迁移；
3. 在 `ArchitectureConstraintTest` 放行清单中移除对应预留项；
4. 按 `Issue → codex/<issue>-<module> → 测试 → PR → 合并` 流程交付。

## 验证

- `mvn test`：`ArchitectureConstraintTest` 覆盖表映射、预留表、迁移版本、统计口径、端类型与会话体系约束。
- `FlywayMySqlMigrationIntegrationTest`（CI/Testcontainers）验证 V1–V11 在真实 MySQL 上可完整执行两次。
- `MySqlContextBootIntegrationTest`（CI/Testcontainers）验证真实 MySQL 上 Flyway 迁移后，Spring 上下文以默认 `ddl-auto=validate` 完整启动，防止“迁移能跑、应用起不来”的类型不一致回归。
