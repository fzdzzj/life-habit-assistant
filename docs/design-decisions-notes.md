# 设计决策笔记：限流双机制与配额原子扣减

> 用途：把优化文档里“只记结论、忘了因果”的两条关键决策展开成学习笔记，对应 Issue #46（认证限流）与 Issue #44（AI 配额）。先自己讲一遍因果，再看代码，最后用文末自测题检查。

## 一、认证限流：为什么“窗口限频 + 失败锁定”双机制

### 1. 结论

两个机制防的是**两种不同形态的攻击**，单独任何一个都有漏洞，所以必须组合：

| 机制 | 挡什么 | 按什么粒度 | 数据来源 |
| --- | --- | --- | --- |
| 窗口限频 | 单机/单 IP 批量爆破、批量注册 | 每个 IP 每分钟请求次数 | 成功或失败的**请求本身** |
| 失败锁定 | 换 IP 撞同一个账号（撞库） | 每个 (IP, 用户名) 的连续失败次数 | **登录失败事件** |

一句话记忆：**窗口限频挡“一个人疯狂试”，失败锁定挡“一群人试同一个号”。**

### 2. 为什么单独不够

只做窗口限频：攻击者准备一批代理 IP，每个 IP 每分钟只试 5 次，总数照样能撞开弱密码账号——窗口限频只按 IP 计数，不认识“账号”这个概念。

只做失败锁定：攻击者不用换账号，一个 IP 一秒钟可以发几百个不同密码的请求；失败锁定只在失败次数达到阈值后生效，连失败都攒不起来的批量尝试根本到不了锁定环节。另外，失败锁定不做窗口限频的话，注册接口也可以被拿来批量刷垃圾账号。

两者叠加后：批量爆破先被窗口限频拦住；即使绕过窗口（换 IP），同一账号连续失败也会触发锁定，攻击者必须同时换 IP 和换账号才能继续，成本大幅上升。

### 3. 代码怎么落地的

配置（`application.yml`）：

```yaml
app:
  security:
    rate-limit:
      register-per-ip: 5      # 每个 IP 每分钟最多 5 次注册
      login-per-ip: 20        # 每个 IP 每分钟最多 20 次登录
      login-failures: 5       # 连续失败 5 次
      login-cooldown: 15m     # 锁定 15 分钟
```

限流器（`AuthRateLimiter.java`）两个入口，职责分开：

```java
public boolean tryLogin(String ip, String username) {
    if (isBlocked(ip, username)) {          // 先看失败锁定
        return false;
    }
    return tryAcquire("login:" + ip, properties.loginPerIp()); // 再看窗口限频
}
```

失败事件在 `AuthService.login` 里记录——注意**用户名不存在也记失败**，否则攻击者能通过“报错速度/是否锁定”探测账号是否存在：

```java
User user = users.findByUsername(input.username()).orElse(null);
if (user == null || !encoder.matches(input.password(), user.getPasswordHash())) {
    rateLimiter.recordLoginFailure(clientIp, input.username());  // 不存在也记
    throw ApiException.unauthorized("用户名或密码错误");
}
rateLimiter.recordLoginSuccess(clientIp, input.username());      // 成功后清零
```

锁定状态本身存在 `ConcurrentHashMap<String, FailureState>`，key 是 `ip + "|" + username`，所以“张三用 IP-A 被锁”不会影响“张三用 IP-B”和“李四用 IP-A”。

### 4. 为什么失败锁定用 (IP + 用户名)，而不是只按用户名

如果只按用户名锁定：攻击者换 IP 没用了（正好达成目的），但**正常用户在公司网络（同一出口 IP）里输错几次密码，会被自己同事的失败尝试锁掉**，属于误伤；而且容易变成“故意输错锁死他人账号”的拒绝服务。按 (IP, 用户名) 组合，误伤面最小，同时仍能拦住换 IP 撞同一账号——因为换 IP 后 key 变化，需要重新积累 5 次失败。

这是安全性和可用性之间的明确取舍：**先保可用性，失败锁定作为第二道防线**，真正的第一道防线仍是窗口限频 + 强密码 + BCrypt。

### 5. 为什么限流返回 429 而不是 401

401 的语义是“身份凭证无效”，被限流时凭证可能是完全正确的，只是请求不被处理，所以用 429（Too Many Requests）更准确。

还有一个隐蔽的安全原因：如果限流返回 401，攻击者可以观察“同一条请求什么时候从 429 变回 401”，从而判断账号是否存在。统一返回 429 不泄露这个信息。

### 6. 为什么用内存限流而不是 Redis

项目是单实例部署，`ConcurrentHashMap` 的滑动窗口已经够用，不引入中间件（需求约束）。已知边界：**多实例部署时各实例状态不共享，限流会被放大 N 倍**，届时再换分布式限流（如 Redis + Lua）。这个边界写进了优化文档，不是没意识到，而是刻意不为当前规模过度设计。

## 二、AI 配额：为什么不用 `ON DUPLICATE KEY UPDATE`

### 1. 结论

`ON DUPLICATE KEY UPDATE` 是 **MySQL 专有语法**，测试环境用的 H2（MySQL 兼容模式）不支持，写进 Repository 里测试必挂。改用三步通用组合，两个数据库都支持：

