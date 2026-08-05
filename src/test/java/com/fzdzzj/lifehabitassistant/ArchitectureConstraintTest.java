package com.fzdzzj.lifehabitassistant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架构约束回归测试。
 *
 * 把 openspec 扩展性预留规范
 * （spec/changes/update-backend-evolution/specs/extensibility-groundwork/spec-delta.md）
 * 固化为可执行检查。未来引入社交 / App 端能力时：
 * 1. 以独立领域模块与独立 Flyway 迁移接入，不得修改已发布迁移；
 * 2. 需要显式修改本测试中的放行清单（并同步规范与文档）。
 */
class ArchitectureConstraintTest {

    private static final Path ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path MIGRATIONS = ROOT.resolve("src/main/resources/db/migration");
    private static final Path MAIN_JAVA = ROOT.resolve("src/main/java");

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?im)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_]+)");
    private static final Pattern ENTITY_TABLE = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");

    /** 预留功能在本期不得建表；功能立项时新增独立迁移并在此清单中放行。 */
    private static final Set<String> RESERVED_TABLES = Set.of(
            "friends", "friend_requests", "posts", "comments", "communities",
            "leaderboard", "routes", "screen_time", "third_party_identity_bindings");

    /** 预留领域包：本期不存在；功能立项时以独立模块新增并在此放行。 */
    private static final Set<String> RESERVED_PACKAGES = Set.of(
            "social", "community", "leaderboard", "apptracking");

    @Test
    void everyMigratedTableIsMappedToJpaEntity() throws IOException {
        Set<String> unmapped = new TreeSet<>(tables());
        unmapped.removeAll(entityTables());
        assertTrue(unmapped.isEmpty(),
                "迁移中建的表必须映射到 JPA 实体（禁止死表/空表），未映射: " + unmapped);
    }

    @Test
    void reservedExtensionTablesAreNotCreatedYet() throws IOException {
        Set<String> conflicts = new TreeSet<>(tables());
        conflicts.retainAll(RESERVED_TABLES);
        assertTrue(conflicts.isEmpty(),
                "本期不得为未实现能力建空表；功能立项时应新增独立迁移并同步放行，发现: " + conflicts);
    }

    @Test
    void migrationsAreAppendOnlyVersionedFiles() throws IOException {
        List<Integer> versions;
        try (Stream<Path> stream = Files.list(MIGRATIONS)) {
            versions = stream.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.*\\.sql"))
                    .map(name -> Integer.parseInt(name.substring(1, name.indexOf("__"))))
                    .sorted()
                    .toList();
        }
        // 放行清单：新增迁移时必须同步更新；已发布的迁移文件不得修改。
          assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), versions,
                  "迁移必须按 V1..Vn 追加且版本号不重复；新增迁移时同步更新本清单");
    }

    @Test
    void aggregateStatisticsAreProducedOnlyByUnifiedEntrypoint() throws IOException {
        // 排行榜等未来能力必须复用统一统计口径：HealthStatistics 只能由 HealthStatisticsService 构造，
        // 任何“各自独立的计算逻辑”都会在这里失败。
        List<String> constructors = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(MAIN_JAVA)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        if (readUtf8(path).contains("new HealthStatistics(")) {
                            constructors.add(relative(path));
                        }
                    });
        }
        assertEquals(
                List.of("com/fzdzzj/lifehabitassistant/server/service/HealthStatisticsService.java"),
                constructors,
                "HealthStatistics 聚合结果只允许由统一统计入口 HealthStatisticsService 构造");
    }

    @Test
    void statisticsConsumersUseUnifiedEntrypoint() throws IOException {
        // 趋势/报告/AI 上下文都必须经由 HealthStatisticsService，不允许自行重算聚合指标。
        List<String> consumers = List.of(
                "server/service/AnalysisService.java",
                "server/service/ReportService.java",
                "server/service/AiAdviceService.java",
                "server/service/AiConversationService.java");
        for (String consumer : consumers) {
            String source = readUtf8(MAIN_JAVA.resolve("com/fzdzzj/lifehabitassistant").resolve(consumer));
            assertTrue(source.contains("HealthStatisticsService"),
                    consumer + " 必须依赖统一统计服务（排行榜等未来能力同理）");
        }
    }

    @Test
    void sessionCarriesMetadataOnlyAndAuthHasSingleSystem() throws IOException {
        // 端类型仅作会话元数据：本期 session 只含设备名/设备 ID/IP/UA，
        // 不得出现按端类型分支鉴权的字段或独立 App 会话/令牌体系。
        List<String> forbidden = List.of(
                "deviceType", "clientType", "platformType", "appSession", "mobileSession", "webSession");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(MAIN_JAVA)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String source = readUtf8(path);
                        for (String token : forbidden) {
                            if (source.contains(token)) {
                                hits.add(relative(path) + " -> " + token);
                            }
                        }
                    });
        }
        assertTrue(hits.isEmpty(),
                "发现端类型鉴权分支或独立会话体系（端类型仅可作会话元数据）: " + hits);
    }

    @Test
    void noReservedDomainPackagesExistYet() throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(MAIN_JAVA)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        for (Path part : path) {
                            if (RESERVED_PACKAGES.contains(part.toString().toLowerCase())) {
                                hits.add(relative(path));
                                break;
                            }
                        }
                    });
        }
        assertTrue(hits.isEmpty(),
                "预留领域本期不建包；功能立项时以独立模块新增并同步放行: " + hits);
    }

    private Set<String> tables() throws IOException {
        Set<String> tables = new TreeSet<>();
        try (Stream<Path> stream = Files.list(MIGRATIONS)) {
            for (Path migration : stream.filter(Files::isRegularFile).toList()) {
                Matcher matcher = CREATE_TABLE.matcher(readUtf8(migration));
                while (matcher.find()) {
                    tables.add(matcher.group(1));
                }
            }
        }
        return tables;
    }

    private Set<String> entityTables() throws IOException {
        Set<String> tables = new TreeSet<>();
        try (Stream<Path> stream = Files.walk(MAIN_JAVA)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = ENTITY_TABLE.matcher(readUtf8(file));
                while (matcher.find()) {
                    tables.add(matcher.group(1));
                }
            }
        }
        return tables;
    }

    private String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取 " + path, e);
        }
    }

    private String relative(Path path) {
        return MAIN_JAVA.relativize(path).toString().replace('\\', '/');
    }
}
