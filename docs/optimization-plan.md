# 后端优化方案与执行记录

> 用途：固定本轮优化范围与验收标准，防止实施过程中扩大范围或偏离原目标。后续每做一项优化，先在这里更新“本轮范围”，再按 Issue → 分支 → 测试 → PR 流程执行。

## 一、优化总览

本轮主题：**正确性收口**。只做四件事，不顺手改其他模块：

1. 500 错误可排查（兜底异常记录日志）。
2. 并发重复提交不撞唯一约束（原子重试，保持幂等更新语义）。
3. AI 配额成为硬约束（独立配额表 + 行级原子扣减，并发不超卖）。
4. 合并前自动验证（GitHub Actions CI）。

P0 安全项独立执行：注册/登录限流（Issue #46 / PR #47，见二）。

对应 Issue #44、分支 `codex/44-reliability-hardening`、PR #45。

## 二、本轮已完成（Issue #44 / PR #45）

### 注册/登录限流（Issue #46 / PR #47）

| 优化项 | 问题 | 方案 | 落点 | 验证 |
| --- | --- | --- | --- | --- |
| 注册/登录限流 | `/api/auth/**` 公开且无速率限制，可批量注册、暴力撞库 | 内存滑动窗口按 IP 限频（注册 5 次/分钟、登录 20 次/分钟）；同一 IP+用户名连续失败 5 次锁定 15 分钟，登录成功清零；新错误码 42900（HTTP 429）统一返回 | [AuthRateLimiter.java](../src/main/java/com/fzdzzj/lifehabitassistant/config/AuthRateLimiter.java)、[ClientIpResolver.java](../src/main/java/com/fzdzzj/lifehabitassistant/config/ClientIpResolver.java)、[AuthService.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/service/AuthService.java)、[ErrorCode.java](../src/main/java/com/fzdzzj/lifehabitassistant/common/ErrorCode.java) | `AuthRateLimiterTest` 8 项（窗口、锁定、解锁、隔离）；`AuthRateLimitHttpIntegrationTest` 3 项（注册 429、失败锁定、正常登录不受影响） |

### 正确性收口（Issue #44 / PR #45）

| 优化项 | 问题 | 方案 | 落点 | 验证 |
| --- | --- | --- | --- | --- |
| 500 日志 | `handleUnexpected` 只返回通用错误，生产排障无堆栈 | 兜底处理器加 `Logger.error` | [ApiExceptionHandler.java](../src/main/java/com/fzdzzj/lifehabitassistant/common/ApiExceptionHandler.java) | `ApiExceptionHandlerTest` 断言统一 500 结构；日志输出堆栈 |
| 并发重复提交 | `save` 先查后存，并发请求可同时通过检查，撞唯一约束变成 500 | 捕获 `DataIntegrityViolationException` 后重查重试一次 | [HabitService.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/service/HabitService.java) | `retriesOnceWhenConcurrentInsertViolatesUniqueConstraint` |
| AI 配额硬约束 | 历史表计数是“先查后调”，并发可超卖；且无法区分失败尝试与未尝试降级 | 独立 `ai_quota_usage` 表，`UPDATE ... WHERE used_count < limit` 原子扣减，先扣后调，事务回滚保证日/月两行一致 | [AiQuotaUsageRepository.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/dao/AiQuotaUsageRepository.java)、[AiAdviceService.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/service/AiAdviceService.java)、V6 迁移 | `AiQuotaUsageRepositoryTest`（唯一约束、原子扣减到上限、日/月独立计数） |
| CI | `.github/workflows` 是空目录，PR 合并无自动验证 | JDK 21 + Maven 缓存 + `mvn -B test`，push/PR 触发 | [ci.yml](../.github/workflows/ci.yml) | PR #45 的 CI 检查通过 |

### 健康检查与请求日志（Issue #50）

| 优化项 | 问题 | 方案 | 落点 | 验证 |
| --- | --- | --- | --- | --- |
| 健康检查与请求日志 | 无 actuator、无 requestId，生产无法快速探活，日志无法串起单次请求 | `spring-boot-starter-actuator`，只暴露 `/actuator/health`；`RequestIdFilter` 优先沿用 `X-Request-Id` 否则生成 UUID，写入 MDC 并回写响应头，记录请求开始/结束与耗时；日志格式增加 `%X{requestId}` | [RequestIdFilter.java](../src/main/java/com/fzdzzj/lifehabitassistant/config/RequestIdFilter.java)、[RequestIdConfig.java](../src/main/java/com/fzdzzj/lifehabitassistant/config/RequestIdConfig.java)、[SecurityConfig.java](../src/main/java/com/fzdzzj/lifehabitassistant/config/SecurityConfig.java) | `RequestIdAndHealthHttpIntegrationTest` 4 项：health 公开可用、请求头透传且日志可检索、无头自动生成、不跨请求泄漏 |

### 真实 MySQL 迁移验证（Issue #52）

