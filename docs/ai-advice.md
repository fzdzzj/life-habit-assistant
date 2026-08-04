# OpenAI 个性化建议模块设计

## 目标与边界

在不改变现有规则统计与规则建议的前提下，为“分析、周报、月报”提供显式触发的 OpenAI 自然语言解读，并把每次结果保存为按用户隔离的历史，供导出复用。

核心原则：

- 事实与阈值判定由本地计算负责（`HealthStatistics` → `RuleBasedAdviceGenerator`），模型只负责把脱敏聚合指标“翻译”成通俗解读。
- 模型只在用户显式调用 `POST /api/ai/...` 时被请求；查看报告、下载导出、查询趋势都不会触发模型。
- 未启用、未配置密钥/模型、无记录、配额耗尽、超时或供应商异常时，一律返回规则建议（`source=RULE_FALLBACK`）。

## 为什么使用 spring-ai starter（2026-08 已升级）

早期项目固定在 Spring Boot 3.3.3 时，Spring AI 2.0.x 要求 Boot 4.x，1.0.x/1.1.x 要求 Boot 3.4+，兼容版本不在 Maven Central，因此先用 `RestClientOpenAiChatClient` 手写调用 OpenAI Chat Completions，并保留 `OpenAiChatClient` 接口作为替换点。

升级到 Spring Boot 3.5.16 + Spring AI 1.1.8 后，手写实现已替换为 `SpringAiOpenAiChatClient`（基于 `ChatClient` / `OpenAiChatModel`），服务层与测试依赖的 `OpenAiChatClient` 接口保持不变。实现由 `AiAdviceConfig` 基于项目自己的 `app.ai.advice.*` 属性手动装配：baseUrl、apiKey、模型、超时与 temperature 全部沿用 `AI_ADVICE_*` 环境变量，不改 `.env` 变量名。

同时把 Spring AI 的模型自动配置全部显式关闭（`spring.ai.model.* = none`）：自动配置在缺少 `spring.ai.openai.api-key` 时会强制校验并导致上下文启动失败，而本项目由 `app.ai.advice.*` 统一管理开关与密钥。未来升级 Spring Boot 4 后可切到 Spring AI 2.0，届时只需调整 Bean 装配或启用对应自动配置。

## 数据流

```text
HabitRecord
  -> HealthStatisticsService.summarize()   (事实：均值、总量、达标率、风险饮品)
  -> RuleBasedAdviceGenerator              (权威风险与建议，也是降级兜底)
  -> AiAdviceService                        (配额校验 + 脱敏 prompt + 保存历史)
  -> OpenAiChatClient -> Chat Completions   (只发送聚合数字和规则结论)
  -> AiAdviceContentParser                  (强制 JSON 结构，解析失败即降级)
  -> ai_advice_history                      (AI 或 RULE_FALLBACK 均保存)
```

报告导出路径不经过模型：

```text
GET /api/reports/weekly|monthly[/export]
  -> ReportService 读取该周期最近一条 ai_advice_history
  -> ReportExporter 把 AI 解读写入 Excel 的 “AI advice” Sheet 或 PDF 的 AI Advice 节
```

## 配置（全部来自环境变量，经 `.env` 注入）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_ADVICE_ENABLED` | `false` | 总开关 |
| `OPENAI_API_KEY` | 空 | 密钥只存在于进程内存，不落库、不打印 |
| `OPENAI_MODEL` | 空 | 模型 ID，以 OpenAI 官方文档为准；不硬编码 |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | 兼容代理或中转 |
| `AI_ADVICE_DAILY_LIMIT` | `3` | 每天最多发起多少次模型请求 |
| `AI_ADVICE_MONTHLY_LIMIT` | `30` | 每月最多发起多少次模型请求 |
| `AI_ADVICE_TIMEOUT_SECONDS` | `30` | 连接与读取超时 |
| `AI_ADVICE_PROMPT_VERSION` | `v1` | 写入历史，便于提示词升级后区分版本 |

配额按“已发起的模型请求次数”统计，包括失败尝试（防止快速失败绕过限制）；未发起请求的降级（禁用、无密钥、无记录、配额已满）不计入配额。

配额计数放在独立表 `ai_quota_usage`（`user_id + period_type + period_key` 唯一，按天/按月各一行），使用行级原子扣减：

```sql
UPDATE ai_quota_usage
SET used_count = used_count + 1, updated_at = NOW()
WHERE user_id = ? AND period_type = ? AND period_key = ?
  AND used_count < ?
```

“先扣后调”：两个并发请求要么都成功占用（每个请求先扣减后再调用模型），要么在配额满时由 UPDATE 影响 0 行触发降级，事务回滚当次已占用的另一周期额度，因此并发不会超卖。实现细节：行占位用 JPA 实体 + 唯一约束兜底（并发冲突时重查），因为 H2 的 MySQL 兼容模式不支持 `ON DUPLICATE KEY UPDATE`；额度读取用原生标量查询，避免同一事务内 Hibernate 一级缓存返回 UPDATE 前的旧值。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/ai/analyses?days=7` | 最近 N 天 AI 解读 |
| `POST` | `/api/ai/reports/weekly?week=YYYY-MM-DD` | 自然周 AI 解读 |
| `POST` | `/api/ai/reports/monthly?month=YYYY-MM` | 自然月 AI 解读 |

响应：

```json
{
  "code": 1,
  "message": "success",
  "data": {
    "source": "AI",
    "content": {
      "periodSummary": "…",
      "riskExplanation": "…",
      "recommendations": ["…"],
      "nextPeriodPlan": "…",
      "encouragement": "…",
      "disclaimer": "本建议仅作健康生活方式参考，不构成医疗诊断或治疗建议；如有健康问题请咨询医生。"
    },
    "historyId": 1,
    "createdAt": "…",
    "dailyUsed": 1,
    "dailyLimit": 3,
    "monthlyUsed": 1,
    "monthlyLimit": 30
  }
}
```

`source` 为 `AI` 或 `RULE_FALLBACK`；前端应把降级结果明确展示给用户。

## 脱敏与提示词

发给模型的用户消息只包含：天数、记录数、睡眠/饮食/运动/补水均值、风险饮品总量、连续天数、按类型的运动与饮品聚合、规则风险与建议列表。不包含用户名、用户 ID、备注、原始明细；历史表 `content` 字段也只存这段结构化内容。

系统提示词固定要求：只输出 JSON、不编造指标、不诊断/开药/推荐极端饮食或危险训练、最多 3 条建议、必须包含固定免责声明。模型偶尔用 ```json 围栏包裹输出，解析器会剥掉围栏；解析失败按降级处理。

## 测试覆盖

- `AiAdviceContentParserTest`：合法 JSON、围栏 JSON、数组根、非 JSON。
- `AiAdviceServiceTest`：禁用、无数据、成功、供应商失败、日/月配额、按用户计费与历史归属、自然周边界。
- `AiAdviceHttpIntegrationTest`：未授权 401、禁用时降级并保存历史、报表附加最近建议、用户隔离。
- `ReportExporterTest`：Excel/PDF 包含或省略 AI 解读。

## 未来升级路径

1. 升级 Spring Boot 到 3.4+（独立任务，需重新验证安全配置与 JPA）。
2. 引入 `spring-ai-starter-model-openai`，新增一个 `OpenAiChatClient` 实现包装 `ChatClient`。
3. 通过配置开关切换实现，服务层保持不变。
