package com.redface;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * C20-5 大屏页本地演示启动器（仅用于人工截图与视觉验收，非自动化断言）。
 *
 * <p>默认被 surefire 排除（类名不以 Test 结尾），需要看页面时手动指定运行：
 * <pre>
 * mvn -o test -Dtest=DisplayDemoServerC20 -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 * 它会以 H2 内存库启动完整应用于 18080 端口，灌入一轮演示数据后保持运行 20 分钟，
 * 便于用浏览器打开 /display/ 实际操作换票、轮询、切榜与出图。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18080",
                "redface.display.token=demo-display-token",
                "redface.admin.token=demo-admin-token"
        })
@ActiveProfiles("test")
class DisplayDemoServerC20 {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void runDemoServer() throws Exception {
        seed();
        System.out.println("=== C20-5 演示服务已启动: http://127.0.0.1:18080/display/ ===");
        System.out.println("=== 展示令牌: demo-display-token ===");
        Thread.sleep(20 * 60 * 1000L);
    }

    private void seed() {
        jdbcTemplate.update("DELETE FROM player_round_stats");
        jdbcTemplate.update("DELETE FROM team_round_stats");
        jdbcTemplate.update("DELETE FROM player_round");
        jdbcTemplate.update("DELETE FROM teams");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM rounds");

        jdbcTemplate.update("""
                INSERT INTO rounds (round_id, name, start_time, end_time, status)
                VALUES (1, '第一轮 · 初舞台', ?, ?, 'active')
                """, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(3));

        String[] teams = {"红队", "蓝队", "金队"};
        for (int t = 1; t <= teams.length; t++) {
            jdbcTemplate.update("INSERT INTO teams (team_id, name) VALUES (?, ?)", t, teams[t - 1]);
        }

        String[] names = {"沈千语", "陆知微", "苏映雪", "顾清宁", "白锦书",
                "温以歌", "江晚吟", "叶疏影", "宋雨眠", "林寒枝", "许星洛", "阮南栀"};
        long[] values = {128600, 96400, 88250, 75300, 71800, 64200,
                58900, 52100, 47600, 41300, 38800, 38800};
        for (int i = 0; i < names.length; i++) {
            int playerId = i + 1;
            int teamId = (i % 3) + 1;
            jdbcTemplate.update("""
                    INSERT INTO players (player_id, name, number, status) VALUES (?, ?, ?, 'active')
                    """, playerId, names[i], playerId);
            jdbcTemplate.update("""
                    INSERT INTO player_round (player_id, round_id, team_id, is_spy, player_status)
                    VALUES (?, 1, ?, 0, 'normal')
                    """, playerId, teamId);
            jdbcTemplate.update("""
                    INSERT INTO player_round_stats (player_id, round_id, individual_popularity, spy_popularity, coefficient)
                    VALUES (?, 1, ?, 0, 100)
                    """, playerId, values[i]);
        }

        long[] teamValues = {286300, 231400, 198700};
        for (int t = 1; t <= teams.length; t++) {
            jdbcTemplate.update("""
                    INSERT INTO team_round_stats (team_id, round_id, team_popularity, distributed_popularity, coefficient)
                    VALUES (?, 1, ?, 0, 100)
                    """, t, teamValues[t - 1]);
        }
    }
}
