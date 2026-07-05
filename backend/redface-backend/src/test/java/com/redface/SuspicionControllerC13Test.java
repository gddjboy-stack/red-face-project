package com.redface;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C13 真相识破 Controller 测试，覆盖防剧透与每轮一次提交红线。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuspicionControllerC13Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
    }

    @Test
    void notOpenShouldRejectSubmit() throws Exception {
        seedCandidates("player");
        String token = loginAndGetToken("code_c13_not_open");

        mockMvc.perform(post("/api/suspicion/submit")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"roundId\":1,\"suspectPlayerIds\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(41001))
                .andExpect(jsonPath("$.data.businessCode").value("not_open"));
    }

    @Test
    void invalidCandidateShouldBeRejected() throws Exception {
        seedCandidates("spy");
        String token = loginAndGetToken("code_c13_invalid_candidate");

        mockMvc.perform(post("/api/suspicion/submit")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"roundId\":1,\"suspectPlayerIds\":[99]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(41002))
                .andExpect(jsonPath("$.data.businessCode").value("invalid_candidate"));
    }

    @Test
    void sameUserCanSubmitOnlyOncePerRound() throws Exception {
        seedCandidates("spy");
        String token = loginAndGetToken("code_c13_once");

        mockMvc.perform(post("/api/suspicion/submit")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"roundId\":1,\"suspectPlayerIds\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.submitted").value(true))
                .andExpect(jsonPath("$.data.accepted[0]").value(1));

        mockMvc.perform(post("/api/suspicion/submit")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"roundId\":1,\"suspectPlayerIds\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(41003))
                .andExpect(jsonPath("$.data.businessCode").value("already_submitted"));

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM suspicion_votes", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void statusShouldReturnCandidatesDistributionWithoutSpyIdentity() throws Exception {
        seedCandidates("spy");
        String tokenA = loginAndGetToken("code_c13_status_a");
        String tokenB = loginAndGetToken("code_c13_status_b");

        submitOk(tokenA, 1);
        submitOk(tokenB, 2);

        mockMvc.perform(get("/api/suspicion/status?roundId=1")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.open").value(true))
                .andExpect(jsonPath("$.data.submitted").value(true))
                .andExpect(jsonPath("$.data.submittedPlayerIds[0]").value(1))
                .andExpect(jsonPath("$.data.candidates[0].number").value(3))
                .andExpect(jsonPath("$.data.candidates[0].count").value(1))
                .andExpect(jsonPath("$.data.candidates[1].number").value(5))
                .andExpect(jsonPath("$.data.candidates[1].count").value(1))
                .andExpect(content().string(not(containsString("isSpy"))))
                .andExpect(content().string(not(containsString("actualSpyPlayerId"))));
    }

    @Test
    void liveHomeShouldOpenSpyChannelOnlyInSpyMode() throws Exception {
        seedCandidates("spy");
        mockMvc.perform(get("/api/live/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spyChannelOpen").value(true));

        jdbcTemplate.update("UPDATE collect_state SET mode = 'player' WHERE id = 1");
        mockMvc.perform(get("/api/live/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spyChannelOpen").value(false));
    }

    @Test
    void eliminatedPlayerShouldNotBeCandidate() throws Exception {
        seedCandidates("spy");
        jdbcTemplate.update("UPDATE player_round SET player_status = 'eliminated' WHERE player_id = 2 AND round_id = 1");
        String token = loginAndGetToken("code_c13_eliminated");

        mockMvc.perform(post("/api/suspicion/submit")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"roundId\":1,\"suspectPlayerIds\":[2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(41002));
    }

    private void submitOk(String token, int suspectPlayerId) throws Exception {
        mockMvc.perform(post("/api/suspicion/submit")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"roundId\":1,\"suspectPlayerIds\":[" + suspectPlayerId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void seedCandidates(String mode) {
        insertRound(1, "active");
        insertTeam(10, "A组");
        insertPlayer(1, 3, "林夏");
        insertPlayer(2, 5, "陈微");
        insertPlayerRound(1, 1, 10);
        insertPlayerRound(2, 1, 10);
        jdbcTemplate.update("UPDATE player_round SET is_spy = 1 WHERE player_id = 2 AND round_id = 1");
        jdbcTemplate.update("""
                INSERT INTO collect_state (id, mode, target_id, round_id, updated_by, updated_at)
                VALUES (1, ?, 2, 1, 'operator_c13', ?)
                """, mode, LocalDateTime.now());
    }
}
