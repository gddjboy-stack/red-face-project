package com.redface.service;

import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.PopularityChangeResult;
import com.redface.dto.SimResult;
import com.redface.entity.CollectState;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 直播数据接入服务。负责将真实直播事件或彩排模拟事件转换为人气值变更请求，
 * 并统一委托 PopularityService.applyChange 入账。
 */
@Service
public class LiveDataService {

    private static final String EVENT_GIFT = "gift";
    private static final String EVENT_LIKE_DELTA = "like_delta";
    private static final String EVENT_COMMENT_DELTA = "comment_delta";
    private static final String SOURCE_GIFT = "gift";
    private static final String SOURCE_LIKE = "like";
    private static final String SOURCE_COMMENT = "comment";
    private static final String TARGET_PLAYER = "player";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PopularityService popularityService;
    private final RoundService roundService;
    private final CollectStateService collectStateService;

    public LiveDataService(PopularityService popularityService,
                           RoundService roundService,
                           CollectStateService collectStateService) {
        this.popularityService = popularityService;
        this.roundService = roundService;
        this.collectStateService = collectStateService;
    }

    /**
     * 接收礼物事件。礼物显式归属到选手，轮次使用当前可入账轮次。
     *
     * @param msgId       直播平台消息 ID
     * @param playerId    目标选手 ID
     * @param doubiValue  礼物抖币值
     * @param occurredAt  事件发生时间，Unix 毫秒时间戳；小于等于 0 时使用当前时间
     * @return 人气值变更结果
     */
    public PopularityChangeResult onGiftEvent(String msgId, int playerId, long doubiValue, long occurredAt) {
        validateText(msgId, "msgId不能为空");
        return applyGift(playerId, doubiValue, occurredAt, "gift_" + msgId, null, "直播礼物入账");
    }

    /**
     * 接收点赞/留言总增量。该类事件不带 target，归属和轮次均由当前场控状态决定。
     *
     * @param metricType like_delta 或 comment_delta
     * @param delta      总增量
     * @param occurredAt 事件发生时间，Unix 毫秒时间戳；小于等于 0 时使用当前时间
     * @param idemKey    幂等键
     * @return 人气值变更结果
     */
    public PopularityChangeResult onMetricDelta(String metricType, long delta, long occurredAt, String idemKey) {
        validateText(idemKey, "idemKey不能为空");
        String source = resolveMetricSource(metricType);
        return applyMetric(metricType, source, delta, occurredAt, idemKey, null, "直播互动增量入账");
    }

    /**
     * 模拟器：场控后台手动注入。模拟事件必须走与真实事件一致的人气入账路径。
     *
     * @param eventType  gift、like_delta 或 comment_delta
     * @param value      模拟值；gift 为抖币值，like/comment 为增量
     * @param targetId   gift 的目标选手 ID；like/comment 可为空并走场控归属
     * @param operatorId 操作人 ID
     * @return 模拟注入结果
     */
    public SimResult simulateInject(String eventType, long value, Integer targetId, String operatorId) {
        validateText(operatorId, "operatorId不能为空");
        String normalizedEventType = normalizeEventType(eventType);
        String idempotencyKey = generateSimulationIdempotencyKey();
        PopularityChangeResult result;
        if (EVENT_GIFT.equals(normalizedEventType)) {
            int playerId = resolveGiftTargetId(targetId);
            result = applyGift(playerId, value, System.currentTimeMillis(), idempotencyKey, operatorId, "模拟礼物入账");
        } else {
            String source = resolveMetricSource(normalizedEventType);
            result = applyMetric(normalizedEventType, source, value, System.currentTimeMillis(), idempotencyKey, operatorId, "模拟互动增量入账");
        }
        return SimResult.from(normalizedEventType, idempotencyKey, result);
    }

    /**
     * 兼容开发手册 5.3 的三参数模拟器签名。gift 未显式传 targetId 时，
     * 仅允许在当前场控为 player 模式时使用当前场控 targetId。
     */
    public SimResult simulateInject(String eventType, long value, String operatorId) {
        return simulateInject(eventType, value, null, operatorId);
    }

    private PopularityChangeResult applyGift(int playerId,
                                             long doubiValue,
                                             long occurredAt,
                                             String idempotencyKey,
                                             String operatorId,
                                             String reason) {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId必须为正数");
        }
        if (doubiValue <= 0) {
            throw new IllegalArgumentException("doubiValue必须为正数");
        }
        Integer roundId = roundService.getCurrentAccrualRoundId();
        if (roundId == null) {
            throw new IllegalStateException("当前无可用轮次,无法入账礼物事件");
        }
        PopularityChangeRequest req = new PopularityChangeRequest();
        req.setTargetType(TARGET_PLAYER);
        req.setTargetId(playerId);
        req.setSource(SOURCE_GIFT);
        req.setRawValue(doubiValue);
        req.setRoundId(roundId);
        req.setIdempotencyKey(idempotencyKey);
        req.setOperatorId(operatorId);
        req.setReason(reason);
        req.setOccurredAt(toLocalDateTime(occurredAt));
        return popularityService.applyChange(req);
    }

    private PopularityChangeResult applyMetric(String eventType,
                                               String source,
                                               long delta,
                                               long occurredAt,
                                               String idempotencyKey,
                                               String operatorId,
                                               String reason) {
        if (delta <= 0) {
            throw new IllegalArgumentException("delta必须为正数");
        }
        PopularityChangeRequest req = new PopularityChangeRequest();
        req.setSource(source);
        req.setRawValue(delta);
        req.setIdempotencyKey(idempotencyKey);
        req.setOperatorId(operatorId);
        req.setReason(reason + ":" + eventType);
        req.setOccurredAt(toLocalDateTime(occurredAt));
        return popularityService.applyChange(req);
    }

    private String resolveMetricSource(String metricType) {
        String normalizedMetricType = normalizeEventType(metricType);
        return switch (normalizedMetricType) {
            case EVENT_LIKE_DELTA -> SOURCE_LIKE;
            case EVENT_COMMENT_DELTA -> SOURCE_COMMENT;
            default -> throw new IllegalArgumentException("未知metricType: " + metricType);
        };
    }

    private String normalizeEventType(String eventType) {
        validateText(eventType, "eventType不能为空");
        return eventType.trim().toLowerCase();
    }

    private int resolveGiftTargetId(Integer targetId) {
        if (targetId != null) {
            return targetId;
        }
        CollectState current = collectStateService.getCurrent();
        if (current != null && TARGET_PLAYER.equals(current.getMode()) && current.getTargetId() != null) {
            return current.getTargetId();
        }
        throw new IllegalArgumentException("模拟gift事件必须传targetId；仅当前场控为player模式时可省略targetId");
    }

    private String generateSimulationIdempotencyKey() {
        return "sim_" + System.currentTimeMillis() + "_" + Integer.toUnsignedString(SECURE_RANDOM.nextInt(), 36);
    }

    private LocalDateTime toLocalDateTime(long occurredAt) {
        if (occurredAt <= 0) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(occurredAt), ZoneId.systemDefault());
    }

    private void validateText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
