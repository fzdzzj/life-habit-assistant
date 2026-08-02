# 项目复盘：生活习惯助手后端（第一人称自述版）

> 用法：先通读一遍，再合上文档，按“为什么这样做”把每一节讲给自己听；讲不出来的段落就是需要回炉的地方。面试前只看“面试自问自答”和“验收清单”。

## 一、一句话讲清这个项目

我给“生活习惯助手”做了一个单人用户的后端：用户注册登录后，每天记录睡眠、饮食、运动、饮水，系统给出趋势、规则型健康建议、周报/月报，还能下载 Excel/PDF，并且可以显式请求 AI 生成个性化解读。

技术栈一句话：Spring Boot 3.3 + Java 21 + Spring Data JPA + Spring Security/JWT + MySQL + Flyway + POI/OpenPDF + H2（测试），按 Issue → 分支 → 测试 → PR 的流程开发。

## 二、我当初怎么把需求拆成模块

拿到需求文档我先做的不是写代码，而是**拆边界**：账号、数据、分析、输出四类能力。然后按依赖关系排成里程碑，每个里程碑一个 Issue、一个功能分支：

| 顺序 | Issue | 做了什么 | 为什么这个顺序 |
| --- | --- | --- | --- |
| 1 | 认证 | 注册/登录、BCrypt、JWT | 没有用户身份，后面所有数据隔离都做不了 |
| 2 | 每日记录 | 习惯记录 + 睡眠/运动/饮水明细 | 先有数据，才有分析 |
| 3 | 趋势与规则建议 | 统计口径统一、规则引擎 | 报告和分析共用同一套统计 |
| 4 | 周报/月报 | 自然周/自然月即时聚合 | 建立在统一统计之上 |
| 5 | 导出 | Excel 多 Sheet、PDF | 报告接口稳定后再做输出 |
| 6 | 校验与 Flyway | 参数/业务边界、版本化迁移 | 收口：错误信息统一、数据库可演进 |
| 7 | 查询性能 | 批量加载子表 | 修复 N+1 |
| 8 | AI 建议 | OpenAI 显式解读 + 降级 + 配额 | 最后的增值能力，不影响原有确定性逻辑 |

每个模块都是：`codex/<issue>-<module>` 分支 → 写测试 → `mvn test` → Conventional Commit → 推分支 → PR → 合并 → 关 Issue。我只在自己的仓库 `fzdzzj/life-habit-assistant` 上操作。

## 三、核心设计决策（每一条都要能讲出“为什么”）

### 1. 分层：common / pojo / server(controller-service-dao) / config

我按职责而不是按业务域分层，因为它是个单人项目，层与层之间的规矩比模块数量更重要：

- `common`：所有接口共享的 `Result`、错误码、全局异常处理。
- `pojo`：JPA 实体、请求 DTO、响应 VO。实体和响应分开，不让 `HabitRecord` 直接出现在 JSON 里。
- `server/controller`：HTTP 映射 + 参数校验，不写业务。
- `server/service`：事务边界、业务规则、降级决策。
- `server/dao`：Spring Data JPA Repository。
- `config`：JWT 过滤器、Security、配置属性注册。

面试表达：“Controller 只负责翻译 HTTP 和校验，Service 只负责业务和事务，DAO 只负责持久化；实体是持久化模型，DTO 是接口契约，两者不混用。”

### 2. 统一响应与统一异常

普通 JSON 接口永远返回 `{code, message, data}`；`code=1` 成功，否则是错误码。错误码和 HTTP 状态映射放在 `ErrorCode` 枚举里，`ApiExceptionHandler` 统一兜底，所以前端只需要处理一种结构。

一个关键取舍：**文件下载接口不包 `Result`**，直接返回文件流。因为 `Result` 是 JSON 契约，硬塞文件流会让浏览器和客户端都难处理。

### 3. JWT + 无状态安全 + 当前用户

登录发 JWT（claims 只放 username），`JwtAuthenticationFilter` 每请求解析并塞进 `SecurityContext`；服务层不信任前端传的任何用户 ID，统一通过 `CurrentUser.require()` 从上下文拿当前用户。

