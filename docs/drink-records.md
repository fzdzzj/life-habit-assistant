# 饮品明细与健康规则模块

## 目标

将旧版每日 `water_ml` 总量升级为可追溯的饮品事件。有效补水量和风险饮品量都从明细实时计算，避免总量字段与子表发生双写不一致。

```text
habit_records（一位用户的一天）
  └─ drink_records（当天 0..N 次饮用）
```

不按白水、咖啡、酒精分别建表：它们有相同的“类型、容量、时间、备注”结构；差异由 `drink_type` 枚举和规则配置表达。

## 数据与接口

| 字段 | 说明 |
| --- | --- |
| `drink_type` | WATER、UNSWEETENED_TEA、COFFEE、MILK、JUICE、SUGAR_SWEETENED_DRINK、CARBONATED_SWEET_DRINK、ENERGY_DRINK、ALCOHOL、OTHER |
| `volume_ml` | 单次容量，1–3000 ml |
| `recorded_at` | 完整饮用时间，日期必须等于 URL 中的 `recordDate`，且不得是未来 |
| `other_name` | 仅 `OTHER` 时必填，保留未知或后续扩展类型 |

先创建当天的习惯记录，再维护饮品明细：

```text
GET    /api/habits/{date}/drink-records
POST   /api/habits/{date}/drink-records
PUT    /api/habits/{date}/drink-records/{id}
DELETE /api/habits/{date}/drink-records/{id}
```

示例：

```json
{
  "drinkType": "COFFEE",
  "volumeMl": 200,
  "recordedAt": "2026-07-22T10:00:00",
  "note": "美式咖啡"
}
```

## 规则为何放配置

`DrinkHealthRules` 从 `application.yml` 读取系数和阈值，而不是把数字散落在 Controller 或 Entity：

| 类型 | 有效补水系数 |
| --- | --- |
| 白水 | 1.0 |
| 无糖茶 | 0.9 |
| 咖啡、牛奶 | 0.8 |
| 果汁 | 0.5 |
| 含糖、含糖碳酸、能量饮料、酒精、未知饮品 | 0.0 |

当前周期风险阈值：含糖饮料 500 ml、含糖碳酸 330 ml；任意记录到能量饮料或酒精即提示风险。有效补水量按 `volume_ml × 系数` 四舍五入后相加，风险饮品量则是风险类型的原始容量之和。

因此 500 ml 白水 + 200 ml 咖啡 + 330 ml 含糖碳酸饮料的结果是：有效补水 **660 ml**，风险饮品 **330 ml**，而不是错误地把三者相加为 1030 ml 补水。

## 对趋势、报告和建议的影响

- 每日、趋势、周报、月报字段使用 `hydrationMl`，并额外展示 `riskDrinkVolumeMl`。
- 每日达标基于有效补水，而不是总饮用容量。
- 规则型建议会把达到阈值的饮品风险加入风险与建议列表。
- Excel/PDF 的摘要、日趋势、周汇总均包含有效补水和风险饮品量。

## 数据迁移

Flyway `V4__split_drink_records.sql` 依次：创建 `drink_records` → 将旧 `water_ml > 0` 迁成 `WATER / 历史汇总记录`，时间固定为当天 12:00 并注明细节未知 → 删除旧列。

这保留总量而不编造旧记录的真实饮用时间或饮品种类。发布过的 V4 不能被修改；后续变更必须新增 V5。

## 面试表达

“我把每日饮水总量改为聚合根下的一对多饮品事件。主记录定义用户和日期边界，子表保存可重复发生的饮用行为；补水和风险都是可配置规则在读取时计算，不在数据库冗余存储。Flyway 先迁历史总量再删除旧列，既避免丢数据，也不虚构历史细节。”