1. **实体占位**：先查，没有就 `saveAndFlush` 建一行（撞唯一约束则忽略）；
2. **唯一约束兜底**：`user_id + period_type + period_key` 唯一，并发建行只成功一个；
3. **通用原子 UPDATE**：`UPDATE ... WHERE used_count < limit` 只更新还能扣减的行，影响行数 0 就代表配额满。

### 2. 为什么原方案会挂在测试上

MySQL 兼容模式的 H2 只支持与标准 SQL 对齐的语法，解析不了 MySQL 的 `INSERT ... ON DUPLICATE KEY UPDATE`。也就是说**真实 MySQL 能跑、CI/本地测试必挂**。可移植逻辑要遵守“两库交集”，这是本项目测试策略（H2 模拟 MySQL）的直接推论。

### 3. 代码怎么落地的

原子扣减（`AiQuotaUsageRepository.java`）——`UPDATE ... WHERE` 是两库都支持的标准 SQL：

```java
@Modifying
@Query(value = """
        UPDATE ai_quota_usage
        SET used_count = used_count + 1, updated_at = :now
        WHERE user_id = :userId AND period_type = :periodType AND period_key = :periodKey
          AND used_count < :limit
        """, nativeQuery = true)
int incrementIfBelowLimit(...);
```

`WHERE used_count < :limit` 让“扣减”和“检查配额”在**同一行同一语句**里完成。返回 1 = 扣成功；返回 0 = 已到上限，`AiAdviceService` 抛配额耗尽并降级成规则建议：

```java
if (quotaRepository.incrementIfBelowLimit(...) != 1) {
    throw new QuotaExceededException("daily quota exhausted");
}
```

占位建行（`AiAdviceService.ensureRow`）——首次使用才执行，并发时唯一约束保证只建成一行：

```java
try {
    quotaRepository.saveAndFlush(new AiQuotaUsage(user, period, key, now));
} catch (DataIntegrityViolationException ignored) {
    // 并发请求已创建同一行：继续执行，由下面的原子 UPDATE 负责扣减
}
```

### 4. 为什么“先扣后调”而不是“先查后调”

先查后调有两个并发问题：查的时候额度还剩 1，两个请求同时通过检查，模型调用两次，配额超卖。改成“先扣后调”后，并发两个请求只有一个能拿到 `UPDATE` 影响 1 行，另一个影响 0 行直接降级——**配额是硬约束，不是软提示**。

同理，日额度和月额度两次扣减放在**同一个事务**里，任一失败抛异常，事务回滚把另一次的扣减一起撤销，保证日/月两行计数一致。

### 5. 为什么读取配额要用原生标量查询，不能混用实体查询

原生 SQL 更新 `used_count` 后，同一事务内再用 JPA 实体查询会命中 **Hibernate 一级缓存**（持久化上下文），读到的是更新前的旧值。所以读取也走原生标量查询直读数据库：

```java
@Query(value = "SELECT used_count FROM ai_quota_usage WHERE ...", nativeQuery = true)
Optional<Integer> findUsedCount(...);
```

教训：**一条数据只走一条访问路径**；原生 SQL 改过的行，读取也要用原生 SQL，否则要么读到旧值、要么依赖 `clear()` 之类容易踩坑的手段。

## 三、面试自问自答

**Q1：限流你做了哪几层，为什么？**
窗口限频按 IP 挡批量爆破，失败锁定按 (IP, 用户名) 挡换 IP 撞同一账号，两者互补；登录失败记事件时不区分账号是否存在，避免探测账号。

**Q2：为什么不按用户名单独做失败锁定？**
公司网络共用出口 IP 会误伤同事；还可能被“故意输错”变成锁死他人的拒绝服务。按 (IP, 用户名) 组合误伤面最小。

**Q3：并发扣减配额怎么保证不超卖？**
独立配额表 + 唯一约束 + 单条 `UPDATE ... WHERE used_count < limit` 原子扣减，影响行数 0 即配额满；日/月两次扣减同一事务，失败一起回滚。

**Q4：为什么不用 `ON DUPLICATE KEY UPDATE`？**
MySQL 专有语法，H2 MySQL 兼容模式不支持，测试必挂；改用“实体占位 + 唯一约束 + 通用 UPDATE”组合，两库都支持。

**Q5：为什么原生 SQL 更新后读取也要用原生查询？**
同一事务内实体查询命中 Hibernate 一级缓存，读旧值；访问路径要统一。

**Q6：为什么限流返回 429？**
429 语义是“请求不被处理”，比 401 准确；也避免攻击者通过 401/429 的差异探测账号是否存在。

## 四、自测题（合上文档再答）

- [ ] 能画出“窗口限频 + 失败锁定”分别挡什么、按什么粒度、数据来源是什么。
- [ ] 能说出为什么“只按用户名锁定”有误伤问题。
- [ ] 能解释 `UPDATE ... WHERE used_count < limit` 为什么天然防并发超卖。
- [ ] 能说出不用 `ON DUPLICATE KEY UPDATE` 的直接原因（H2 不支持）和替代组合。
- [ ] 能解释“先扣后调”和“日/月同一事务”各自解决什么问题。
- [ ] 能解释一级缓存为什么导致旧值，以及访问路径统一的原则。
