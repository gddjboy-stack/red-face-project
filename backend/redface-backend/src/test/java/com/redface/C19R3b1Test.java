package com.redface;

import com.redface.dto.AdminRequests;
import com.redface.dto.PopularityChangeRequest;
import com.redface.query.LiveHomeService;
import com.redface.service.CollectStateService;
import com.redface.service.PopularityService;
import com.redface.mapper.StatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
public class C19R3b1Test extends C9MockMvcSupport {

    @Autowired
    private CollectStateService collectStateService;

    @Autowired
    private LiveHomeService liveHomeService;

    @Autowired
    private PopularityService popularityService;

    @Autowired
    private StatsMapper statsMapper;

    @BeforeEach
    void setUpData() {
        clearTables();
        insertRound(1, "Round 1");
        jdbcTemplate.update("UPDATE rounds SET status = 'active' WHERE round_id = 1");
        insertTeam(10, "Team A");
        insertPlayer(100, 100, "Player 100");
        insertPlayerRound(100, 1, 10);
        jdbcTemplate.update("UPDATE player_round SET is_spy = true WHERE player_id = 100 AND round_id = 1");
        insertPlayerStats(100, 1, 0L, 0L);
        statsMapper.ensurePoolRoundStats(1);
    }

    @Test
    void spyWithNullTargetShouldBeAcceptedAndReflectedInLiveHome() {
        AdminRequests.CollectStateRequest req = new AdminRequests.CollectStateRequest();
        req.setMode("spy");
        req.setTargetId(null);
        req.setRoundId(1);
        req.setOperatorId("test_op");
        collectStateService.setCollectTarget(req.getMode(), req.getTargetId(), req.getRoundId(), req.getOperatorId());

        var home = liveHomeService.getHome();
        assertThat(home.getCurrentMode()).isEqualTo("spy");
        assertThat(home.isSpyChannelOpen()).isTrue();
        assertThat(home.getTargetDisplayName()).isEqualTo("卧底识破进行中");
        assertThat(home.getTargetPopularity()).isEqualTo(0L);
        assertThat(home.getTargetId()).isNull();
    }

    @Test
    void spyWithNullTargetShouldRouteLikeToPool() {
        AdminRequests.CollectStateRequest req = new AdminRequests.CollectStateRequest();
        req.setMode("spy");
        req.setTargetId(null);
        req.setRoundId(1);
        req.setOperatorId("test_op");
        collectStateService.setCollectTarget(req.getMode(), req.getTargetId(), req.getRoundId(), req.getOperatorId());

        PopularityChangeRequest change = new PopularityChangeRequest();
        change.setSource("like");
        change.setRawValue(100);
        change.setIdempotencyKey("like_1");
        change.setOperatorId("test_op");
        popularityService.applyChange(change);

        Long poolPop = statsMapper.findPoolPopularity(1);
        assertThat(poolPop).isGreaterThan(0L); // 100 * PER_LIKE
    }

    @Test
    void spyWithTargetIdShouldRouteLikeToPlayerSpyPopularity() {
        AdminRequests.CollectStateRequest req = new AdminRequests.CollectStateRequest();
        req.setMode("spy");
        req.setTargetId(100);
        req.setRoundId(1);
        req.setOperatorId("test_op");
        collectStateService.setCollectTarget(req.getMode(), req.getTargetId(), req.getRoundId(), req.getOperatorId());

        var home = liveHomeService.getHome();
        assertThat(home.getCurrentMode()).isEqualTo("spy");
        assertThat(home.isSpyChannelOpen()).isTrue();
        assertThat(home.getTargetDisplayName()).contains("Player 100");
        assertThat(home.getTargetId()).isEqualTo(100);

        PopularityChangeRequest change = new PopularityChangeRequest();
        change.setSource("like");
        change.setRawValue(100);
        change.setIdempotencyKey("like_2");
        change.setOperatorId("test_op");
        popularityService.applyChange(change);

        Long spyPop = statsMapper.findPlayerSpyPopularity(100, 1);
        assertThat(spyPop).isGreaterThan(0L); // 100 * PER_LIKE
    }
}
