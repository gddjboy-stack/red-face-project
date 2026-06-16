package com.redface.service;

import com.redface.api.ApiException;
import com.redface.dto.BasicDataRequests;
import com.redface.dto.BasicDataViews;
import com.redface.mapper.BasicDataMapper;
import com.redface.mapper.OperationsLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * C19 基础数据管理服务。只允许写 players、teams、rounds、player_round 四张静态表。
 */
@Service
public class BasicDataService {
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_ELIMINATED = "eliminated";
    private static final String ROUND_UPCOMING = "upcoming";
    private static final String ROUND_ACTIVE = "active";
    private static final String ROUND_COMPLETED = "completed";
    private static final String PLAYER_NORMAL = "normal";
    private static final String PLAYER_FREE = "free";
    private static final String PLAYER_ELIMINATED = "eliminated";

    private final BasicDataMapper basicDataMapper;
    private final OperationsLogMapper operationsLogMapper;

    public BasicDataService(BasicDataMapper basicDataMapper, OperationsLogMapper operationsLogMapper) {
        this.basicDataMapper = basicDataMapper;
        this.operationsLogMapper = operationsLogMapper;
    }

    public List<BasicDataViews.PlayerView> listPlayers() {
        return basicDataMapper.findPlayers();
    }

    @Transactional
    public BasicDataViews.PlayerView createPlayer(BasicDataRequests.CreatePlayerRequest request) {
        validateOperator(request.getOperatorId());
        validateText(request.getName(), "选手姓名不能为空");
        if (request.getNumber() == null || request.getNumber() <= 0) {
            throw new IllegalArgumentException("选手序号必须为正数");
        }
        request.setStatus(normalizePlayerStatus(request.getStatus()));
        try {
            basicDataMapper.insertPlayer(request);
        } catch (DuplicateKeyException e) {
            throw new ApiException(40901, "序号" + request.getNumber() + "已被占用");
        }
        BasicDataViews.PlayerView created = basicDataMapper.findPlayerById(request.getPlayerId());
        writeLog(request.getOperatorId(), "basic_create_player", "player:" + request.getPlayerId(),
                "{\"name\":\"" + escape(request.getName()) + "\",\"number\":" + request.getNumber() + "}", "新增选手");
        return created;
    }

    public List<BasicDataViews.TeamView> listTeams() {
        return basicDataMapper.findTeams();
    }

    @Transactional
    public BasicDataViews.TeamView createTeam(BasicDataRequests.CreateTeamRequest request) {
        validateOperator(request.getOperatorId());
        validateText(request.getName(), "队伍名称不能为空");
        basicDataMapper.insertTeam(request);
        BasicDataViews.TeamView created = basicDataMapper.findTeamById(request.getTeamId());
        writeLog(request.getOperatorId(), "basic_create_team", "team:" + request.getTeamId(),
                "{\"name\":\"" + escape(request.getName()) + "\"}", "新增队伍");
        return created;
    }

    public List<BasicDataViews.RoundView> listRounds() {
        return basicDataMapper.findRounds();
    }