为什么这样做：
- 无状态：多实例部署不用共享 session。
- 数据隔离写在一个点：`HabitService.range()` 里用 `currentUser.require()`，上层分析、报告、AI 拿到的天然只属于当前用户，不会因为“忘了传 userId”而越权。
- JWT 密钥要求至少 32 字符，避免弱密钥。

### 4. 聚合根：一天一条主记录，子表存可重复事件

`habit_records` 是“一个用户一天”的聚合根（`user_id + record_date` 唯一），睡眠、运动、饮品是它下面的一对多子表。

我做过两个关键判断：

- **午睡/夜睡不拆表**：它们结构完全一样（类型、起止时间），差异只是 `sleep_type` 枚举。拆表会让“算一天总睡眠”变成多表聚合。
- **运动按类型但不拆表**：跑步、力量、散步共用“类型、强度、时长、时间”，强度决定中等强度当量，力量训练影响频次建议；用枚举 + 派生查询表达，而不是建表。

子表全部 `cascade = ALL, orphanRemoval = true`，由主记录管理生命周期；集合字段用 `final` + 初始化，对外只给 `List.copyOf(...)`，防止调用方替换 Hibernate 正在追踪的集合或改坏内部状态。

### 5. 统计是单一计算边界

趋势、规则建议、周报、月报、AI 提示词全部消费同一个 `HealthStatisticsService.summarize(...)` 的输出。它接收“当前用户的记录列表”和“连续天数锚点”，产出不可变的 `HealthStatistics`。

为什么：如果每个功能各自算均值/达标率，口径一定会漂移（比如饮水算总量还是有效补水），报表和趋势对不上。统一边界后，改一个字段所有下游一起生效。

### 6. 阈值全部配置化

健康阈值（睡眠 420–540 分钟、运动 30 分钟、有效补水 1500 ml、饮食 ≥3）和饮品规则（每种饮品的补水系数、风险阈值）都在 `application.yml`，用 `@ConfigurationProperties` 绑定，不散落在实体或控制器里。产品改标准时只动配置，不用改代码。

### 7. Flyway 管数据库演进

`src/main/resources/db/migration/V1..V5` 顺序执行并记录在 `flyway_schema_history`；**已经发布的迁移文件绝不修改**，后续只能新增版本。旧库首次接入用 `FLYWAY_BASELINE_ON_MIGRATE=true` 打基线，避免重跑 V1。

真实的迁移案例：V2 把 `bedtime/wake_time` 迁成睡眠片段、V3 把运动总分钟迁成运动明细、V4 把 `water_ml` 迁成饮品明细——都是“先建子表 → 用 SQL 把旧总量转成历史明细并注明未知细节 → 再删旧列”，既不丢数据也不虚构历史。

### 8. 报告即时聚合，不落快照

周报/月报每次请求现算：查记录 → 统一统计 → 规则建议。不建 report 表存快照。原因：数据每天都在变，快照要么过期要么维护同步逻辑；即时计算在单人数据量下完全够快，也少一个一致性难题。

### 9. 导出的实现细节

- Excel 用 Apache POI，多个 Sheet：Summary、Daily trends、Weekly summaries、Risks and advice、AI advice。
- PDF 用 OpenPDF，中文字体用 `STSong-Light + UniGB-UCS2-H`（否则中文乱码），表格用 `PdfPTable`。
- 导出内容里附加“该周期最近一次已保存的 AI 解读”，但导出本身绝不调用模型。

### 10. 校验分层

- Controller：Bean Validation（`@Min/@Max/@PastOrPresent/@Pattern`）+ `@Validated`，管“参数形态”。
- Service：业务规则（日期范围 ≤366 天、睡眠片段 ≤24 小时、month 不得晚于当前月等），管“业务边界”。
- 全局异常处理器把两类都转成 `40000` 统一结构；401/403/404/500 各归各码。

### 11. AI 模块的信任边界

