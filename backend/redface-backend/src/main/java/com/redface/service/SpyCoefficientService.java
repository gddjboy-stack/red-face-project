package com.redface.service;

import com.redface.dto.SpyCoefficientLedgerItem;
import com.redface.dto.SpyCoefficientResult;
import com.redface.mapper.BasicDataMapper;
import com.redface.mapper.OperationsLogMapper;
import com.redface.mapper.SpyCoefficientLedgerMapper;
import com.redface.mapper.StatsMapper;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * C20-10 卧底人气系数服务。
 *
 * <p><b>本服务与 {@link CoefficientService} 语义不同，不可互相套用：</b>
 * CoefficientService 管的是个人人气系数 {@code player_round_stats.coefficient}，
 * 用<b>加法 delta</b> 累加；本服务管的是卧底人气系数 {@code spy_coefficient}，
 * 用<b>乘法 factor</b> 施加。
 *
 * <p>为什么必须用乘法：规则是「任务加成 +30% 后再被识破减半」。乘法得
 * 100 → 130 → 65（×0.65）；若沿用加法得 100 + 30 - 50 = 80（×0.8）。
 * 以基础卧底人气 205000 计，两者相差 30750 人气，且两个数都不会报错，
 * 运营无从判断哪个是对的。这是刻意不镜像团队版实现的唯一原因。
 *
 * <p>系数与账本的关系：{@code player_round_stats.spy_coefficient} 是缓存值，
 * {@code spy_coefficient_ledger} 中未撤销条目的乘积才是真相。撤销时不做除法回退
 * （整数除法会累积误差，130×50/100=65，65×100/50=130 看似可逆，但
 * 100×130/100=130、130×33/100=42、42×100/33=127 就已经回不去了），
 * 而是从 100 起按剩余条目重新乘一遍。
 */
@Service
public class SpyCoefficientService {

    /** 任务加成，同轮同选手可多次施加。 */
    public static final String FACTOR_TYPE_TASK_BONUS = "task_bonus";
    /** 识破减半，同轮同选手只允许一次有效记录。 */
    public static final String FACTOR_TYPE_EXPOSED_HALVE = "exposed_halve";

    /** 系数基准值：100 表示 ×1.0。 */
    private static final int COEFFICIENT_BASE = 100;
    /** 识破减半的固定因子：50 表示 ×0.5。 */
    private static final int FACTOR_HALVE = 50;
    /**
     * 系数下限。防的是多次减半后系数被整数除法压到 0：一旦为 0，
     * 该选手卧底人气恒为 0 且任何后续加成都乘不回来（0×1.3=0），
     * 表现为「加了分但数字不动」，现场无法排查。
     */
    private static final int COEFFICIENT_FLOOR = 1;
    /**
     * 系数上限：1000 表示 ×10。留痕上限而非无限，防止误把 130 当成「130 倍」
     * 连点数次后卧底人气冲到天文数字，污染整轮榜单。
     */
    private static final int COEFFICIENT_CEILING = 1000;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SpyCoefficientLedgerMapper ledgerMapper;
    private final StatsMapper statsMapper;
    private final OperationsLogMapper operationsLogMapper;
    private final BasicDataMapper basicDataMapper;

    public SpyCoefficientService(SpyCoefficientLedgerMapper ledgerMapper,
                                 StatsMapper statsMapper,
                                 OperationsLogMapper operationsLogMapper,
                                 BasicDataMapper basicDataMapper) {
        this.ledgerMapper = ledgerMapper;
        this.statsMapper = statsMapper;
        this.operationsLogMapper = operationsLogMapper;
        this.basicDataMapper = basicDataMapper;
    }

