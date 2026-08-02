package com.redface;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 验证 H2 测试环境能够按 schema-h2.sql 自动初始化 C1 定义的核心表。
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaInitializationTest {

    private static final int EXPECTED_TABLE_COUNT = 26;

    private final JdbcTemplate jdbcTemplate;

    SchemaInitializationTest(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 验证 C1 Schema 在 H2 MySQL 兼容模式下可执行，并确认废弃表没有被创建。
     */
    @Test
    void shouldInitializeAllC1TablesWithoutDeprecatedTables() {
        Set<String> tableNames = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'public'",
                String.class
        ).stream().map(String::toLowerCase).collect(Collectors.toSet());

        assertThat(tableNames)
                .containsExactlyInAnyOrder(
                        "players",
                        "teams",
                        "rounds",
                        "player_round",
                        "popularity_ledger",
                        "player_round_stats",
                        "team_round_stats",
                        "pool_round_stats",
                        "coefficient_ledger",
                        "tokens",
                        "photo_assets",
                        "user_photo_collection",
                        "user_identity",
                        "user_session",
                        "user_membership",
                        "suspicion_votes",
                        "team_distribution_batches",
                        "collect_state",
                        "operations_log",
                        "idempotency_ledger",
                        "team_coefficient_ledger",
                        "group_vote_ledger",
                        "live_metric_watermark",
                        "product_price_config",
                        "order_sales_ledger",
                        "manual_sales_ledger"
                );
        assertThat(tableNames).hasSize(EXPECTED_TABLE_COUNT);
        assertThat(tableNames)
                .doesNotContain("manual_adjustments", "team_distribution_details", "popularity_summary");
    }
}
