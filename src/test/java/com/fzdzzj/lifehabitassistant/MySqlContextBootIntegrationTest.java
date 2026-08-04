package com.fzdzzj.lifehabitassistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 全上下文启动回归测试：在真实 MySQL 8 上执行 Flyway V1-V11 并以默认
 * ddl-auto=validate 启动 Spring 上下文。
 *
 * 防止“Flyway 迁移能跑、Hibernate 校验却失败”的启动缺陷（如 V8 LONGBLOB
 * 与实体 BLOB 类型不一致）。本地无 Docker 时跳过，CI 必跑。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.flyway.enabled=true"
})
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class MySqlContextBootIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("life_habit_boot_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contextStartsWithMigrationsAndHibernateValidationOnMySql() {
        Integer successMigrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertEquals(11, successMigrations,
                "真实 MySQL 上应完整执行 V1-V11 且全部成功（Hibernate validate 已通过，否则上下文不会启动）");
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertEquals(0, users, "空库不应有预置用户数据");
    }
}