    /**
     * 施加一个卧底系数因子。
     *
     * @param factor 乘数因子×100（130=×1.3，50=×0.5），不是增量
     */
    @Transactional
    public SpyCoefficientResult applyFactor(int playerId,
                                            int roundId,
                                            int factor,
                                            String factorType,
                                            String idempotencyKey,
                                            String operatorId,
                                            String reason) {
        validateCommon(playerId, roundId, operatorId, idempotencyKey);
        validateText(reason, "reason不能为空");
        if (!FACTOR_TYPE_TASK_BONUS.equals(factorType) && !FACTOR_TYPE_EXPOSED_HALVE.equals(factorType)) {
            throw new IllegalArgumentException("factorType仅支持 task_bonus 或 exposed_halve，收到：" + factorType);
        }
        if (factor <= 0) {
            throw new IllegalArgumentException("factor必须为正数（乘数因子×100，130表示×1.3），收到：" + factor);
        }
        if (FACTOR_TYPE_EXPOSED_HALVE.equals(factorType) && factor != FACTOR_HALVE) {
            throw new IllegalArgumentException("识破减半的factor固定为50（×0.5），不接受自定义值");
        }
        if (basicDataMapper.findPlayerById(playerId) == null) {
            throw new IllegalArgumentException("选手不存在：playerId=" + playerId);
        }

        String fullKey = "spycoef_" + idempotencyKey.trim();

        // 幂等：先查后插。不在 @Transactional 内捕获 DuplicateKeyException，
        // 否则事务被标记回滚（沿用群投票与手工销量的既有策略）。
        if (ledgerMapper.countByIdempotencyKey(fullKey) > 0) {
            return SpyCoefficientResult.duplicated(playerId, roundId,
                    currentCoefficient(playerId, roundId), taskBonusCount(playerId, roundId));
        }

        // 识破减半只允许一次。重复施加会让卧底人气变成 ×0.25，
        // 而界面上只显示一个「已识破」标记，看不出被减了两次。
        if (FACTOR_TYPE_EXPOSED_HALVE.equals(factorType)) {
            SpyCoefficientLedgerItem existing =
                    ledgerMapper.findLatestActiveByType(playerId, roundId, FACTOR_TYPE_EXPOSED_HALVE);
            if (existing != null) {
                // 拒绝消息必须带时间与操作人：只说「已施加过」，现场会以为系统卡住
                // 而反复点击，或误以为自己没点成功转而去手动改人气。
                String when = existing.getCreatedAt() == null ? "时间未记录" : TIME_FMT.format(existing.getCreatedAt());
                String who = StringUtils.hasText(existing.getOperatorId()) ? existing.getOperatorId() : "操作人未记录";
                return SpyCoefficientResult.rejected(playerId, roundId,
                        currentCoefficient(playerId, roundId), taskBonusCount(playerId, roundId),
                        "本轮该选手已于 " + when + " 由「" + who + "」标记过识破减半，不可重复施加。"
                                + "若那次标记有误，请先撤销该条记录（账本条目 #" + existing.getId() + "），再重新施加");
            }
        }

        statsMapper.ensurePlayerRoundStats(playerId, roundId);

        int before = currentCoefficient(playerId, roundId);
        int after = clampMultiply(before, factor);
        if (after == before && factor != COEFFICIENT_BASE) {
            // 触及上下限被夹住：宁可明确拒绝，也不要让运营看到「操作成功但数字没动」。
            return SpyCoefficientResult.rejected(playerId, roundId, before, taskBonusCount(playerId, roundId),
                    "当前系数 " + label(before) + " 施加 " + label(factor) + " 后超出允许范围（"
                            + label(COEFFICIENT_FLOOR) + " ~ " + label(COEFFICIENT_CEILING)
                            + "），本次未施加。请核对是否重复点击");
        }

        ledgerMapper.insert(playerId, roundId, factor, factorType, fullKey, operatorId, reason);

        // 留痕先于改数。若日志写失败则整笔回滚，绝不允许「系数变了但没人知道谁改的」。
        operationsLogMapper.insert(operatorId, "spy_coefficient_apply", "player:" + playerId,
                "{\"roundId\":" + roundId
                        + ",\"playerId\":" + playerId
                        + ",\"factorType\":\"" + safe(factorType) + "\""
                        + ",\"factor\":" + factor
                        + ",\"coefficientBefore\":" + before
                        + ",\"coefficientAfter\":" + after
                        + ",\"idempotencyKey\":\"" + safe(fullKey) + "\"}",
                reason);

        int rows = statsMapper.resetPlayerSpyCoefficient(playerId, roundId, after);
        if (rows != 1) {
            throw new IllegalStateException("更新player_round_stats.spy_coefficient失败：playerId="
                    + playerId + ", roundId=" + roundId);
        }

        return SpyCoefficientResult.applied(playerId, roundId, factor, factorType,
                before, after, taskBonusCount(playerId, roundId));
    }

    /**
     * 任务加成的便捷入口，固定 ×1.3。
     */
    @Transactional
    public SpyCoefficientResult applyTaskBonus(int playerId, int roundId, int factor,
                                               String idempotencyKey, String operatorId, String reason) {
        return applyFactor(playerId, roundId, factor, FACTOR_TYPE_TASK_BONUS, idempotencyKey, operatorId, reason);
    }

    /**
     * 识破减半的便捷入口，固定 ×0.5。
     */
    @Transactional
    public SpyCoefficientResult applyExposedHalve(int playerId, int roundId,
                                                   String idempotencyKey, String operatorId, String reason) {
        return applyFactor(playerId, roundId, FACTOR_HALVE, FACTOR_TYPE_EXPOSED_HALVE,
                idempotencyKey, operatorId, reason);
    }

