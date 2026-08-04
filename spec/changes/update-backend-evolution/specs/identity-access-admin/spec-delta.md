# 规范差异：身份、权限、管理后台与多端

本文件定义从“单一普通用户 + 单个短期 JWT”演进为“RBAC + 多端会话 + 管理后台 API”的规范变更（当前行为以 README 与现有实现为准）。

## ADDED Requirements

### Requirement: 用户角色
WHEN 新用户注册,
系统 SHALL 默认赋予 USER 角色；WHERE 管理员调整用户角色, 系统 SHALL 允许在 USER 与 ADMIN 之间变更，并保证至少保留一名有效 ADMIN。

#### Scenario: 注册默认角色
GIVEN 新用户完成注册
WHEN 系统创建账户
THEN 账户角色为 USER
AND 普通用户无管理端点访问权限

#### Scenario: 管理员提升与降级
GIVEN 当前用户为 ADMIN
WHEN 管理员将某 USER 提升为 ADMIN 或将 ADMIN 降级为 USER
THEN 目标用户角色立即生效于后续请求

#### Scenario: 普通用户变更角色被拒
GIVEN 当前用户为 USER
WHEN 用户尝试调整任何角色
THEN 系统返回 403

#### Scenario: 最后一名管理员保护
GIVEN 系统中仅剩一名有效 ADMIN
WHEN 管理员尝试降级或禁用该 ADMIN
THEN 系统拒绝操作并返回 400

---

### Requirement: 管理端点授权
WHERE 请求访问管理端点,
系统 SHALL 验证当前用户具有 ADMIN 角色，未认证返回 401，无权限返回 403。

#### Scenario: 管理员放行
GIVEN 当前用户具有 ADMIN 角色
WHEN 请求任意管理端点
THEN 请求进入管理逻辑并返回正常结果

#### Scenario: 普通用户被拒
GIVEN 当前用户为 USER
WHEN 请求管理端点
THEN 系统返回 403
AND 不执行任何管理逻辑

#### Scenario: 未认证被拒
GIVEN 请求未携带有效访问令牌
WHEN 请求管理端点
THEN 系统返回 401

---

### Requirement: 访问令牌与刷新令牌轮换
WHEN 用户登录成功,
系统 SHALL 签发短期访问令牌与一次性 refresh token，并将会话绑定到登录设备；WHEN 用户使用有效 refresh token 请求续期, 系统 SHALL 签发新令牌对并使旧 refresh token 作废。

#### Scenario: 登录签发令牌对
GIVEN 用户提供有效凭据与设备标识
WHEN 登录成功
THEN 系统返回访问令牌、refresh token 与会话标识
AND 会话记录绑定该设备

#### Scenario: 续期轮换
GIVEN 用户持有有效且未使用的 refresh token
WHEN 用户请求续期
THEN 系统签发新的访问令牌与新的 refresh token
AND 旧 refresh token 立即失效

#### Scenario: 重用检测
GIVEN 某 refresh token 已被使用过一次
WHEN 同一 refresh token 再次被提交
THEN 系统拒绝续期
AND 撤销该会话的全部令牌

#### Scenario: 过期或无效令牌
GIVEN refresh token 已过期、被撤销或格式非法
WHEN 用户请求续期
THEN 系统返回 401
AND 不签发任何新令牌

---

### Requirement: 多端会话管理
WHEN 同一用户在不同设备登录,
系统 SHALL 为每台设备维护独立会话；WHEN 用户查看或撤销会话, 系统 SHALL 仅返回或影响该用户自己的会话。

#### Scenario: 多设备并存
GIVEN 同一用户已在设备 A 登录
WHEN 用户在设备 B 再次登录
THEN 设备 A 的会话保持有效
AND 设备 A 与设备 B 可独立续期

#### Scenario: 撤销指定会话
GIVEN 用户在设备 A 与设备 B 各有一个会话
WHEN 用户撤销设备 A 的会话
THEN 设备 A 的 refresh token 立即失效
AND 设备 B 的会话不受影响
AND 设备 A 的访问令牌保留至自然过期

#### Scenario: 登出当前会话
GIVEN 用户在设备 A 与设备 B 各有一个会话
WHEN 用户在设备 A 调用登出
THEN 设备 A 的 refresh token 失效
AND 设备 B 的会话不受影响

#### Scenario: 撤销他人会话被拒
GIVEN 会话属于另一用户
WHEN 当前用户尝试撤销该会话
THEN 系统返回 404

---

### Requirement: 密码找回
WHEN 用户请求密码重置,
系统 SHALL 生成一次性、短时效的重置令牌并通过配置的邮件通道发送，且对不存在的账号返回相同的成功提示；WHEN 用户提交有效令牌与新密码, 系统 SHALL 重置密码并使该用户全部会话失效。

#### Scenario: 请求成功
GIVEN 用户已注册且邮件通道已配置
WHEN 用户提交注册邮箱请求重置
THEN 系统生成一次性短时效令牌并发送邮件
AND 返回统一成功提示

