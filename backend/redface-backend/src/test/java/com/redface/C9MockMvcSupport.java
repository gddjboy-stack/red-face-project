package com.redface;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * C9 MockMvc 测试支撑方法。该类不以 Test 结尾，避免被 Surefire 当作测试类执行。
 */
abstract class C9MockMvcSupport {
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    protected void clearTables() {
        jdbcTemplate.update("DELETE FROM user_session");
        jdbcTemplate.update("DELETE FROM suspicion_votes");
        jdbcTemplate.update("DELETE FROM user_membership");
        jdbcTemplate.update("DELETE FROM user_identity");
        jdbcTemplate.update("DELETE FROM user_photo_collection");
        jdbcTemplate.update("DELETE FROM popularity_ledger");
        jdbcTemplate.update("DELETE FROM coefficient_ledger");
        jdbcTemplate.update("DELETE FROM team_distribution_batches");
        jdbcTemplate.update("DELETE FROM tokens");
        jdbcTemplate.update("DELETE FROM photo_assets");
        jdbcTemplate.update("DELETE FROM collect_state");
        jdbcTemplate.update("DELETE FROM operations_log");
        jdbcTemplate.update("DELETE FROM player_round_stats");
        jdbcTemplate.update("DELETE FROM team_round_stats");
        jdbcTemplate.update("DELETE FROM pool_round_stats");
        jdbcTemplate.update("DELETE FROM player_round");
        jdbcTemplate.update("DELETE FROM teams");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM rounds");
    }

    protected String loginAndGetToken(String code) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        return root.path("data").path("token").asText();
    }

    protected String loginAndGetUserId(String code) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        return root.path("data").path("userId").asText();
    }

    protected void insertRound(int roundId, String status) {
        jdbcTemplate.update("""
                INSERT INTO rounds (round_id, name, start_time, end_time, status)
                VALUES (?, ?, ?, ?, ?)
                """, roundId, "第" + roundId + "轮", LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), status);
    }

    protected void insertTeam(int teamId, String name) {
        jdbcTemplate.update("INSERT INTO teams (team_id, name) VALUES (?, ?)", teamId, name);
    }

    protected void insertPlayer(int playerId, int number, String name) {
        jdbcTemplate.update("""
                INSERT INTO players (player_id, name, number, status)
                VALUES (?, ?, ?, 'active')
                """, playerId, name, number);
    }

    protected void insertPlayerRound(int playerId, int roundId, int teamId) {
        jdbcTemplate.update("""
                INSERT INTO player_round (player_id, round_id, team_id, is_spy, player_status)
                VALUES (?, ?, ?, 0, 'normal')
                """, playerId, roundId, teamId);
    }

    protected void insertPlayerStats(int playerId, int roundId, long individualPopularity, long spyPopularity) {
        jdbcTemplate.update("""
                INSERT INTO player_round_stats (player_id, round_id, individual_popularity, spy_popularity, coefficient)
                VALUES (?, ?, ?, ?, 100)
                """, playerId, roundId, individualPopularity, spyPopularity);
    }

    protected void insertTeamStats(int teamId, int roundId, long teamPopularity) {
        jdbcTemplate.update("""
                INSERT INTO team_round_stats (team_id, round_id, team_popularity, distributed_popularity)
                VALUES (?, ?, ?, 0)
                """, teamId, roundId, teamPopularity);
    }

    protected void insertPhotoAsset(String assetId, int playerId, String previewUrl) {
        jdbcTemplate.update("""
                INSERT INTO photo_assets (asset_id, player_id, preview_url, download_url)
                VALUES (?, ?, ?, ?)
                """, assetId, playerId, previewUrl, previewUrl.replace("preview", "download"));
    }

    protected void insertToken(String tokenId, int playerId, long points, String photoAssetId, String status) {
        jdbcTemplate.update("""
                INSERT INTO tokens (token_id, player_id, points, photo_asset_id, product_sku, status, created_at)
                VALUES (?, ?, ?, ?, 'sku_c9', ?, ?)
                """, tokenId, playerId, points, photoAssetId, status, LocalDateTime.now());
    }

    protected void insertMembership(String userId, LocalDateTime membershipUntil, String lastTokenId) {
        jdbcTemplate.update("""
                INSERT INTO user_membership (user_id, membership_until, last_token_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, membershipUntil, lastTokenId, LocalDateTime.now(), LocalDateTime.now());
    }
}
