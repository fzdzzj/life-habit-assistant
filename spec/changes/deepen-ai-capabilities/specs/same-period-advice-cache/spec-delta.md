# 规范差异：同周期解读结果缓存

本文件定义“AI 报告解读”同周期结果缓存的规范变更（现有行为为每次请求重新生成、扣配额并写历史）。

## ADDED Requirements

### Requirement: 同周期解读结果缓存
WHEN 用户请求某一周期的 AI 报告解读且该周期存在未过期的 AI 成功缓存,
系统 SHALL 直接返回缓存结果并标记 cached=true，不调用模型、不扣减配额、不新增历史记录；IF 缓存未命中或请求指定强制刷新, 系统 SHALL 重新生成。

#### Scenario: 命中缓存
GIVEN 同一用户同一周期已有 AI 成功解读且缓存未过期
WHEN 用户再次请求该周期解读
THEN 返回相同解读内容
AND 响应标记 cached=true
AND 不调用模型、不扣减配额、不新增历史记录

#### Scenario: 首次请求
GIVEN 该周期不存在缓存
WHEN 用户请求该周期解读
THEN 系统正常生成解读
AND 响应标记 cached=false
AND AI 成功结果写入缓存

#### Scenario: 强制刷新
GIVEN 请求携带 refresh=true
WHEN 用户请求该周期解读
THEN 系统重新调用模型生成新结果
AND 更新缓存
AND 按正常语义扣减配额

#### Scenario: 规则降级不缓存
GIVEN 本次生成结果为规则降级
WHEN 请求结束
THEN 降级结果不写入缓存
AND 下次请求仍尝试调用模型

#### Scenario: 缓存过期
GIVEN 缓存已超过 TTL
WHEN 用户请求该周期解读
THEN 系统重新生成
AND 按正常语义扣减配额

---

### Requirement: 数据变更使解读缓存失效
WHEN 用户写入或修改习惯、饮料、运动、睡眠记录或每日目标,
系统 SHALL 使该用户全部解读缓存失效，保证缓存结果不旧于数据。

#### Scenario: 新增习惯记录
GIVEN 用户已有周报解读缓存
WHEN 用户新增或修改习惯记录
THEN 该用户全部解读缓存被清除
AND 下次请求按最新数据重新生成

#### Scenario: 修改每日目标
GIVEN 用户已有解读缓存
WHEN 用户修改每日目标
THEN 该用户全部解读缓存被清除
AND 下次请求按新目标生成

---

## 备注

- 只缓存 `source=AI` 的成功结果，规则降级不缓存，避免 AI 恢复后仍被旧降级内容阻挡。
- 命中缓存时不新增 history：报告/导出快照继续引用首次生成的历史记录，该语义在文档中说明。
- 旧端点 `POST /api/ai/analyses`、`POST /api/ai/reports/weekly|monthly` 新增可选 `refresh` 参数（默认 false）；响应新增 `cached` 字段，属向后兼容新增。
