package com.redface.service;

import com.redface.config.AppConstants;
import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.PopularityChangeResult;
import com.redface.dto.ScoreResult;
import com.redface.entity.CollectState;
import com.redface.entity.PopularityLedgerEntity;
import com.redface.mapper.PopularityLedgerMapper;
import com.redface.mapper.StatsMapper;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 人气值计算引擎 —— 全系统唯一的人气值变更入口。
 * 铁律:任何其他类都不准直接写 popularity_ledger 或各 stats 表。
 */
@Service
public class PopularityService {

    private static final String TARGET_PLAYER = "player";
    private static final String TARGET_TEAM = "team";
    private static final String TARGET_SPY = "spy";
    private static final String TARGET_POOL = "pool";
    private static final String SOURCE_LIKE = "like";
    private static final String SOURCE_COMMENT = "comment";
    private static final String SOURCE_MANUAL = "manual";
    private static final String SOURCE_REFUND = "refund";
    private static final String SOURCE_GROUP_VOTE = "group_vote";

    private final PopularityLedgerMapper ledgerMapper;
    private final StatsMapper statsMapper;
    private final CollectStateService collectStateService;

    public PopularityService(PopularityLedgerMapper ledgerMapper,
                             StatsMapper statsMapper,
                             CollectStateService collectStateService) {
        this.ledgerMapper = ledgerMapper;
        this.statsMapper = statsMapper;
        this.collectStateService = collectStateService;
    }

    /**
     * 唯一的人气值变更入口。固定五步,不增不减:
     * 1.换算 2.归属判定 3.写流水(幂等) 4.更新统计 5.返回
     *
     * @param req 人气值变更请求
     * @return 人气值变更结果
     */
    @Transactional
    public PopularityChangeResult applyChange(PopularityChangeRequest req) {
        validateRequest(req);

        // === 第1步:换算 ===
        long popularityValue = convert(req.getSource(), req.getRawValue());

        // === 第2步:归属判定 ===
        // TODO: req里指定了targetType就用指定的;
        //       like/comment类(req.targetType==null) → 查collectStateService.getCurrent()
        ResolvedTarget target = resolveTarget(req);

        // === 第3步:写流水(幂等) ===
        try {
            ledgerMapper.insert(buildLedger(req, target, popularityValue));
        } catch (DuplicateKeyException e) {
            return PopularityChangeResult.duplicated();
        }

        // === 第4步:更新统计表 ===
        // TODO: 按target_type更新对应stats表
        //       必须用 UPDATE ... SET x = x + ? 的累加写法
        //       禁止先SELECT再SET(并发不安全)
        updateStats(target, popularityValue);

        // === 第5步:返回 ===
        return PopularityChangeResult.success(popularityValue, target.targetType(), target.targetId(), target.roundId());
    }

    /** 换算逻辑(已写好,不要改) */
    private long convert(String source, long rawValue) {
        switch (source) {
            case "gift":    return rawValue * AppConstants.POPULARITY_PER_DOUBI;
            case "like":    return rawValue * AppConstants.POPULARITY_PER_LIKE;
            case "comment": return rawValue * AppConstants.POPULARITY_PER_COMMENT;
            case "token":
            case "manual":
            case "refund":
            case "team_distribution":
                return rawValue; // 这些来源直接就是人气值
            case "group_vote":
                return rawValue; // C20-3: 群投票得票直接记票数，1票=1，不做换算
            default:
                throw new IllegalArgumentException("未知source: " + source);
        }
    }

    /**
     * 积分计算(只读)。
     * 公式: 积分 = 衰减后人气值 × 系数 / 100
     * 衰减: 本轮超过(上轮×1.5)的部分按0.1计
     * 边界: 上轮为0或首轮→不衰减; 恰好等于阈值→不衰减; 结果为负→返回0
     *
     * @param playerId 选手 ID
     * @param roundId  轮次 ID
     * @return 积分计算结果
     */
    public ScoreResult computeScore(int playerId, int roundId) {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId必须为正数");
        }
        if (roundId <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }

        long currentPop = valueOrZero(statsMapper.findPlayerIndividualPopularity(playerId, roundId));
        long lastRoundPop = valueOrZero(statsMapper.findPreviousRoundIndividualPopularity(playerId, roundId));
        int coefficient = coefficientOrBase(statsMapper.findPlayerCoefficient(playerId, roundId));

        long decayed = currentPop;
        boolean decayApplied = false;
        long threshold = lastRoundPop * AppConstants.DECAY_THRESHOLD_RATIO / AppConstants.PERCENT_BASE;
        if (lastRoundPop > 0 && currentPop > threshold) {
            decayed = threshold + (currentPop - threshold) * AppConstants.DECAY_RATE / AppConstants.PERCENT_BASE;
            decayApplied = true;
        }

        long finalScore = decayed * coefficient / AppConstants.COEFFICIENT_BASE;
        if (finalScore < 0) {
            finalScore = 0;
        }
        return new ScoreResult(currentPop, coefficient, decayed, finalScore, decayApplied);
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private int coefficientOrBase(Integer coefficient) {
        return coefficient == null ? AppConstants.COEFFICIENT_BASE : coefficient;
    }

