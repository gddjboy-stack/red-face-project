package com.redface;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * C9 API-2 人气看板 Controller 测试。排序合规是本测试的重点。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PopularityBoardControllerC9Test extends C9MockMvcSupport {

    @BeforeEach
    void setUp() {
        clearTables();
        insertRound(1, "active");
        insertTeam(10, "A组");
        insertTeam(20, "B组");
        insertPlayer(1, 1, "陈微");
        insertPlayer(2, 2, "赵雨");
        insertPlayer(3, 3, "林夏");
        insertPlayerRound(1, 1, 10);
        insertPlayerRound(2, 1, 10);
        insertPlayerRound(3, 1, 20);
        insertPlayerStats(1, 1, 100L, 10L);
        insertPlayerStats(2, 1, 500L, 50L);
        insertPlayerStats(3, 1, 999999L, 90L);
        insertTeamStats(10, 1, 2000L);
        insertTeamStats(20, 1, 999999L);
    }

    @Test
    void playerBoardShouldSortByNumberAscEvenWhenThirdHasHighestPopularity() throws Exception {
        mockMvc.perform(get("/api/popularity/board").param("tab", "player").param("roundId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tab").value("player"))
                .andExpect(jsonPath("$.data.items[0].number").value(1))
                .andExpect(jsonPath("$.data.items[1].number").value(2))
                .andExpect(jsonPath("$.data.items[2].number").value(3))
                .andExpect(jsonPath("$.data.items[2].value").value(999999L));
    }

    @Test
    void teamBoardShouldSortByTeamIdAscNotPopularityValue() throws Exception {
        mockMvc.perform(get("/api/popularity/board").param("tab", "team").param("roundId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tab").value("team"))
                .andExpect(jsonPath("$.data.items[0].number").value(10))
                .andExpect(jsonPath("$.data.items[1].number").value(20));
    }

    @Test
    void spyBoardShouldReturnDisabledFlagAndNumberAscItems() throws Exception {
        mockMvc.perform(get("/api/popularity/board").param("tab", "spy").param("roundId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tab").value("spy"))
                .andExpect(jsonPath("$.data.spyTabEnabled").value(false))
                .andExpect(jsonPath("$.data.items[0].number").value(1))
                .andExpect(jsonPath("$.data.items[1].number").value(2))
                .andExpect(jsonPath("$.data.items[2].number").value(3));
    }
}
