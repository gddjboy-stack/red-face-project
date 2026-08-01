package com.redface.service;

import com.redface.entity.LiveMetricWatermark;
import com.redface.mapper.LiveMetricWatermarkMapper;
import com.redface.mapper.OperationsLogMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * C20-4A 直播数据水位线服务。
 *
 * <p><b>为什么需要水位线</b>：抖音官方直播中控台只提供「本场直播」的点赞/评论/礼物实时累计数，
 * 不提供跨场次历史累计。每场开播时中控台三个数字都从 0 重新开始。运营在后台录入的是
 * 「当前累计总数」，系统必须减去上次录入的总数（水位线）才能得到本次真实增量。
 *
 * <p><b>为什么必须能校准</b>：8/3 收官时点赞水位线可能是 50000，8/9 开播后中控台从 0 重新计数，
 * 运营录入 3000 时若直接算 3000 - 50000 = -47000，会导致人气倒扣四万七。
 * 这个错误在每场新直播的首次录入时必然发生，因此必须提供显式校准入口 + 自动兜底。
 *
 * <p><b>两个方向的风险</b>：
 * <ul>
 *   <li>倒扣（当前总数 &lt; 水位线）：由 {@link #previewEntry} 拦截，返回需确认信号，绝不写入负值。</li>
 *   <li>多加（误点校准导致水位线被清零）：反方向不会触发任何异常，因此
 *       {@link #calibrate} 保留归零前原值，并提供 {@link #revokeCalibration} 撤销。</li>
 * </ul>
 *
 * <p><b>为什么不建「场次」实体</b>：8/3 与 8/9 属于同一轮次的两场直播，轮次无法作为重置依据；
 * 而完整场次实体对当前规模是过度设计。折中方案是 sessionSeq 分段标识，写入流水 metadata，
 * 需要还原「某一场通过点赞入账了多少」时按标识分组即可。
 */
@Service
public class LiveWatermarkService {

    /** 点赞累计数。 */
    public static final String METRIC_LIKE = "like_delta";
    /** 评论累计数。 */
    public static final String METRIC_COMMENT = "comment_delta";
    /** 礼物累计数。是否纳入水位线由配置开关控制，见 LiveProperties。 */
    public static final String METRIC_GIFT = "gift";

    /** 受水位线管理的全部来源。校准时必须三条同时归零，否则漏归零的那条会在下次录入时倒扣。 */
    private static final List<String> ALL_METRICS = List.of(METRIC_GIFT, METRIC_LIKE, METRIC_COMMENT);

    private static final DateTimeFormatter SESSION_SEQ_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * 生成计数周期标识。时间戳后拼 4 位随机后缀保证唯一。
     *
     * <p><b>为何不能只用秒级时间戳</b>：现场断流重连时，运营极可能在几秒内
     * 连点两次校准（第一次没反应就再点）。若同一秒内两次校准得到相同标识，
     * 两段流水在 metadata 里完全无法区分，「按场次还原入账总额」这一唯一的
     * 事后审计手段就彻底失效——而那正是引入标识的全部目的。
     * 不用毫秒是因为毫秒同样可能碰撞，且人工核对日志时可读性更差。
     */
    private static String nextSessionSeq() {
        return LocalDateTime.now().format(SESSION_SEQ_FORMAT) + "-"
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private final LiveMetricWatermarkMapper watermarkMapper;
    private final OperationsLogMapper operationsLogMapper;

    public LiveWatermarkService(LiveMetricWatermarkMapper watermarkMapper,
                               OperationsLogMapper operationsLogMapper) {
        this.watermarkMapper = watermarkMapper;
        this.operationsLogMapper = operationsLogMapper;
    }

    /**
     * 预演一次录入，计算增量并判定是否需要人工确认。本方法不写入任何数据。
     *
     * <p>当前总数小于水位线时，唯一合理的解释是中控台已重新计数（新场次开播或断流重连），
     * 此时返回 needsCalibration=true，由前端弹窗确认，确认后调用
     * {@link #calibrate} 再重新录入。任何情况下都不会产生负增量。
     *
     * @param metricType   数据来源
     * @param currentTotal 中控台当前累计总数
     * @return 预演结果
     */
    public EntryPreview previewEntry(String metricType, long currentTotal) {
        String normalized = normalizeMetric(metricType);
        if (currentTotal < 0) {
            throw new IllegalArgumentException("当前总数不能为负数");
        }
        LiveMetricWatermark watermark = requireWatermark(normalized);
        long lastTotal = watermark.getLastTotal();
        if (currentTotal < lastTotal) {
            return EntryPreview.needsCalibration(normalized, currentTotal, lastTotal, watermark.getSessionSeq());
        }
        return EntryPreview.ok(normalized, currentTotal, lastTotal,
                currentTotal - lastTotal, watermark.getSessionSeq());
    }

    /**
     * 录入成功后推进水位线。必须与人气入账在同一事务内调用，否则会出现
     * 「人气已入账但水位线未推进」导致同一增量被重复入账。
     *
     * @param metricType    数据来源
     * @param newTotal      本次录入的当前总数
     * @param expectedTotal 预演时读到的水位线，用于乐观锁比对
     * @param operatorId    操作人
     */
    public void advanceWatermark(String metricType, long newTotal, long expectedTotal, String operatorId) {
        String normalized = normalizeMetric(metricType);
        validateText(operatorId, "operatorId不能为空");
        int affected = watermarkMapper.advance(normalized, newTotal, expectedTotal, operatorId);
        if (affected == 0) {
            throw new IllegalStateException("水位线已被其他操作变更（可能是并发录入或期间发生了校准），"
                    + "本次录入已取消，请重新读取当前总数后再提交");
        }
    }

    /**
     * 校准全部来源的水位线（归零），用于新一场直播开播。
     *
     * <p>三条来源在同一事务内归零：只归零两条的中间态会让第三条在下次录入时倒扣。
     *
     * <p><b>重要</b>：本操作只重置中控台读数基准，<b>不会改变任何选手的人气值</b>。
     * 历史人气跨场累计保留。前端必须在二次确认文案中写明这一点——运营若把
     * 「校准/清零」理解为「分数清零」，会在发现分数没变时误判系统故障，
     * 进而手动调分「修正」，造成系统层面无法拦截的数据污染。
     *
     * @param operatorId 操作人
     * @param reason     操作原因
     * @return 校准结果
     */
    @Transactional
    public CalibrationResult calibrate(String operatorId, String reason) {
        validateText(operatorId, "operatorId不能为空");
        validateText(reason, "reason不能为空");
        String sessionSeq = nextSessionSeq();
        Map<String, Long> previousTotals = new LinkedHashMap<>();
        for (String metric : ALL_METRICS) {
            LiveMetricWatermark watermark = requireWatermark(metric);
            previousTotals.put(metric, watermark.getLastTotal());
            watermarkMapper.calibrate(metric, sessionSeq, operatorId);
        }
        operationsLogMapper.insert(operatorId, "live_watermark_calibrate", "all_metrics",
                buildCalibrationDetail(sessionSeq, previousTotals), reason);
        return new CalibrationResult(sessionSeq, previousTotals);
    }

    /**
     * 撤销最近一次校准，把水位线恢复为归零前的值。
     *
     * <p>存在的必要性：误点校准的后果是<b>多加</b>而非倒扣。例如直播中途误点校准，
     * 点赞水位线由 80000 被清为 0，下次录入 85000 时系统算出 +85000，
     * 一次性多加八万五，且不触发任何告警（85000 &gt; 0 完全「正常」）。
     * 负值兜底只防倒扣，对这个方向完全无效，因此必须能撤销。
     *
     * <p>仅当校准后尚未发生任何录入时可撤销；已录入则流水已按新周期入账，
     * 自动恢复会造成账实不符，必须走人工冲销（日志中保留了原值可供核算）。
     *
     * @param operatorId 操作人
     * @param reason     操作原因
     * @return 撤销结果
     */
    @Transactional
    public RevokeResult revokeCalibration(String operatorId, String reason) {
        validateText(operatorId, "operatorId不能为空");
        validateText(reason, "reason不能为空");
        Map<String, Long> restoredTotals = new LinkedHashMap<>();
        for (String metric : ALL_METRICS) {
            LiveMetricWatermark watermark = requireWatermark(metric);
            if (watermark.getPrevTotal() == null) {
                throw new IllegalStateException("来源 " + metric + " 没有可撤销的校准记录");
            }
            if (watermark.getEntryCount() > 0) {
                throw new IllegalStateException("来源 " + metric + " 在校准后已录入 "
                        + watermark.getEntryCount() + " 次，不能自动撤销，须人工冲销；"
                        + "归零前水位线原值为 " + watermark.getPrevTotal() + "，可据此核算");
            }
            restoredTotals.put(metric, watermark.getPrevTotal());
        }
        for (String metric : ALL_METRICS) {
            int affected = watermarkMapper.revokeCalibration(metric, operatorId);
            if (affected == 0) {
                throw new IllegalStateException("来源 " + metric + " 撤销失败，状态已变更，请重新查询后处理");
            }
        }
        operationsLogMapper.insert(operatorId, "live_watermark_revoke_calibration", "all_metrics",
                buildRevokeDetail(restoredTotals), reason);
        return new RevokeResult(restoredTotals);
    }

    /**
     * 切换场控目标前检查是否有尚未录入的直播数据，返回人工可读的风险提示。
     *
     * <p><b>为何需要这一层</b>：Claude 裁定 R-2 采用「礼物按场控目标归属」后，
     * 「换场控目标前先录一次数」从一个普通操作升级为<b>决定归属正确性的关键动作</b>：
     * 若漏做，上一位选手在台时收到的礼物会整段归到下一位选手头上。
     * 裁定把这一条交给《运营执行手册》，但直播现场靠记忆执行纪律不可靠，
     * 因此在系统层面给出提示。注意：这里只能提示「自上次录入后已过了多久」，
     * <b>无法得知中控台真实增量</b>——因为数据靠人工录入，系统看不到未录入的部分。
     * 这是人工录入模式的固有上限，不应对该提示产生超出其能力的信赖。
     *
     * @return 风险提示文案；无需提醒时返回 null
     */
    public String buildTargetSwitchWarning() {
        LiveMetricWatermark gift = requireWatermark(METRIC_GIFT);
        // 判定依据必须是 entry_count（本计数周期内是否发生过录入），不能用 updated_at：
        // 该列 NOT NULL DEFAULT CURRENT_TIMESTAMP，惰性初始化时就被填上当前时间，永远不为空，
        // 且会被校准等非录入操作刷新，本身就不是「上次录入时间」的可靠代理。
        // 若错用 updated_at，最该提示的场景（一次都没录过）恰好变成唯一不提示的场景。
        if (gift.getEntryCount() == 0) {
            return "本场尚未录入过礼物数据。若切换前已有礼物进入，请先录入一次再切换，"
                    + "否则这段礼物会整段归到下一位选手头上。";
        }
        if (gift.getUpdatedAt() == null) {
            return null;
        }
        long minutes = java.time.Duration.between(gift.getUpdatedAt(), LocalDateTime.now()).toMinutes();
        if (minutes >= 3) {
            return "礼物数据已有 " + minutes + " 分钟未录入。建议先录入一次再切换场控目标，"
                    + "否则这段时间的礼物会整段归到下一位选手头上。";
        }
        return null;
    }

    /**
     * 读取指定来源当前的计数周期标识，用于写入人气流水 metadata 实现分段还原。
     *
     * @param metricType 数据来源
     * @return 计数周期标识
     */
    public String currentSessionSeq(String metricType) {
        return requireWatermark(normalizeMetric(metricType)).getSessionSeq();
    }

    /**
     * 查询全部来源的水位线现状，供后台展示。
     *
     * @return 水位线列表，顺序固定为 gift、like_delta、comment_delta
     */
    public List<LiveMetricWatermark> listAll() {
        return ALL_METRICS.stream().map(this::requireWatermark).toList();
    }

    /**
     * 读取水位线；该来源尚无记录时惰性初始化一行，避免部署后首次录入报错。
     */
    private LiveMetricWatermark requireWatermark(String metricType) {
        LiveMetricWatermark watermark = watermarkMapper.findByMetricType(metricType);
        if (watermark != null) {
            return watermark;
        }
        watermarkMapper.insert(metricType, 0L, nextSessionSeq(), "system");
        LiveMetricWatermark created = watermarkMapper.findByMetricType(metricType);
        if (created == null) {
            throw new IllegalStateException("水位线初始化失败: " + metricType);
        }
        return created;
    }

    private String buildCalibrationDetail(String sessionSeq, Map<String, Long> previousTotals) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"newSessionSeq\":\"").append(sessionSeq).append("\",\"previousTotals\":{");
        appendTotals(sb, previousTotals);
        sb.append("},\"popularityAffected\":false}");
        return sb.toString();
    }

    private String buildRevokeDetail(Map<String, Long> restoredTotals) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"restoredTotals\":{");
        appendTotals(sb, restoredTotals);
        sb.append("},\"popularityAffected\":false}");
        return sb.toString();
    }

    private void appendTotals(StringBuilder sb, Map<String, Long> totals) {
        boolean first = true;
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
    }

    private String normalizeMetric(String metricType) {
        validateText(metricType, "metricType不能为空");
        String normalized = metricType.trim().toLowerCase();
        if (!ALL_METRICS.contains(normalized)) {
            throw new IllegalArgumentException("未知metricType: " + metricType
                    + "，仅支持 gift/like_delta/comment_delta");
        }
        return normalized;
    }

    private void validateText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 录入预演结果。
     *
     * @param metricType        数据来源
     * @param currentTotal      本次读到的当前总数
     * @param lastTotal         当前水位线
     * @param delta             计算出的增量；needsCalibration 为 true 时该值为 0
     * @param sessionSeq        当前计数周期标识
     * @param needsCalibration  是否需要先校准（当前总数小于水位线）
     * @param message           面向运营的提示文案
     */
    public record EntryPreview(String metricType,
                               long currentTotal,
                               long lastTotal,
                               long delta,
                               String sessionSeq,
                               boolean needsCalibration,
                               String message) {

        static EntryPreview ok(String metricType, long currentTotal, long lastTotal,
                               long delta, String sessionSeq) {
            return new EntryPreview(metricType, currentTotal, lastTotal, delta, sessionSeq, false,
                    "本次增量 " + delta);
        }

        static EntryPreview needsCalibration(String metricType, long currentTotal,
                                             long lastTotal, String sessionSeq) {
            return new EntryPreview(metricType, currentTotal, lastTotal, 0L, sessionSeq, true,
                    "检测到当前总数 " + currentTotal + " 小于上次记录 " + lastTotal
                            + "，通常说明中控台已重新计数（新场次开播或断流重连）。"
                            + "是否按新一场直播处理？校准只重置读数基准，不会改变任何选手的人气值。");
        }
    }

    /**
     * 校准结果。
     *
     * @param sessionSeq     新的计数周期标识
     * @param previousTotals 各来源归零前的水位线原值
     */
    public record CalibrationResult(String sessionSeq, Map<String, Long> previousTotals) {
    }

    /**
     * 撤销校准结果。
     *
     * @param restoredTotals 各来源恢复后的水位线值
     */
    public record RevokeResult(Map<String, Long> restoredTotals) {
    }
}