| 优化项 | 问题 | 方案 | 落点 | 验证 |
| --- | --- | --- | --- | --- |
| 真实 MySQL 迁移验证 | 测试用 H2，Flyway SQL 只在本地 MySQL 手工跑过 | Testcontainers 拉起 mysql:8.0.36，空库上 `flyway.migrate()` 两次：首次应用 V1-V6、第二次为 0，并断言迁移历史完整且全部 SUCCESS；`disabledWithoutDocker` 使本地无 Docker 时跳过、CI 必跑 | [FlywayMySqlMigrationIntegrationTest.java](../src/test/java/com/fzdzzj/lifehabitassistant/FlywayMySqlMigrationIntegrationTest.java)、pom.xml（testcontainers junit-jupiter/mysql） | CI 上该测试通过；本地无 Docker 显示 skipped，不破坏其他测试 |

### AI 提示词文件化（Issue #54）

| 优化项 | 问题 | 方案 | 落点 | 验证 |
| --- | --- | --- | --- | --- |
| AI 提示词文件化 | 系统提示词是 Java 常量，改提示词要动代码 | 移到 `resources/prompts/ai-advice-system-<version>.txt`（当前 v1，内容与原常量一致）；`AiSystemPromptLoader` 启动时加载并缓存，版本非法或文件缺失快速失败；`AiAdviceService` 消费 loader | [AiSystemPromptLoader.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/service/AiSystemPromptLoader.java)、[ai-advice-system-v1.txt](../src/main/resources/prompts/ai-advice-system-v1.txt) | `AiSystemPromptLoaderTest` 4 项：默认 v1 加载、自定义版本走测试资源、非法版本拒绝、缺失文件快速失败 |

### 深分页（Issue #56）

| 优化项 | 问题 | 方案 | 落点 | 验证 |
| --- | --- | --- | --- | --- |
| 深分页 | `PageRequest.of(page, size)` 深页码全表扫描，耗时不可控 | 新增 `app.pagination.max-offset`（默认 10000），`offset = page * size`（long 防溢出）超过上限返回统一 400，提示缩小日期范围；阈值配置化 | [PaginationProperties.java](../src/main/java/com/fzdzzj/lifehabitassistant/config/PaginationProperties.java)、[HabitService.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/service/HabitService.java) | `HabitServiceTest` 新增超限拒绝且不查库；`ValidationBoundaryHttpIntegrationTest` 新增边界：100*100 拒、99*100 放行、size>100 仍被原校验拒 |

### 本地 MySQL 冒烟验收（Issue #58）

| 检查项 | 结果（2026-08-04 实测） |
| --- | --- |
| 构建与测试 | `mvn clean package` BUILD SUCCESS；80 项测试 0 失败、1 项跳过（Testcontainers，本地无 Docker） |
| 应用启动与迁移 | JDK 21 + 本地 MySQL（3306，`.env` 配置）；`/actuator/health` UP；旧库启动时自动补跑 Flyway V5/V6，迁移历史全部 SUCCESS |
| 冒烟链路 | 注册 → 登录 → 未授权 401 → 4 天习惯录入（夜睡/午睡、运动、饮水含风险饮料、重复提交更新）→ 列表/单日查询 → 趋势 → 规则分析 → 周报/月报 → xlsx/pdf 导出（文件头 PK / %PDF 校验）→ AI 降级路径（source=RULE_FALLBACK） |
| 落库核对 | `ai_advice_history` 1 行（call_counted=0）、`ai_quota_usage` 0 行、`habit_records` 4 行 |
| 实测确认的业务规则 | wakeAt / startedAt / recordedAt 不得晚于当前时间：造数必须用过去时间，未来时间会被 400 拒绝（符合预期，非缺陷） |

### 自定义每日目标（Issue #60）

| 优化项 | 问题 | 方案 | 落点 | 验证 |
| --- | --- | --- | --- | --- |
| 自定义每日目标 | 所有用户共用 `app.health.*` 全局阈值，无法按个人体质/目标调整 | 新增 `daily_goals` 表（每用户一行，`uk_daily_goal_user` 唯一约束）；`GET/PUT/DELETE /api/goals` 查询/upsert/重置；`GoalService.effective(user)` 解析生效目标，无记录时回落全局默认值 | [V7 迁移](../src/main/resources/db/migration/V7__create_daily_goals.sql)、[DailyGoal.java](../src/main/java/com/fzdzzj/lifehabitassistant/pojo/DailyGoal.java)、[GoalService.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/service/GoalService.java)、[GoalController.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/controller/GoalController.java) | `DailyGoalRepositoryTest` 3 项（唯一约束、用户隔离、按用户删除）；`GoalServiceTest` 5 项（默认回落、upsert、重置、min<=max 校验、`effective` 不触请求上下文）；`GoalHttpIntegrationTest` 6 项（401、默认值、保存/更新/重置、统一 400、用户隔离、自定义目标改变趋势达标） |
| 统计/建议/报告/AI 读取用户目标 | 达标与建议仍按全局阈值硬编码 | `HealthStatisticsService.summarize(records, anchor, goals)`、`RuleBasedAdviceGenerator.generate(days, statistics, goals)`、`HabitService.dailyEvaluation` 与 AI `userPrompt` 全部改为消费 `DailyGoals`；周报/月报/导出自动继承 | [HealthStatisticsService.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/service/HealthStatisticsService.java)、[RuleBasedAdviceGenerator.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/service/RuleBasedAdviceGenerator.java)、[AiAdviceService.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/service/AiAdviceService.java)、[HabitService.java](../src/main/java/com/fzdzzj/lifehabitassistant/server/service/HabitService.java) | `HealthStatisticsServiceTest`、`RuleBasedAdviceGeneratorTest` 新增自定义/放宽目标改变达标与风险断言；既有 5 个测试类适配新签名 |