    @Transactional
    public BasicDataViews.RoundView createRound(BasicDataRequests.CreateRoundRequest request) {
        validateOperator(request.getOperatorId());
        validateText(request.getName(), "轮次名称不能为空");
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("startTime和endTime不能为空");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("endTime必须晚于startTime");
        }
        request.setStatus(normalizeRoundStatus(request.getStatus()));
        basicDataMapper.insertRound(request);
        if (ROUND_ACTIVE.equals(request.getStatus())) {
            completeOtherActiveRounds(request.getRoundId(), request.getOperatorId(), "创建active轮次");
        }
        BasicDataViews.RoundView created = basicDataMapper.findRoundById(request.getRoundId());
        writeLog(request.getOperatorId(), "basic_create_round", "round:" + request.getRoundId(),
                "{\"name\":\"" + escape(request.getName()) + "\",\"status\":\"" + request.getStatus() + "\"}", "新增轮次");
        return created;
    }

    @Transactional
    public BasicDataViews.RoundView updateRoundStatus(int roundId, BasicDataRequests.UpdateRoundStatusRequest request) {
        validateOperator(request.getOperatorId());
        String status = normalizeRoundStatus(request.getStatus());
        BasicDataViews.RoundView before = basicDataMapper.findRoundById(roundId);
        if (before == null) {
            throw new IllegalArgumentException("轮次不存在: " + roundId);
        }
        if (ROUND_ACTIVE.equals(status)) {
            completeOtherActiveRounds(roundId, request.getOperatorId(), "切换active轮次");
        }
        int updated = basicDataMapper.updateRoundStatus(roundId, status);
        if (updated != 1) {
            throw new IllegalStateException("轮次状态更新失败");
        }
        writeLog(request.getOperatorId(), "basic_update_round_status", "round:" + roundId,
                "{\"from\":\"" + before.getStatus() + "\",\"to\":\"" + status + "\"}", "切换轮次状态");
        return basicDataMapper.findRoundById(roundId);
    }

    public List<BasicDataViews.PlayerRoundView> listPlayerRounds(int roundId) {
        if (roundId <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        return basicDataMapper.findPlayerRounds(roundId);
    }

    @Transactional
    public BasicDataViews.PlayerRoundView upsertPlayerRound(BasicDataRequests.PlayerRoundRequest request) {
        validateOperator(request.getOperatorId());
        if (request.getPlayerId() == null || request.getPlayerId() <= 0) {
            throw new IllegalArgumentException("playerId必须为正数");
        }
        if (request.getRoundId() == null || request.getRoundId() <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        request.setPlayerStatus(normalizePlayerRoundStatus(request.getPlayerStatus()));
        request.setIsSpy(Boolean.TRUE.equals(request.getIsSpy()));
        int updated = basicDataMapper.upsertPlayerRound(request);
        if (updated <= 0) {
            throw new IllegalStateException("分队信息保存失败");
        }
        writeLog(request.getOperatorId(), "basic_upsert_player_round",
                "player_round:" + request.getPlayerId() + ":" + request.getRoundId(),
                "{\"playerId\":" + request.getPlayerId() + ",\"roundId\":" + request.getRoundId()
                        + ",\"teamId\":" + (request.getTeamId() == null ? "null" : request.getTeamId())
                        + ",\"isSpy\":" + request.getIsSpy() + "}", "分队与卧底设置");
        return basicDataMapper.findPlayerRounds(request.getRoundId()).stream()
                .filter(row -> request.getPlayerId().equals(row.getPlayerId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("分队信息读取失败"));
    }

    private void completeOtherActiveRounds(int keepRoundId, String operatorId, String reason) {
        List<BasicDataViews.RoundView> activeRounds = basicDataMapper.findActiveRounds().stream()
                .filter(round -> !Integer.valueOf(keepRoundId).equals(round.getRoundId()))
                .collect(Collectors.toList());
        int completed = basicDataMapper.completeOtherActiveRounds(keepRoundId);
        if (!activeRounds.isEmpty()) {
            String oldRounds = activeRounds.stream()
                    .map(round -> String.valueOf(round.getRoundId()))
                    .collect(Collectors.joining(","));
            writeLog(operatorId, "basic_auto_complete_active_rounds", "round:" + keepRoundId,
                    "{\"newActiveRoundId\":" + keepRoundId + ",\"completedRoundIds\":[" + oldRounds + "],\"updatedRows\":" + completed + "}", reason);
        }
    }

    private String normalizePlayerStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_ACTIVE;
        }
        String normalized = status.trim().toLowerCase();
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_ELIMINATED.equals(normalized)) {
            throw new IllegalArgumentException("未知选手状态: " + status);
        }
        return normalized;
    }

    private String normalizeRoundStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return ROUND_UPCOMING;
        }
        String normalized = status.trim().toLowerCase();
        if (!ROUND_UPCOMING.equals(normalized) && !ROUND_ACTIVE.equals(normalized) && !ROUND_COMPLETED.equals(normalized)) {
            throw new IllegalArgumentException("未知轮次状态: " + status);
        }
        return normalized;
    }

    private String normalizePlayerRoundStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return PLAYER_NORMAL;
        }
        String normalized = status.trim().toLowerCase();
        if (!PLAYER_NORMAL.equals(normalized) && !PLAYER_FREE.equals(normalized) && !PLAYER_ELIMINATED.equals(normalized)) {
            throw new IllegalArgumentException("未知选手轮次状态: " + status);
        }
        return normalized;
    }

    private void validateOperator(String operatorId) {
        validateText(operatorId, "operatorId不能为空");
    }

    private void validateText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void writeLog(String operatorId, String actionType, String target, String detail, String reason) {
        operationsLogMapper.insert(operatorId, actionType, target, detail, reason);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
