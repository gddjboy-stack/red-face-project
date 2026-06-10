package com.redface.service;

import com.redface.config.AppConstants;
import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.PopularityChangeResult;
import com.redface.dto.ScoreResult;
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
        Target target = resolveTarget(req);

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
        updateStats(target, req.getRoundId(), popularityValue);

        // === 第5步:返回 ===
        return PopularityChangeResult.success(popularityValue, target.targetType(), target.targetId(), req.getRoundId());
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
        // TODO: 严格按上述公式和边界实现
        // 提示: long threshold = lastRoundPop * AppConstants.DECAY_THRESHOLD_RATIO / 100;
        //      if (lastRoundPop > 0 && currentPop > threshold) {
        //          decayed = threshold + (currentPop - threshold) * AppConstants.DECAY_RATE / 100;
        //      }
        return null;
    }

    private void validateRequest(PopularityChangeRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("req不能为空");
        }
        if (!StringUtils.hasText(req.getSource())) {
            throw new IllegalArgumentException("source不能为空");
        }
        if (req.getRawValue() <= 0) {
            throw new IllegalArgumentException("rawValue必须为正数");
        }
        if (req.getRoundId() == null) {
            throw new IllegalArgumentException("roundId不能为空");
        }
        if (!StringUtils.hasText(req.getIdempotencyKey())) {
            throw new IllegalArgumentException("idempotencyKey不能为空");
        }
    }

    private Target resolveTarget(PopularityChangeRequest req) {
        if (StringUtils.hasText(req.getTargetType())) {
            if (req.getTargetId() == null) {
                throw new IllegalArgumentException("targetId不能为空");
            }
            return new Target(req.getTargetType(), req.getTargetId());
        }
        collectStateService.getCurrent();
        throw new UnsupportedOperationException("C2仅实现player直接归属; like/comment场控归属将在C3实现");
    }

    private PopularityLedgerEntity buildLedger(PopularityChangeRequest req, Target target, long popularityValue) {
        PopularityLedgerEntity ledger = new PopularityLedgerEntity();
        ledger.setTargetType(target.targetType());
        ledger.setTargetId(target.targetId());
        ledger.setSource(req.getSource());
        ledger.setRawValue(req.getRawValue());
        ledger.setPopularityValue(popularityValue);
        ledger.setRoundId(req.getRoundId());
        ledger.setIdempotencyKey(req.getIdempotencyKey());
        ledger.setDistributionBatchId(req.getDistributionBatchId());
        ledger.setOperatorId(req.getOperatorId());
        ledger.setReason(req.getReason());
        ledger.setMetadata(req.getMetadata());
        ledger.setOccurredAt(req.getOccurredAt() == null ? LocalDateTime.now() : req.getOccurredAt());
        return ledger;
    }

    private void updateStats(Target target, int roundId, long popularityValue) {
        if (!TARGET_PLAYER.equals(target.targetType())) {
            throw new UnsupportedOperationException("C2仅实现player直接归属统计更新");
        }
        statsMapper.ensurePlayerRoundStats(target.targetId(), roundId);
        int updatedRows = statsMapper.incrementPlayerIndividualPopularity(target.targetId(), roundId, popularityValue);
        if (updatedRows != 1) {
            throw new IllegalStateException("更新player_round_stats失败");
        }
    }

    private record Target(String targetType, int targetId) {
    }
}