## 三、关键取舍（防止“换个思路做坏”的约束）

- **配额为什么用独立表而不是历史表计数**：历史表无法区分“调用失败（应计费）”和“未调用降级（不应计费）”；独立表只记录已发起的模型请求，语义干净。
- **为什么“先扣后调”**：并发两个请求要么都先占用成功再调用，要么配额满时 UPDATE 影响 0 行触发降级并回滚另一周期的占用，杜绝超卖。
- **为什么不用 `ON DUPLICATE KEY UPDATE`**：H2 的 MySQL 兼容模式不支持该语法，测试必挂；改用 JPA 实体占位 + 唯一约束兜底（并发冲突重查），只把两库都支持的通用 `UPDATE ... WHERE` 写成 native。
- **为什么读取配额用原生标量查询**：原生 SQL 更新后，同一事务内实体查询会命中 Hibernate 一级缓存，读到旧值；标量查询直读数据库。
- **并发重试为什么只一次**：冲突窗口极小，重试一次即可；极端情况下仍冲突按 500 暴露，不无限重试。
- **为什么用内存限流而不是 Redis**：单实例部署下 `ConcurrentHashMap` 足够，且符合“不引 Redis”的约束；多实例部署时状态不共享，届时再换分布式限流（已记录为已知边界）。
- **为什么“窗口限频 + 失败锁定”双机制**：窗口限频挡批量爆破（一个 IP 短时间大量尝试），失败锁定挡同一账号撞库（换 IP 也要撞开锁定的账号）；两者互补。
- **为什么限流返回 429 而不是 401**：被限流意味着“请求不被处理”，用 429 语义更准确；也避免攻击者通过“失败到底返回 401 还是 429”判断账号是否存在。

## 四、待办优化（供下一步选择，按优先级）

### P0（安全）

- 注册/登录限流：已完成（Issue #46 / PR #47，见二）。

### P1（工程与运维）

| 优化项 | 现状 | 方案 | 验收标准 |
| --- | --- | --- | --- |
| 统计预聚合 | 区间记录全量载入内存聚合 | 数据量有真实压力后再做 DB 聚合/预聚合 | 暂缓，不做过度设计 |

### P2（产品与架构）

| 优化项 | 说明 |
| --- | --- |
| 自定义每日目标 | 已完成（Issue #60，见二） |
| 前端 | ECharts 消费 `/api/trends`，报告页与 AI 解读展示 |
| 依赖升级 | Boot 3.3.3 → 3.4/3.5 后把 AI 适配层换成 spring-ai starter；独立任务，需回归安全与 JPA |
| 报表缓存/异步导出 | 周期报告 TTL 缓存；大区间导出任务表异步生成 |

## 五、明确不做（防跑偏清单）

- 不做角色/RBAC、管理员后台、消息队列、微服务、多端适配（需求文档明确排除）。
- 不升级 Spring Boot（独立任务，不与本轮混做）。
- 不引入 Redis（单实例内存限流够用时不加中间件）。
- 不改 `.env`、不提交任何真实密钥/令牌；只更新 `.env.example`。
- 不修改已发布的 Flyway 迁移文件，只新增版本。
- 每项优化仍走 `Issue → codex/<issue>-<module> → 测试 → Conventional Commit → Push → PR → 合并 → 关 Issue`。

## 六、本轮验收标准

- [x] `mvn test`：80 个测试通过（1 项 Testcontainers 跳过，本地无 Docker；新增配额原子性、并发重试、异常兜底、限流窗口、失败锁定、429 集成测试）。
- [x] 注册/登录限流：连续失败被 429 拒绝；正常登录不受影响（HTTP 集成测试覆盖）。
- [x] PR #45 的 GitHub Actions CI 通过。
- [x] Flyway V6 建表（H2 已验证 SQL 语义；真实 MySQL 冒烟列入 P1）。
- [x] 本地 MySQL 冒烟：启动应用 → 注册登录 → 录数据 → 趋势/报告 → 导出 → AI 降级路径可走通（2026-08-04 实测通过，证据见二）。
- [x] 自定义每日目标：`mvn test` 96 项通过（1 项 Testcontainers 跳过）；目标查询/upsert/重置/校验/隔离与自定义目标改变达标、建议全部覆盖。