我保留了“规则判定权威、AI 只做表达”的原则：发给模型的只有脱敏聚合数字和规则结论，没有用户名、ID、备注、原始记录；模型被要求输出固定 JSON 结构并含免责声明。AI 默认关闭，只在显式调用时触发，未启用/无 key/无数据/配额满/超时/供应商异常全部降级成规则建议。

为什么没用 spring-ai starter：版本矩阵里 2.0.x 要 Spring Boot 4、1.0.x 要 Boot 3.4+，项目是 3.3.3；兼容的 0.8.x 不在 Maven Central 且官方仓库访问受限。所以我写了 `OpenAiChatClient` 接口 + `RestClient` 实现，保留替换点。详见 [ai-advice.md](ai-advice.md)。

### 12. 测试策略

- 单元测试：Service + Mockito（mock DAO/依赖），快，覆盖分支。
- 集成测试：`@SpringBootTest + MockMvc`，H2（MySQL 兼容模式）真实走 JPA 和 JWT 过滤链，覆盖认证、隔离、校验、AI 降级。
- 导出测试：真的生成 XLSX 用 POI 读回断言 Sheet/单元格，PDF 用 `PdfReader` 验证可打开。
- 测试里 flyway 关闭、`ddl-auto=create-drop`，测试数据每次全新，不依赖本地 MySQL。

## 四、一次请求的生命周期（代码导游）

**注册**：`AuthController` → `AuthService.register`：查重 → BCrypt 加密 → 保存 → 发 JWT。

**带 token 访问**：`JwtAuthenticationFilter` 解析 `Bearer` → 把 username 放进 `SecurityContext`；业务里 `CurrentUser.require()` 从用户名回查 `users` 表。失败统一 401。

**保存一天的习惯**：`HabitService.save`：`findByUserAndRecordDate` 有则更新、无则新建（唯一约束兜底）→ 返回 VO。子表由各自 controller/service 独立维护，均以 `date` + 当前用户锁定主记录。

**算统计**：`HealthStatisticsService.summarize`：每条记录映射成 `DailyStatistics`（夜睡/午睡/总睡眠、有效补水、风险饮品、达标判定），再汇总成周期指标（均值、总量、连续天数、按类型聚合）。

**规则建议**：`RuleBasedAdviceGenerator.generate(days, statistics)`：只读统计，按阈值产出 risks/suggestions；没有记录时给“继续记录”的引导而不是报错。

**报告**：`ReportService.weekly/monthly`：算起止边界（周一为周起点、`YearMonth` 月边界）→ 查记录 → 统一统计 → 规则建议 → 附最近一次已保存 AI 解读。

**导出**：`ReportExporter.xlsx/pdf(report)`：纯函数，把 `ReportResponse` 转成二进制，不查库、不调 AI。

**AI 解读**：`AiAdviceService.generate`：配额校验 → 脱敏 prompt → `OpenAiChatClient` → JSON 解析 → 保存 `ai_advice_history`（AI 或 RULE_FALLBACK 都存，fallback 不计数、失败尝试计数）→ 返回 source 和额度使用情况。

## 五、我踩过的坑（真实发生过）

1. **Hibernate 受管集合被替换**：实体集合如果用普通 `new ArrayList` 返回或整体替换，Hibernate 可能丢失追踪导致更新丢失或意外删数据。解决：集合字段 `final`，对外返回 `List.copyOf`；修改走实体方法。
2. **子表更新时实体状态不一致**：只把新子实体加入内存集合而不同步主实体，或反过来，导致孤儿删除或脏检查出人意料。解决：所有变更走主实体的 add/remove 方法，一个事务内完成。
3. **双写不一致**：早期主表存 `water_ml` 总量、又引入子表，两边都可能改，必然对不上。解决：删掉冗余列，一切实时计算。
4. **N+1 查询**：遍历记录取子表集合产生海量 SQL。解决：`@BatchSize` 批量加载 + 集成测试盯 SQL 行为。
5. **校验遗漏**：未来日期、超范围分页、非法导出格式。解决：Controller 注解 + Service 业务校验 + 专门的边界集成测试。
6. **mock save 返回 null**：单测里 `history.save(...)` 默认返回 null，服务取 `getId()` 直接 NPE。解决：mock 用 `thenAnswer(invocation -> invocation.getArgument(0))` 原样返回入参。
7. **Spring AI 版本冲突**：详情见设计决策 11，教训是先查版本矩阵再决定依赖，而不是“最新版直接加”。
8. **导出中文乱码**：PDF 必须显式指定中文字体，Excel 字符串按类型写入单元格。

