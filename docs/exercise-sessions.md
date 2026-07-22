# 运动明细模块设计

## 目标

把每日总分钟数改为可追溯的多次运动记录，支持项目、强度和可选指标，并让趋势、报告和建议从同一份明细数据实时聚合。

## 为什么是一张子表

```text
habit_records (一天、一个用户的聚合记录)
  └─ exercise_sessions (当天 0..N 次运动)
```

不要为跑步、游泳、力量训练分别建表：字段高度重复，查询周报时还要跨表合并。`exercise_type` 枚举表示项目，`OTHER + other_name` 保留未知或扩展项目。

## 数据模型

| 字段 | 含义 |
| --- | --- |
| `exercise_type` | RUN、WALK、CYCLING、SWIMMING、STRENGTH、BALL_SPORT、YOGA、STRETCHING、HIIT、OTHER |
| `intensity` | LOW、MEDIUM、HIGH |
| `duration_minutes` | 必填，1–600 分钟 |
| `started_at` | 必填，归属日期必须等于其日期，且不能在未来 |
| `other_name` | 仅 OTHER 必填，例如跳绳、爬山 |
| `distance_km` | 可选，`DECIMAL(6,2)` / Java `BigDecimal` |
| `calories_kcal`、`note` | 可选补充信息 |

## 接口

先创建当天的 `habit_records`，再维护运动明细：

```text
GET    /api/habits/{date}/exercise-sessions
POST   /api/habits/{date}/exercise-sessions
PUT    /api/habits/{date}/exercise-sessions/{id}
DELETE /api/habits/{date}/exercise-sessions/{id}
```

请求示例：

```json
{
  "exerciseType": "RUN",
  "intensity": "HIGH",
  "durationMinutes": 30,
  "startedAt": "2026-07-21T18:00:00",
  "distanceKm": 5.00,
  "caloriesKcal": 320,
  "note": "间歇跑"
}
```

## 计算规则

- 实际运动分钟数：所有 `duration_minutes` 求和，用于趋势和报告展示。
- 中等强度当量：HIGH 的分钟数 ×2；LOW/MEDIUM 原样计入。
- 日评价：中等强度当量至少 30 分钟时，运动维度达标。
- 周期建议（周期至少 7 天）：目标为每周 150 分钟中等强度当量、每周至少 2 次力量训练。

这是可解释的习惯规则，不是医疗诊断。

## 数据迁移

Flyway `V3__split_exercise_sessions.sql` 创建子表，将旧 `exercise_minutes > 0` 迁为 `OTHER / 历史汇总记录 / MEDIUM`，标注其细节未知，最后删除旧列。这样不丢失总量，也不伪造项目与强度。

## 面试表达

“我将一天一条的运动总量改为聚合根下的一对多明细。主记录负责用户和日期边界，子表负责可重复发生的运动事件。总分钟数不再双写，而在领域对象中实时聚合；Flyway 通过新增版本迁移保留旧数据。高强度换算和力量训练频次作为透明规则进入建议层，并由 HTTP 集成测试和真实 MySQL 启动验证覆盖。”
