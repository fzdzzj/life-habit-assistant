# 规范差异：报告解读结构化输出

本文件修改“AI 报告解读”的模型输出与解析要求（现有行为以 README 与 `docs/optimization-plan.md` 描述为准）。

## MODIFIED Requirements

### Requirement: 报告解读生成与解析
**Previous**：系统提示词要求模型输出 JSON，服务端对文本做宽松解析：剥离围栏、字段缺失时以空字符串填充，无服务端 schema 校验。

WHEN 用户请求 AI 报告解读,
系统 SHALL 要求模型输出符合既定 JSON schema 的结构化内容，并通过结构化输出转换器严格解析；IF 输出缺失必填字段、解析失败或模型不可用, 系统 SHALL 返回规则降级回复并保持既有配额语义。

#### Scenario: 结构化输出成功
GIVEN AI 服务可用且模型返回完整合法 JSON
WHEN 用户请求报告解读
THEN 系统返回结构化解读内容
AND 保存历史记录且标记为计费

#### Scenario: 必填字段缺失
GIVEN 模型返回 JSON 但缺少必填字段
WHEN 系统解析模型输出
THEN 系统返回规则降级回复
AND 该次调用计入配额
AND 不返回字段残缺的半结构化内容

#### Scenario: 围栏或非 JSON 文本
GIVEN 模型返回带代码围栏或无法解析的文本
WHEN 系统解析模型输出
THEN 系统返回规则降级回复
AND 该次调用计入配额

#### Scenario: 模型不可用
GIVEN 模型调用超时或供应商异常
WHEN 请求结束
THEN 系统返回规则降级回复
AND 该次调用计入配额

#### Scenario: 历史快照兼容
GIVEN 历史表中已存在旧格式 JSON 快照
WHEN 报告或导出读取该快照
THEN 快照仍可被解析并正常展示
AND 既有快照无需迁移

---

## 备注

- 对外响应结构 `AiAdviceResponse`/`AiAdviceContent` 保持不变，本 capability 只增强生成路径的可靠性。
- 规则降级内容与现有 `RULE_FALLBACK` 语义一致；结构化解析只应用于模型生成路径，不改变历史快照读取逻辑。
