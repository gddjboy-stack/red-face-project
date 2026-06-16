package com.redface.dto;

/**
 * 模拟注入结果对象。用于彩排后台确认模拟事件是否成功入账、是否命中幂等重复，
 * 以及本次模拟事件最终归属到哪个目标。
 */
public class SimResult {
    private final boolean success;
    private final boolean duplicated;
    private final String eventType;
    private final long popularityValue;
    private final String targetType;
    private final Integer targetId;
    private final Integer roundId;
    private final String idempotencyKey;
    private final String message;

    private SimResult(boolean success,
                      boolean duplicated,
                      String eventType,
                      long popularityValue,
                      String targetType,
                      Integer targetId,
                      Integer roundId,
                      String idempotencyKey,
                      String message) {
        this.success = success;
        this.duplicated = duplicated;
        this.eventType = eventType;
        this.popularityValue = popularityValue;
        this.targetType = targetType;
        this.targetId = targetId;
        this.roundId = roundId;
        this.idempotencyKey = idempotencyKey;
        this.message = message;
    }

    /**
     * 根据人气变更结果构造模拟注入结果。
     *
     * @param eventType      模拟事件类型
     * @param idempotencyKey 本次模拟注入幂等键
     * @param result         人气变更结果
     * @return 模拟注入结果
     */
    public static SimResult from(String eventType, String idempotencyKey, PopularityChangeResult result) {
        return new SimResult(
                result.isSuccess(),
                result.isDuplicated(),
                eventType,
                result.getPopularityValue(),
                result.getTargetType(),
                result.getTargetId(),
                result.getRoundId(),
                idempotencyKey,
                result.getMessage()
        );
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isDuplicated() {
        return duplicated;
    }

    public String getEventType() {
        return eventType;
    }

    public long getPopularityValue() {
        return popularityValue;
    }

    public String getTargetType() {
        return targetType;
    }

    public Integer getTargetId() {
        return targetId;
    }

    public Integer getRoundId() {
        return roundId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getMessage() {
        return message;
    }
}
