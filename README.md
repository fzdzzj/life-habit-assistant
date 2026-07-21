# 生活习惯助手 Agent 后端

## 启动

1. 在 MySQL 中执行 `src/main/resources/db/schema.sql`。
2. 设置 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 和强随机 `JWT_SECRET`。
3. 使用 Maven（JDK 21）运行：`mvn spring-boot:run`。
4. 打开 `http://localhost:8080/swagger-ui.html`，先调用注册、登录接口，再在 Swagger 的 Authorize 中填入 `Bearer <token>`。

## 演示流程

注册 -> 登录 -> 录入每日记录 -> 查询历史/趋势 -> 生成建议 -> 查看周报或月报 -> 下载 xlsx/pdf。
