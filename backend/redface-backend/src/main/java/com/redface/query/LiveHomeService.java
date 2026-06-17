package com.redface.query;

import com.redface.dto.LiveHomeResponse;
import com.redface.entity.CollectState;
import com.redface.mapper.C9QueryMapper;
import com.redface.mapper.StatsMapper;
import com.redface.service.CollectStateService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/**
 * API-1 首页直播状态只读聚合服务。
 */
@Service
public class LiveHomeService {
    private static final String MODE_NONE = "none";
    private static final String MODE_PLAYER = "player";
    private static final String MODE_TEAM = "team";
    private static final String MODE_SPY = "spy";
    private static final String MODE_POOL = "pool";

    private final C9QueryMapper c9QueryMapper;
    private final StatsMapper statsMapper;
    private final CollectStateService collectStateService;

    public LiveHomeService(C9QueryMapper c9QueryMapper,
                           StatsMapper statsMapper,
                           CollectStateService collectStateService) {
        this.c9QueryMapper = c9QueryMapper;
        this.statsMapper = statsMapper;
        this.collectStateService = collectStateService;
    }

    public LiveHomeResponse getHome() {
        RoundSummary activeRound = c9QueryMapper.findLatestActiveRound();
        if (activeRound == null) {
            return idleResponse();
        }

        CollectState state = collectStateService.getCurrent();
        LiveHomeResponse response = new LiveHomeResponse();
        response.setLiveStatus("live");
        response.setRoundId(activeRound.getRoundId());
        response.setRoundName(activeRound.getRoundName());
        response.setSpyChannelOpen(isSpyChannelOpen(state, activeRound.getRoundId()));
        response.setUpdatedAt(toEpochSeconds(state == null ? LocalDateTime.now() : state.getUpdatedAt()));

        if (state == null || state.getMode() == null || state.getTargetId() == null && !MODE_POOL.equals(state.getMode())) {
            response.setCurrentMode(MODE_NONE);
            response.setTargetDisplayName(null);
            return response;
        }

        String mode = state.getMode();
        int statsRoundId = state.getRoundId() == null ? activeRound.getRoundId() : state.getRoundId();
        response.setCurrentMode(mode);
        if (MODE_PLAYER.equals(mode) || MODE_SPY.equals(mode)) {
            fillPlayerTarget(response, state.getTargetId(), statsRoundId, MODE_SPY.equals(mode));
        } else if (MODE_TEAM.equals(mode)) {
            fillTeamTarget(response, state.getTargetId(), statsRoundId);
        } else if (MODE_POOL.equals(mode)) {
            response.setTargetDisplayName("赛事总池");
            response.setTargetPopularity(valueOrZero(statsMapper.findPoolPopularity(statsRoundId)));
        } else {
            response.setCurrentMode(MODE_NONE);
        }
        return response;
    }

    private boolean isSpyChannelOpen(CollectState state, int activeRoundId) {
        return state != null
                && MODE_SPY.equals(state.getMode())
                && (state.getRoundId() == null || state.getRoundId().equals(activeRoundId));
    }

    private LiveHomeResponse idleResponse() {
        LiveHomeResponse response = new LiveHomeResponse();
        response.setLiveStatus("idle");
        response.setCurrentMode(MODE_NONE);
        response.setSpyChannelOpen(false);
        response.setUpdatedAt(toEpochSeconds(LocalDateTime.now()));
        return response;
    }

    private void fillPlayerTarget(LiveHomeResponse response, int playerId, int roundId, boolean spyMode) {
        PlayerDisplayRow player = c9QueryMapper.findPlayerDisplay(playerId, roundId);
        if (player == null) {
            response.setTargetDisplayName(null);
            response.setTargetPopularity(0L);
            return;
        }
        response.setTargetDisplayName(player.getNumber() + "号 " + player.getName()
                + (player.getTeamName() == null ? "" : " " + player.getTeamName()));
        response.setTargetPopularity(spyMode
                ? valueOrZero(statsMapper.findPlayerSpyPopularity(playerId, roundId))
                : valueOrZero(statsMapper.findPlayerIndividualPopularity(playerId, roundId)));
        response.setTeamDisplayName(player.getTeamName());
        response.setTeamPopularity(player.getTeamId() == null ? 0L : valueOrZero(statsMapper.findTeamPopularity(player.getTeamId(), roundId)));
    }

    private void fillTeamTarget(LiveHomeResponse response, int teamId, int roundId) {
        String teamName = c9QueryMapper.findTeamName(teamId);
        response.setTargetDisplayName(teamName);
        response.setTargetPopularity(valueOrZero(statsMapper.findTeamPopularity(teamId, roundId)));
        response.setTeamDisplayName(teamName);
        response.setTeamPopularity(valueOrZero(statsMapper.findTeamPopularity(teamId, roundId)));
    }

    private long toEpochSeconds(LocalDateTime time) {
        LocalDateTime safeTime = time == null ? LocalDateTime.now() : time;
        return safeTime.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
