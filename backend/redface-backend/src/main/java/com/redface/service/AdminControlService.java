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
import com.redface.mapper.GroupVoteLedgerMapper;
import com.redface.mapper.OperationsLogMapper;
import com.redface.mapper.PopularityLedgerMapper;
import com.redface.mapper.SpyCoefficientLedgerMapper;
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
    private static final String METHOD_EQUAL = "equal";

    private final CollectStateService collectStateService;
    private final LiveDataService liveDataService;
    private final PopularityService popularityService;
    private final TeamDistributionService teamDistributionService;
    private final LiveHomeService liveHomeService;
    private final PopularityBoardService popularityBoardService;
    private final OperationsLogMapper operationsLogMapper;
    private final PopularityLedgerMapper popularityLedgerMapper;
    private final GroupVoteLedgerMapper groupVoteLedgerMapper;
    private final LiveWatermarkService liveWatermarkService;
    private final VoterCountService voterCountService;
    private final SpyCoefficientLedgerMapper spyCoefficientLedgerMapper;

    public AdminControlService(CollectStateService collectStateService,
                               LiveDataService liveDataService,
                               PopularityService popularityService,
                               TeamDistributionService teamDistributionService,
                               LiveHomeService liveHomeService,
                               PopularityBoardService popularityBoardService,
                               OperationsLogMapper operationsLogMapper,
                               PopularityLedgerMapper popularityLedgerMapper,
                               GroupVoteLedgerMapper groupVoteLedgerMapper,
                               LiveWatermarkService liveWatermarkService,
                               VoterCountService voterCountService,
                               SpyCoefficientLedgerMapper spyCoefficientLedgerMapper) {
        this.collectStateService = collectStateService;
        this.liveDataService = liveDataService;
        this.popularityService = popularityService;
        this.teamDistributionService = teamDistributionService;
        this.liveHomeService = liveHomeService;
        this.popularityBoardService = popularityBoardService;
        this.operationsLogMapper = operationsLogMapper;
        this.popularityLedgerMapper = popularityLedgerMapper;
        this.groupVoteLedgerMapper = groupVoteLedgerMapper;
        this.liveWatermarkService = liveWatermarkService;
        this.voterCountService = voterCountService;
        this.spyCoefficientLedgerMapper = spyCoefficientLedgerMapper;
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
        // 先取提示再切换：该提示描述的是「切换前未录入的那段」的风险。
        // R-2 采用礼物按场控目标归属后，漏做「切换前先录一次数」会直接导致归属错人。
        String warning = liveWatermarkService.buildTargetSwitchWarning();
        collectStateService.setCollectTarget(request.getMode(), request.getTargetId(), request.getRoundId(), request.getOperatorId());
        String message = warning == null ? "场控目标已切换" : "场控目标已切换。注意：" + warning;
        return AdminOperationResult.of("set_collect_target", message, null);
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
     * C20-3-FIX 群投票结果录入。同轮同选手可多次录入累加；负数为冲销；
     * 幂等键由前端生成，重复提交返回 duplicated，防连点。
     * 票数写入独立表 group_vote_ledger，与人气账本物理隔离，只用于卧底胜负判定，不折算人气。
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

        // 幂等：先查后插，避免在 @Transactional 内捕获 DuplicateKeyException 导致事务被标记回滚；
        // 并发窗口仍由表内唯一约束兜底（极端并发下撞到约束冲突则整个请求失败重试，不会重复记账）。
        String idempotencyKey = "gv_" + request.getIdempotencyKey();
        boolean duplicated = groupVoteLedgerMapper.countByIdempotencyKey(idempotencyKey) > 0;
        if (!duplicated) {
            groupVoteLedgerMapper.insert(request.getRoundId(), request.getPlayerId(), request.getVotes(),
                    idempotencyKey, request.getOperatorId(), request.getReason());
        }

        if (!duplicated) {
            operationsLogMapper.insert(request.getOperatorId(), "group_vote_entry",
                    "player:" + request.getPlayerId(),
                    "{\"roundId\":" + request.getRoundId() + ",\"playerId\":" + request.getPlayerId()
                            + ",\"votes\":" + request.getVotes()
                            + ",\"idempotencyKey\":\"gv_" + safe(request.getIdempotencyKey()) + "\"}",
                    request.getReason());
        }

        long currentTotal = groupVoteLedgerMapper.sumVotes(request.getRoundId(), request.getPlayerId());

        // C20-10 票数一侧的参与人数校验。此处<b>只提示不阻断</b>：
        // 票数的正确修复动作是负数冲销而非覆盖，若在这里硬阻断，
        // 一旦参与人数录错（例如少数了一位），本轮所有票都录不进去，现场会直接卡死。
        // 反之若完全不查，得票数超过参与人数的数据会静默流到大屏，出现 120% 的占比。
        String voterCountWarning = voterCountService.checkAgainstVoterCount(
                request.getRoundId(), lookupPlayerName(request.getRoundId(), request.getPlayerId()), currentTotal);

        GroupVoteEntryOutcome outcome = new GroupVoteEntryOutcome(
                duplicated, request.getVotes(), currentTotal, voterCountWarning);
        String message = duplicated ? "重复提交已拦截（幂等），未重复记账" : "群投票录入成功";
        if (voterCountWarning != null) {
            message = message + "；但数据存在冲突需核对";
        }
        return AdminOperationResult.of("group_vote_entry", message, outcome);
    }

    /**
     * C20-3-FIX 查询指定轮次各选手群投票累计票数（冲销后净值），数据源为独立表 group_vote_ledger。
     *
     * @param roundId 轮次 ID
     * @return 汇总响应
     */
    public GroupVoteSummaryResponse getGroupVoteSummary(int roundId) {
        if (roundId <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        java.util.List<GroupVoteSummaryItem> items = groupVoteLedgerMapper.summarize(roundId);
        long total = items.stream().mapToLong(GroupVoteSummaryItem::getTotalVotes).sum();
        Integer voterCount = voterCountService.getVoterCount(roundId);
        fillPercentAndExposed(items, roundId, voterCount);
        GroupVoteSummaryResponse response = new GroupVoteSummaryResponse(roundId, total, items);
        response.setVoterCount(voterCount);
        return response;
    }

    /**
     * C20-10 为汇总项补充得票占比与识破标记。
     *
     * <p>占比分母用<b>参与人数</b>而非票数总和：一人可能投多票或弃票，
     * 用票数总和作分母会得到「所有人占比加起来正好 100%」的假象，掩盖弃票情况。
     *
     * <p>参与人数未录入时占比留 null 而非 0，让界面能显示「——」并提示补录。
     */
    private void fillPercentAndExposed(java.util.List<GroupVoteSummaryItem> items, int roundId, Integer voterCount) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (GroupVoteSummaryItem item : items) {
            if (voterCount != null && voterCount > 0) {
                // 保留一位小数，四舍五入。不取整：4 票/7 人若显示 57% 与 3 票的 43% 相加不为 100，
                // 运营会怀疑数据出错；一位小数足以让差值来源可解释。
                item.setVotePercent(Math.round(item.getTotalVotes() * 1000.0 / voterCount) / 10.0);
            } else if (voterCount != null) {
                // 参与人数为 0：占比无定义，但已录入，故记 0.0 而非 null，与「未录入」区分。
                item.setVotePercent(0.0);
            }
            if (item.getPlayerId() != null) {
                item.setExposed(spyCoefficientLedgerMapper.countActiveByType(
                        item.getPlayerId(), roundId, "exposed_halve") > 0);
            }
        }
    }

    private String lookupPlayerName(int roundId, int playerId) {
        java.util.List<GroupVoteSummaryItem> items = groupVoteLedgerMapper.summarize(roundId);
        for (GroupVoteSummaryItem item : items) {
            if (item.getPlayerId() != null && item.getPlayerId() == playerId) {
                return item.getPlayerNumber() == null
                        ? item.getPlayerName()
                        : item.getPlayerNumber() + "号 " + item.getPlayerName();
            }
        }
        return null;
    }

    /**
     * C20-3 群投票录入结果：是否幂等拦截、本次增量、该选手当前累计票数。
     *
     * <p>C20-10 新增 {@code voterCountWarning}：非 null 表示票数已入账但与参与人数矛盾，
     * 前端必须显著提示。<b>不可因为有警告就把本次录入当成失败</b>——票已经在账上了，
     * 若提示成「录入失败」，运营会再录一次，造成双倍票数。
     */
    public record GroupVoteEntryOutcome(boolean duplicated, long votesApplied, long currentTotalVotes,
                                        String voterCountWarning) {
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