#### Scenario: 账号不存在不泄露
GIVEN 提交的邮箱未注册
WHEN 用户请求重置
THEN 系统返回与账号存在时相同的成功提示
AND 不发送任何邮件

#### Scenario: 令牌有效并重置
GIVEN 用户持有有效且未使用的重置令牌
AND 新密码符合密码策略
WHEN 用户提交令牌与新密码
THEN 密码被重置
AND 该用户全部会话立即失效
AND 旧密码不再可用

#### Scenario: 令牌过期或已使用
GIVEN 重置令牌已过期或已被使用
WHEN 用户提交该令牌
THEN 系统返回 400 统一错误
AND 密码保持不变

#### Scenario: 新密码不合规
GIVEN 用户持有有效重置令牌
AND 新密码不符合密码策略
WHEN 用户提交令牌与新密码
THEN 系统返回 400 统一错误
AND 密码保持不变

---

### Requirement: 管理后台用户管理
WHERE ADMIN 请求用户管理端点,
系统 SHALL 提供用户分页列表、用户概览、启用/禁用与角色调整；WHEN 用户被禁用, 系统 SHALL 使其全部会话立即失效并禁止后续登录。

#### Scenario: 分页查询用户
GIVEN 当前用户为 ADMIN
WHEN 请求用户列表并携带分页与可选搜索参数
THEN 系统返回分页用户结果，包含角色与状态
AND 不返回任何用户密码哈希

#### Scenario: 禁用用户
GIVEN 当前用户为 ADMIN
WHEN 管理员禁用某用户
THEN 该用户全部会话立即失效
AND 该用户后续登录返回 403

#### Scenario: 非管理员被拒
GIVEN 当前用户为 USER
WHEN 请求用户管理端点
THEN 系统返回 403

---

### Requirement: 管理后台配额与导出任务管理
WHERE ADMIN 请求配额或导出任务管理端点,
系统 SHALL 提供 AI 配额查看与调整、任意用户导出任务查看与取消能力。

#### Scenario: 查看并调整配额
GIVEN 当前用户为 ADMIN
WHEN 管理员查看某用户 AI 配额或调整日/月额度
THEN 系统返回当前配额与用量
AND 调整结果立即对后续请求生效

#### Scenario: 取消任意用户任务
GIVEN 某任务处于 PENDING 或 RUNNING 状态且属于任意用户
WHEN ADMIN 取消该任务
THEN 任务流转为 CANCELLED
AND 任务所属用户收到取消状态

#### Scenario: 非管理员被拒
GIVEN 当前用户为 USER
WHEN 请求配额或导出任务管理端点
THEN 系统返回 403

---

### Requirement: API 版本化策略
WHEN 新增后端能力,
系统 SHALL 使用 `/api/v1` 前缀发布新端点并保持既有 `/api` 端点不变；WHEN 未来发生破坏性变更, 系统 SHALL 新增更高版本前缀而不修改既有版本端点。

#### Scenario: 新端点独立发布
GIVEN 新能力已实现
WHEN 请求其 `/api/v1` 端点
THEN 请求成功处理
AND 既有 `/api` 端点仍按原路径可用

#### Scenario: 旧端点回归
GIVEN 既有客户端使用旧 `/api` 路径
WHEN 新版本发布后发起旧路径请求
THEN 路径、请求与响应结构均保持不变

---

## MODIFIED Requirements

### Requirement: 登录与注册响应
**Previous**：登录与注册仅返回单个 JWT 字符串。

WHEN 登录或注册成功,
系统 SHALL 返回短期访问令牌、refresh token 与会话标识，且后续接口继续接受原 Bearer 访问令牌。

#### Scenario: 登录返回令牌对
GIVEN 用户提交有效凭据
WHEN 登录成功
THEN 响应包含访问令牌、refresh token 与会话标识
AND 访问令牌可立即用于现有业务接口

#### Scenario: 注册返回令牌对
GIVEN 新用户注册成功
WHEN 注册接口返回
THEN 响应包含访问令牌、refresh token 与会话标识
AND 新会话绑定注册时提供的设备标识

---

### Requirement: 登出语义
**Previous**：系统无登出接口，JWT 到期前无法主动失效。

WHEN 已认证用户调用登出,
系统 SHALL 使当前会话的 refresh token 立即失效，并允许 access token 保留至自然过期。

#### Scenario: 登出成功
GIVEN 用户已登录且持有会话
WHEN 用户调用登出
THEN 系统返回成功
AND 该会话的 refresh token 立即失效
AND 后续使用该 refresh token 续期返回 401

#### Scenario: 重复登出
GIVEN 会话已被登出
WHEN 同一会话再次调用登出
THEN 系统返回成功或 401，但不产生副作用

---

## 备注

- access token 保持短时效无状态，撤销会话只立即使 refresh token 失效；如需即时吊销 access token，应引入令牌黑名单，作为后续提案。
- 管理后台本提案仅交付后端 API；管理前端界面单独提案，不在本规范内。
- 密码找回的邮件通道为必选配置（prod）；dev profile 未配置时允许将令牌写入日志，便于本地验收。