    private void validateRequest(PopularityChangeRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("req不能为空");
        }
        if (!StringUtils.hasText(req.getSource())) {
            throw new IllegalArgumentException("source不能为空");
        }
        if (req.getRawValue() == 0) {
            throw new IllegalArgumentException("rawValue不能为0");
        }
        boolean negativeAllowed = SOURCE_MANUAL.equals(req.getSource()) || SOURCE_REFUND.equals(req.getSource())
                || SOURCE_GROUP_VOTE.equals(req.getSource()); // C20-3: 群投票录负数冲销，复用manual冲销语义
        if (!negativeAllowed && req.getRawValue() < 0) {
            throw new IllegalArgumentException("该source的rawValue必须为正数: " + req.getSource());
        }
        if (!StringUtils.hasText(req.getIdempotencyKey())) {
            throw new IllegalArgumentException("idempotencyKey不能为空");
        }
    }

    private ResolvedTarget resolveTarget(PopularityChangeRequest req) {
        if (StringUtils.hasText(req.getTargetType())) {
            Integer roundId = requireRoundId(req.getRoundId());
            if (!TARGET_POOL.equals(req.getTargetType()) && req.getTargetId() == null) {
                throw new IllegalArgumentException("targetId不能为空");
            }
            return new ResolvedTarget(req.getTargetType(), req.getTargetId(), roundId);
        }
        if (!SOURCE_LIKE.equals(req.getSource()) && !SOURCE_COMMENT.equals(req.getSource())) {
            throw new IllegalArgumentException("只有like/comment允许通过场控状态自动归属");
        }
        CollectState current = collectStateService.getCurrent();
        if (current == null) {
            throw new IllegalStateException("当前场控状态未设置");
        }
        if (!StringUtils.hasText(current.getMode())) {
            throw new IllegalStateException("当前场控mode为空");
        }
        if (!TARGET_POOL.equals(current.getMode()) && !TARGET_SPY.equals(current.getMode()) && current.getTargetId() == null) {
            throw new IllegalStateException("当前场控targetId为空");
        }
        Integer roundId = req.getRoundId() == null ? current.getRoundId() : req.getRoundId();
        
        // 归属语义对齐：spy+null 记入公共池；spy+targetId 记给该选手
        String resolvedType = current.getMode();
        if (TARGET_SPY.equals(current.getMode()) && current.getTargetId() == null) {
            resolvedType = TARGET_POOL;
        }
        return new ResolvedTarget(resolvedType, current.getTargetId(), requireRoundId(roundId));
    }

    private Integer requireRoundId(Integer roundId) {
        if (roundId == null) {
            throw new IllegalArgumentException("roundId不能为空");
        }
        return roundId;
    }

    private PopularityLedgerEntity buildLedger(PopularityChangeRequest req, ResolvedTarget target, long popularityValue) {
        PopularityLedgerEntity ledger = new PopularityLedgerEntity();
        ledger.setTargetType(target.targetType());
        ledger.setTargetId(target.targetId());
        ledger.setSource(req.getSource());
        ledger.setRawValue(req.getRawValue());
        ledger.setPopularityValue(popularityValue);
        ledger.setRoundId(target.roundId());
        ledger.setIdempotencyKey(req.getIdempotencyKey());
        ledger.setDistributionBatchId(req.getDistributionBatchId());
        ledger.setOperatorId(req.getOperatorId());
        ledger.setReason(req.getReason());
        ledger.setMetadata(req.getMetadata());
        ledger.setOccurredAt(req.getOccurredAt() == null ? LocalDateTime.now() : req.getOccurredAt());
        return ledger;
    }

    private void updateStats(ResolvedTarget target, long popularityValue) {
        switch (target.targetType()) {
            case TARGET_PLAYER -> updatePlayerStats(target, popularityValue);
            case TARGET_SPY -> updateSpyStats(target, popularityValue);
            case TARGET_TEAM -> updateTeamStats(target, popularityValue);
            case TARGET_POOL -> updatePoolStats(target, popularityValue);
            default -> throw new IllegalArgumentException("未知targetType: " + target.targetType());
        }
    }

    private void updatePlayerStats(ResolvedTarget target, long popularityValue) {
        int playerId = requireTargetId(target);
        statsMapper.ensurePlayerRoundStats(playerId, target.roundId());
        int updatedRows = statsMapper.incrementPlayerIndividualPopularity(playerId, target.roundId(), popularityValue);
        if (updatedRows != 1) {
            throw new IllegalStateException("更新player_round_stats.individual_popularity失败");
        }
    }

    private void updateSpyStats(ResolvedTarget target, long popularityValue) {
        int playerId = requireTargetId(target);
        statsMapper.ensurePlayerRoundStats(playerId, target.roundId());
        int updatedRows = statsMapper.incrementPlayerSpyPopularity(playerId, target.roundId(), popularityValue);
        if (updatedRows != 1) {
            throw new IllegalStateException("更新player_round_stats.spy_popularity失败");
        }
    }

    private void updateTeamStats(ResolvedTarget target, long popularityValue) {
        int teamId = requireTargetId(target);
        statsMapper.ensureTeamRoundStats(teamId, target.roundId());
        int updatedRows = statsMapper.incrementTeamPopularity(teamId, target.roundId(), popularityValue);
        if (updatedRows != 1) {
            throw new IllegalStateException("更新team_round_stats.team_popularity失败");
        }
    }

    private void updatePoolStats(ResolvedTarget target, long popularityValue) {
        statsMapper.ensurePoolRoundStats(target.roundId());
        int updatedRows = statsMapper.incrementPoolPopularity(target.roundId(), popularityValue);
        if (updatedRows != 1) {
            throw new IllegalStateException("更新pool_round_stats.pool_popularity失败");
        }
    }

    private int requireTargetId(ResolvedTarget target) {
        if (target.targetId() == null) {
            throw new IllegalArgumentException("targetId不能为空");
        }
        return target.targetId();
    }

    private record ResolvedTarget(String targetType, Integer targetId, int roundId) {
    }
}