    /**
     * 撤销一条账本条目，并按剩余未撤销条目重建系数。
     *
     * <p>不做除法回退，原因见类注释。重建后允许同类型条目重新施加
     * （识破减半误标后必须能改回来，否则只能靠手动调人气，会污染账本）。
     */
    @Transactional
    public SpyCoefficientResult revoke(long ledgerId, int playerId, int roundId,
                                        String operatorId, String reason) {
        validateCommon(playerId, roundId, operatorId, "n/a");
        validateText(reason, "reason不能为空（撤销必须说明原因）");

        SpyCoefficientLedgerItem item = ledgerMapper.findById(ledgerId);
        if (item == null) {
            throw new IllegalArgumentException("账本条目不存在：id=" + ledgerId);
        }
        // 校验归属，防跨轮/跨选手误撤。前端理论上不会传错，但接口可被直接调用，
        // 而撤错一条系数记录不会报错，只会让另一位选手的人气静默变化。
        if (item.getPlayerId() == null || item.getPlayerId() != playerId
                || item.getRoundId() == null || item.getRoundId() != roundId) {
            throw new IllegalArgumentException("账本条目 #" + ledgerId + " 不属于该选手该轮次（实际归属：playerId="
                    + item.getPlayerId() + ", roundId=" + item.getRoundId() + "），拒绝撤销");
        }
        if (item.isRevoked()) {
            return SpyCoefficientResult.duplicated(playerId, roundId,
                    currentCoefficient(playerId, roundId), taskBonusCount(playerId, roundId));
        }

        int rows = ledgerMapper.markRevoked(ledgerId);
        if (rows != 1) {
            // 并发下另一个请求已抢先撤销。这里视为幂等成功，不抛错。
            return SpyCoefficientResult.duplicated(playerId, roundId,
                    currentCoefficient(playerId, roundId), taskBonusCount(playerId, roundId));
        }

        int before = currentCoefficient(playerId, roundId);
        int after = rebuildCoefficient(playerId, roundId);

        operationsLogMapper.insert(operatorId, "spy_coefficient_revoke", "player:" + playerId,
                "{\"roundId\":" + roundId
                        + ",\"playerId\":" + playerId
                        + ",\"ledgerId\":" + ledgerId
                        + ",\"revokedFactorType\":\"" + safe(item.getFactorType()) + "\""
                        + ",\"revokedFactor\":" + item.getFactor()
                        + ",\"coefficientBefore\":" + before
                        + ",\"coefficientAfter\":" + after + "}",
                reason);

        return SpyCoefficientResult.revoked(playerId, roundId, ledgerId, item.getFactorType(),
                before, after, taskBonusCount(playerId, roundId));
    }

    /**
     * 查询选手当前系数与账本明细，供界面回显。
     */
    public SpyCoefficientResult inspect(int playerId, int roundId) {
        if (playerId <= 0 || roundId <= 0) {
            throw new IllegalArgumentException("playerId与roundId必须为正数");
        }
        int current = currentCoefficient(playerId, roundId);
        SpyCoefficientResult result = SpyCoefficientResult.inspected(playerId, roundId, current,
                taskBonusCount(playerId, roundId));
        result.setLedger(ledgerMapper.listByPlayerRound(playerId, roundId));
        result.setExposed(ledgerMapper.countActiveByType(playerId, roundId, FACTOR_TYPE_EXPOSED_HALVE) > 0);
        result.setSpyPopularityRaw(nullToZero(statsMapper.findPlayerSpyPopularityRaw(playerId, roundId)));
        result.setSpyPopularityAdjusted(nullToZero(statsMapper.findPlayerSpyPopularity(playerId, roundId)));
        return result;
    }

    /**
     * 从 100 起，按未撤销条目顺序乘一遍，重算并写回系数。
     */
    private int rebuildCoefficient(int playerId, int roundId) {
        List<Integer> factors = ledgerMapper.listActiveFactors(playerId, roundId);
        int value = COEFFICIENT_BASE;
        for (Integer f : factors) {
            if (f == null || f <= 0) {
                continue;
            }
            value = clampMultiply(value, f);
        }
        statsMapper.ensurePlayerRoundStats(playerId, roundId);
        int rows = statsMapper.resetPlayerSpyCoefficient(playerId, roundId, value);
        if (rows != 1) {
            throw new IllegalStateException("重建spy_coefficient失败：playerId=" + playerId + ", roundId=" + roundId);
        }
        return value;
    }

    /**
     * 乘法施加并夹在上下限内。夹住时返回原值，由调用方判断并拒绝。
     */
    private int clampMultiply(int current, int factor) {
        long next = (long) current * factor / COEFFICIENT_BASE;
        if (next < COEFFICIENT_FLOOR || next > COEFFICIENT_CEILING) {
            return current;
        }
        return (int) next;
    }

    private int currentCoefficient(int playerId, int roundId) {
        Integer value = statsMapper.findPlayerSpyCoefficient(playerId, roundId);
        return value == null ? COEFFICIENT_BASE : value;
    }

    private int taskBonusCount(int playerId, int roundId) {
        return ledgerMapper.countActiveByType(playerId, roundId, FACTOR_TYPE_TASK_BONUS);
    }

    private void validateCommon(int playerId, int roundId, String operatorId, String idempotencyKey) {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId必须为正数");
        }
        if (roundId <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        validateText(operatorId, "operatorId不能为空");
        validateText(idempotencyKey, "idempotencyKey不能为空（防连点，由前端生成）");
    }

    private void validateText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static String label(int coefficient) {
        return coefficient % 100 == 0 ? "×" + (coefficient / 100) : "×" + (coefficient / 100.0);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
