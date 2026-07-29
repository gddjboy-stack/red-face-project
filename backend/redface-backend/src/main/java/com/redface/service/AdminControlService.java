package com.redface.service;

import com.redface.dto.AdminOperationResult;
import com.redface.dto.AdminRequests;
import com.redface.dto.DistributionResult;
import com.redface.dto.GroupVoteSummaryItem;
import com.redface.dto.GroupVoteSummaryResponse;
import com.redface.dto.LiveHomeResponse;
import com.redface.dto.PopularityBoardResponse;
import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.PopularityChangeResult;
import com.redface.dto.SimResult;
import com.redface.entity.CollectState;
import com.redface.mapper.OperationsLogMapper;
import com.redface.mapper.PopularityLedgerMapper;
import com.redface.query.LiveHomeService;
import com.redface.query.PopularityBoardService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * C10 场控后台服务。只组合调用既有业务 Service，不直接写人气、统计或系数表。
 */
@Service
public class AdminControlService {
    private static final String SOURCE_MANUAL = "manual";
    private static final String SOURCE_GROUP_VOTE = "group_vote";
    private static final String TARGET_SPY = "spy";
    private static final String METHOD_EQUAL = "equal";

    private final CollectStateService collectStateService;
    private final LiveDataService liveDataService;
    private final PopularityService popularityService;
    private final TeamDistributionService teamDistributionService;
    private final LiveHomeService liveHomeService;
    private final PopularityBoardService popularityBoardService;
    private final OperationsLogMapper operationsLogMapper;
    private final PopularityLedgerMapper popularityLedgerMapper;

    public AdminControlService(CollectStateService collectStateService,
                               LiveDataService liveDataService,
                               PopularityService popularityService,
                               TeamDistributionService teamDistributionService,
                               LiveHomeService liveHomeService,
                               PopularityBoardService popularityBoardService,
                               OperationsLogMapper operationsLogMapper,
                               PopularityLedgerMapper popularityLedgerMapper) {
        this.collectStateService = collectStateService;
        this.liveDataService = liveDataService;
        this.popularityService = popularityService;
        this.teamDistributionService = teamDistributionService;
        this.liveHomeService = liveHomeService;
        this.popularityBoardService = popularityBoardService;
        this.operationsLogMapper = operationsLogMapper;
        this.popularityLedgerMapper = popularityLedgerMapper;
    }

    public LiveHomeResponse getLiveHome() {
        return liveHomeService.getHome();
    }

    public PopularityBoardResponse getBoard(String tab, int roundId) {
        return popularityBoardService.getBoard(tab, roundId);
    }

    public CollectState getCollectState() {
        return collectStateService.getCurrent();
    }

    @Transactional
    public AdminOperationResult<Void> setCollectTarget(AdminRequests.CollectStateRequest request) {
        validateOperator(request.getOperatorId());
        collectStateService.setCollectTarget(request.getMode(), request.getTargetId(), request.getRoundId(), request.getOperatorId());
        return AdminOperationResult.of("set_collect_target", "场控目标已切换", null);
    }

    @Transactional
    public AdminOperationResult<SimResult> simulateInject(AdminRequests.SimulateInjectRequest request) {
        validateOperator(request.getOperatorId());
        SimResult result = liveDataService.simulateInject(request.getEventType(), request.getValue(), request.getTargetId(), request.getOperatorId());
        operationsLogMapper.insert(request.getOperatorId(), "simulate_inject", request.getEventType(),
                "{\"eventType\":\"" + safe(request.getEventType()) + "\",\"value\":" + request.getValue()
                        + ",\"targetId\":" + (request.getTargetId() == null ? "null" : request.getTargetId()) + "}",
                "模拟注入");
        return AdminOperationResult.of("simulate_inject", "模拟注入成功", result);
    }

