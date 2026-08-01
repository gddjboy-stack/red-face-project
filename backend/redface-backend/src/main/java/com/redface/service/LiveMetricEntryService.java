package com.redface.service;

import com.redface.config.LiveProperties;
import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.PopularityChangeResult;
import com.redface.entity.CollectState;
import com.redface.mapper.OperationsLogMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * C20-4A 直播互动数据录入服务。运营录入「中控台当前累计总数」，本服务负责换算增量并入账。
 *
 * <p>与旧的 {@link LiveDataService#onMetricDelta} 的区别：旧接口接收的是运营自行算好的增量，
 * 一旦运营算错或漏算，系统无从校验；新接口接收原始累计总数，增量由系统计算，
 * 且水位线推进与人气入账在同一事务内完成，杜绝「已入账但水位线未推进」导致的重复入账。
 */
@Service
public class LiveMetricEntryService {

    private static final String SOURCE_LIKE = "like";
    private static final String SOURCE_COMMENT = "comment";
    private static final String SOURCE_GIFT = "gift";
    private static final String TARGET_PLAYER = "player";

    private final LiveWatermarkService watermarkService;
    private final PopularityService popularityService;
    private final CollectStateService collectStateService;
    private final RoundService roundService;
    private final OperationsLogMapper operationsLogMapper;
    private final LiveProperties liveProperties;

    public LiveMetricEntryService(LiveWatermarkService watermarkService,
                                  PopularityService popularityService,
                                  CollectStateService collectStateService,
                                  RoundService roundService,
                                  OperationsLogMapper operationsLogMapper,
                                  LiveProperties liveProperties) {
        this.watermarkService = watermarkService;
        this.popularityService = popularityService;
        this.collectStateService = collectStateService;
        this.roundService = roundService;
        this.operationsLogMapper = operationsLogMapper;
        this.liveProperties = liveProperties;
    }

    /**
     * 预演录入，返回将要入账的增量或「需先校准」信号。不写入任何数据，供前端展示确认。
     *
     * @param metricType   数据来源
     * @param currentTotal 中控台当前累计总数
     * @return 预演结果
     */
    public LiveWatermarkService.EntryPreview preview(String metricType, long currentTotal) {
        assertMetricAllowed(metricType);
        return watermarkService.previewEntry(metricType, currentTotal);
    }

    /**
     * 按「当前累计总数」录入并入账。
     *
     * <p>当前总数小于水位线时<b>不写入任何数据</b>，直接抛出待确认异常，
     * 由前端弹窗询问是否为新场次开播，确认后先校准再重新录入。
     * 这保证任何情况下都不会产生负增量入账。
     *
     * @param metricType     数据来源（like_delta / comment_delta / gift）
     * @param currentTotal   中控台当前累计总数
     * @param operatorId     操作人
     * @param idempotencyKey 前端生成的幂等键，防连点
     * @param reason         操作原因
     * @return 录入结果
     */
    @Transactional
    public EntryOutcome submit(String metricType,
                               long currentTotal,
                               String operatorId,
                               String idempotencyKey,
                               String reason) {
        assertMetricAllowed(metricType);
        validateText(operatorId, "operatorId不能为空");
        validateText(idempotencyKey, "idempotencyKey不能为空（防连点，由前端生成）");
        validateText(reason, "reason不能为空");

        LiveWatermarkService.EntryPreview preview = watermarkService.previewEntry(metricType, currentTotal);
        if (preview.needsCalibration()) {
            throw new WatermarkCalibrationRequiredException(preview);
        }
        if (preview.delta() == 0) {
            return EntryOutcome.noChange(preview);
        }

        PopularityChangeResult changeResult = applyPopularity(metricType, preview, operatorId,
                idempotencyKey, reason);
        watermarkService.advanceWatermark(metricType, currentTotal, preview.lastTotal(), operatorId);
        operationsLogMapper.insert(operatorId, "live_metric_entry", metricType,
                "{\"metricType\":\"" + preview.metricType() + "\",\"currentTotal\":" + currentTotal
                        + ",\"previousTotal\":" + preview.lastTotal()
                        + ",\"delta\":" + preview.delta()
                        + ",\"sessionSeq\":\"" + preview.sessionSeq() + "\"}",
                reason);
        return EntryOutcome.applied(preview, changeResult);
    }

    /**
     * 按增量入账。sessionSeq 写入流水 metadata，用于事后按计数周期分组还原
     * 「某一场直播通过点赞入账了多少人气」。
     */
    private PopularityChangeResult applyPopularity(String metricType,
                                                   LiveWatermarkService.EntryPreview preview,
                                                   String operatorId,
                                                   String idempotencyKey,
                                                   String reason) {
        PopularityChangeRequest req = new PopularityChangeRequest();
        req.setSource(resolveSource(metricType));
        req.setRawValue(preview.delta());
        req.setIdempotencyKey("lme_" + idempotencyKey);
        req.setOperatorId(operatorId);
        req.setReason(reason);
        req.setOccurredAt(LocalDateTime.now());
        req.setMetadata("{\"sessionSeq\":\"" + preview.sessionSeq() + "\",\"metricType\":\""
                + preview.metricType() + "\",\"currentTotal\":" + preview.currentTotal()
                + ",\"previousTotal\":" + preview.lastTotal() + "}");
        if (LiveWatermarkService.METRIC_GIFT.equals(preview.metricType())) {
            // 礼物走水位线时，中控台累计数是全场维度、不区分选手，只能按当前场控目标归属。
            req.setTargetType(TARGET_PLAYER);
            req.setTargetId(requireCollectPlayerId());
            Integer roundId = roundService.getCurrentAccrualRoundId();
            if (roundId == null) {
                throw new IllegalStateException("当前无可用轮次,无法入账礼物");
            }
            req.setRoundId(roundId);
        }
        return popularityService.applyChange(req);
    }

    private int requireCollectPlayerId() {
        CollectState current = collectStateService.getCurrent();
        if (current != null && TARGET_PLAYER.equals(current.getMode()) && current.getTargetId() != null) {
            return current.getTargetId();
        }
        throw new IllegalStateException("礼物按总数录入需要当前场控为 player 模式并已指定目标选手");
    }

    /**
     * 礼物是否允许走水位线由配置开关控制。开关关闭时明确拒绝，而不是静默按 like 处理——
     * 静默降级会让运营以为礼物已入账。
     */
    private void assertMetricAllowed(String metricType) {
        if (metricType != null
                && LiveWatermarkService.METRIC_GIFT.equalsIgnoreCase(metricType.trim())
                && !liveProperties.isGiftWatermarkEnabled()) {
            throw new IllegalStateException("礼物未启用按总数录入（redface.live.gift-watermark-enabled=false），"
                    + "请使用逐笔礼物入账接口");
        }
    }

    private String resolveSource(String metricType) {
        String normalized = metricType.trim().toLowerCase();
        return switch (normalized) {
            case LiveWatermarkService.METRIC_LIKE -> SOURCE_LIKE;
            case LiveWatermarkService.METRIC_COMMENT -> SOURCE_COMMENT;
            case LiveWatermarkService.METRIC_GIFT -> SOURCE_GIFT;
            default -> throw new IllegalArgumentException("未知metricType: " + metricType);
        };
    }

    private void validateText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 录入结果。
     *
     * @param metricType    数据来源
     * @param previousTotal 录入前水位线
     * @param currentTotal  本次录入的当前总数
     * @param delta         本次入账增量
     * @param sessionSeq    计数周期标识
     * @param changeResult  人气入账结果，无变化时为 null
     * @param message       面向运营的提示文案
     */
    public record EntryOutcome(String metricType,
                               long previousTotal,
                               long currentTotal,
                               long delta,
                               String sessionSeq,
                               PopularityChangeResult changeResult,
                               String message) {

        static EntryOutcome applied(LiveWatermarkService.EntryPreview preview, PopularityChangeResult result) {
            return new EntryOutcome(preview.metricType(), preview.lastTotal(), preview.currentTotal(),
                    preview.delta(), preview.sessionSeq(), result,
                    "已入账增量 " + preview.delta());
        }

        static EntryOutcome noChange(LiveWatermarkService.EntryPreview preview) {
            return new EntryOutcome(preview.metricType(), preview.lastTotal(), preview.currentTotal(),
                    0L, preview.sessionSeq(), null,
                    "当前总数与上次记录一致，增量为 0，未入账");
        }
    }

    /**
     * 当前总数小于水位线时抛出。携带预演结果供前端渲染确认弹窗。
     *
     * <p>用异常而非返回值表达，是为了确保调用方无法「忽略」这个信号继续入账——
     * 若用返回值，任何漏判 needsCalibration 的调用路径都会静默写入负增量。
     */
    public static class WatermarkCalibrationRequiredException extends RuntimeException {

        private final transient LiveWatermarkService.EntryPreview preview;

        public WatermarkCalibrationRequiredException(LiveWatermarkService.EntryPreview preview) {
            super(preview.message());
            this.preview = preview;
        }

        public LiveWatermarkService.EntryPreview getPreview() {
            return preview;
        }
    }
}
