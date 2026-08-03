package com.fzdzzj.lifehabitassistant;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Flyway migration set (V1-V6) against a real MySQL 8 database,
 * not just H2 in MySQL-compatibility mode. Skipped automatically when Docker
 * is unavailable; CI runs on GitHub-hosted runners where Docker is present.
 */
// MOCK keeps the servlet context (SecurityConfig needs HttpSecurity); no real
// server is started, and the datasource is redirected to the MySQL container.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.jpa.hibernate.ddl-auto=none"
})
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class FlywayMySqlMigrationIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("life_habit_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void allMigrationsShouldApplyTwiceOnAnEmptyDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load();

        MigrateResult firstRun = flyway.migrate();
        assertEquals(6, firstRun.migrationsExecuted, "空库首次迁移应执行 V1-V6 共 6 个版本");

        MigrationInfo[] applied = flyway.info().applied();
        List<String> versions = Arrays.stream(applied)
                .map(info -> info.getVersion().toString())
                .toList();
        assertEquals(List.of("1", "2", "3", "4", "5", "6"), versions, "迁移历史应按版本顺序完整");
        assertTrue(Arrays.stream(applied)
                        .allMatch(info -> info.getState() == MigrationState.SUCCESS),
                "所有已应用迁移都应为 SUCCESS");

        MigrateResult secondRun = flyway.migrate();
        assertEquals(0, secondRun.migrationsExecuted, "第二次迁移应无新版本可执行");
    }
}