    @Transactional
    public AdminOperationResult<PopularityChangeResult> manualAdjust(AdminRequests.ManualAdjustRequest request) {
        validateOperator(request.getOperatorId());
        validateText(request.getTargetType(), "targetType不能为空");
        validateText(request.getReason(), "reason不能为空");
        if (request.getRoundId() == null || request.getRoundId() <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        if (request.getRawValue() == 0) {
            throw new IllegalArgumentException("rawValue不能为0");
        }
        PopularityChangeRequest changeRequest = new PopularityChangeRequest();
        changeRequest.setTargetType(request.getTargetType());
        changeRequest.setTargetId(request.getTargetId());
        changeRequest.setRoundId(request.getRoundId());
        changeRequest.setSource(SOURCE_MANUAL);
        changeRequest.setRawValue(request.getRawValue());
        changeRequest.setOperatorId(request.getOperatorId());
        changeRequest.setReason(request.getReason());
        changeRequest.setOccurredAt(LocalDateTime.now());
        changeRequest.setIdempotencyKey(generateManualIdempotencyKey(request.getOperatorId()));
        PopularityChangeResult result = popularityService.applyChange(changeRequest);
        operationsLogMapper.insert(request.getOperatorId(), "manual_adjust", request.getTargetType() + ":" + request.getTargetId(),
                "{\"targetType\":\"" + safe(request.getTargetType()) + "\",\"targetId\":" + request.getTargetId()
                        + ",\"roundId\":" + request.getRoundId() + ",\"rawValue\":" + request.getRawValue() + "}",
                request.getReason());
        return AdminOperationResult.of("manual_adjust", "手动调分成功", result);
    }

    @Transactional
    public AdminOperationResult<DistributionResult> distributeTeam(AdminRequests.TeamDistributionRequest request) {
        validateOperator(request.getOperatorId());
        String method = StringUtils.hasText(request.getMethod()) ? request.getMethod().trim().toLowerCase() : METHOD_EQUAL;
        validateText(request.getReason(), "reason不能为空");
        DistributionResult result = teamDistributionService.distribute(request.getTeamId(), request.getRoundId(), method,
                request.getCustomWeights(), request.getOperatorId(), request.getReason());
        operationsLogMapper.insert(request.getOperatorId(), "team_distribution", "team:" + request.getTeamId(),
                "{\"teamId\":" + request.getTeamId() + ",\"roundId\":" + request.getRoundId() + ",\"method\":\"" + safe(method) + "\"}",
                request.getReason());
        return AdminOperationResult.of("team_distribution", "团队人气分配成功", result);
    }

    /**
     * C20-3 群投票结果录入。同轮同选手可多次录入累加；负数为冲销（复用 manual 冲销语义）；
     * 幂等键由前端生成，重复提交返回 duplicated，防连点。计入 spy_popularity（不影响个人积分口径）。
     *
     * @param request 群投票录入请求
     * @return 录入结果（含本轮该选手录入后的累计票数）
     */
    @Transactional
    public AdminOperationResult<GroupVoteEntryOutcome> recordGroupVote(AdminRequests.GroupVoteEntryRequest request) {
        validateOperator(request.getOperatorId());
        validateText(request.getReason(), "reason不能为空");
        validateText(request.getIdempotencyKey(), "idempotencyKey不能为空（防连点，由前端生成）");
        if (request.getRoundId() == null || request.getRoundId() <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        if (request.getPlayerId() == null || request.getPlayerId() <= 0) {
            throw new IllegalArgumentException("playerId必须为正数");
        }
        if (request.getVotes() == null || request.getVotes() == 0) {
            throw new IllegalArgumentException("votes不能为0（正数累加，负数冲销）");
        }

        PopularityChangeRequest changeRequest = new PopularityChangeRequest();
        changeRequest.setTargetType(TARGET_SPY);
        changeRequest.setTargetId(request.getPlayerId());
        changeRequest.setRoundId(request.getRoundId());
        changeRequest.setSource(SOURCE_GROUP_VOTE);
        changeRequest.setRawValue(request.getVotes());
        changeRequest.setOperatorId(request.getOperatorId());
        changeRequest.setReason(request.getReason());
        changeRequest.setMetadata("{\"channel\":\"fan_group_vote\"}");
        changeRequest.setOccurredAt(LocalDateTime.now());
        changeRequest.setIdempotencyKey("gv_" + request.getIdempotencyKey());
        PopularityChangeResult result = popularityService.applyChange(changeRequest);

        boolean duplicated = result.isDuplicated();
        if (!duplicated) {
            operationsLogMapper.insert(request.getOperatorId(), "group_vote_entry",
                    "player:" + request.getPlayerId(),
                    "{\"roundId\":" + request.getRoundId() + ",\"playerId\":" + request.getPlayerId()
                            + ",\"votes\":" + request.getVotes()
                            + ",\"idempotencyKey\":\"gv_" + safe(request.getIdempotencyKey()) + "\"}",
                    request.getReason());
        }

        long currentTotal = popularityLedgerMapper.summarizeBySource(request.getRoundId(), SOURCE_GROUP_VOTE).stream()
                .filter(item -> request.getPlayerId().equals(item.getPlayerId()))
                .mapToLong(GroupVoteSummaryItem::getTotalVotes)
                .findFirst()
                .orElse(0L);
        GroupVoteEntryOutcome outcome = new GroupVoteEntryOutcome(duplicated, request.getVotes(), currentTotal);
        String message = duplicated ? "重复提交已拦截（幂等），未重复记账" : "群投票录入成功";
        return AdminOperationResult.of("group_vote_entry", message, outcome);
    }

    /**
     * C20-3 查询指定轮次各选手群投票累计票数（冲销后净值）。
     *
     * @param roundId 轮次 ID
     * @return 汇总响应
     */
    public GroupVoteSummaryResponse getGroupVoteSummary(int roundId) {
        if (roundId <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        java.util.List<GroupVoteSummaryItem> items = popularityLedgerMapper.summarizeBySource(roundId, SOURCE_GROUP_VOTE);
        long total = items.stream().mapToLong(GroupVoteSummaryItem::getTotalVotes).sum();
        return new GroupVoteSummaryResponse(roundId, total, items);
    }

    /**
     * C20-3 群投票录入结果：是否幂等拦截、本次增量、该选手当前累计票数。
     */
    public record GroupVoteEntryOutcome(boolean duplicated, long votesApplied, long currentTotalVotes) {
    }

    private String generateManualIdempotencyKey(String operatorId) {
        return "manual_" + System.currentTimeMillis() + "_" + operatorId + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void validateOperator(String operatorId) {
        validateText(operatorId, "operatorId不能为空");
    }

    private void validateText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