## 六、面试自问自答

**Q1：用户数据隔离怎么做的？**
JWT 只证明“是谁”，服务层一律 `CurrentUser.require()` 拿当前用户；所有 Repository 查询都带 `userId` 或基于用户查到的聚合根。测试里有“两个用户互相看不到对方数据”的集成用例。

**Q2：为什么一天只允许一条记录？**
这是领域约束：习惯记录以天为粒度。`user_id + record_date` 唯一约束兜底，重复提交走更新而不是报错，用户体验更好。

**Q3：跨午夜睡眠怎么算的？**
睡眠片段存的是完整 `LocalDateTime`（入睡和醒来），时长 = `Duration.between`，跨不跨午夜对计算没有影响；午夜只是“今天”的归属问题，由主记录的 `record_date` 决定。

**Q4：为什么不把午睡夜睡拆成两张表？**
结构同构，枚举表达差异，避免多表 JOIN；统计时按 `sleep_type` 过滤聚合即可。

**Q5：规则建议和 AI 建议什么关系？**
规则建议是权威和兜底；AI 只对脱敏聚合做自然语言解读。AI 挂了系统仍然可用，且响应带 `source` 字段让前端知道用的是哪一路。

**Q6：为什么报告不存库？**
单人数据量即时聚合足够快；避免快照过期和同步逻辑。若未来数据量大，再按周期缓存并做失效策略。

**Q7：Flyway 的 baseline 是干什么的？**
数据库已有旧表时，把“当前状态”标记为基线，之后 Flyway 只执行比基线新的迁移；否则 V1 会重复执行。一次性操作，建完就关。

**Q8：怎么防止 SQL 注入和越权？**
所有查询走 Spring Data JPA 派生查询或参数绑定；越权靠统一 CurrentUser 边界，不信任请求里的任何归属参数。

**Q9：实体为什么不能直接返回给前端？**
实体包含持久化细节（懒加载代理、集合、内部状态），直接序列化可能触发意外查询或泄露字段；VO/DTO 是稳定契约，改库表不影响 API。

**Q10：测试为什么用 H2 而不是连 MySQL？**
CI 和本地都要能跑；H2 的 MySQL 兼容模式覆盖了 JPA/SQL 行为，Flyway 在测试里关闭、`create-drop` 建表，隔离且快。迁移文件本身用真实 MySQL 验证过。

## 七、验收清单（证明我真的会做）

- [ ] 能不看文档，画出“请求 → 安全 → 服务 → 统计 → 建议/报告/AI → 导出”的数据流。
- [ ] 能解释上面 12 条设计决策里至少 10 条“为什么”。
- [ ] 能说清 8 个坑的根因和预防。
- [ ] 本地能跑：MySQL + Flyway 自动建表 → Swagger 里注册登录 → 录数据 → 看趋势/报告 → 下载导出。
- [ ] `mvn test` 49 个测试全绿，并能说出新增测试覆盖了什么。
- [ ] 能讲出 Git 流程：为什么每个模块独立分支、为什么合并后关 Issue。

## 八、如果重做 / 下一步

- 前端：用 ECharts 消费 `/api/trends`，把报告和 AI 解读做成页面。
- 依赖升级：升级 Boot 到 3.4+ 后把 `OpenAiChatClient` 换成 spring-ai starter 实现（接口不变）。
- 可扩展：自定义每日目标阈值（现在阈值是全局配置）、多用户角色（当前明确不做复杂权限）。
- 运维：`prod` profile 已关 Swagger；部署时补齐日志采集、监控和备份策略。
