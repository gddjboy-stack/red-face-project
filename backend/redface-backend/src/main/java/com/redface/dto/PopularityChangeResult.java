package com.redface.dto;

/**
 * 人气值变更结果对象，用于返回本次变更是否成功、是否幂等重复以及换算后人气值。
 */
public class PopularityChangeResult {
    private final boolean success;
    private final boolean duplicated;
    private final long popularityValue;
    private final String targetType;
    private final Integer targetId;
    private final Integer roundId;
    private final String message;

    private PopularityChangeResult(boolean success,
                                   boolean duplicated,
                                   long popularityValue,
                                   String targetType,
                                   Integer targetId,
                                   Integer roundId,
                                   String message) {
        this.success = success;
        this.duplicated = duplicated;
        this.popularityValue = popularityValue;
        this.targetType = targetType;
        this.targetId = targetId;
        this.roundId = roundId;
        this.message = message;
    }

    /**
     * 构造成功结果。
     *
     * @param popularityValue 本次换算后的人气值变动
     * @param targetType      目标类型
     * @param targetId        目标 ID
     * @param roundId         轮次 ID
     * @return 成功结果
     */
    public static PopularityChangeResult success(long popularityValue,
                                                 String targetType,
                                                 Integer targetId,
                                                 Integer roundId) {
        return new PopularityChangeResult(true, false, popularityValue, targetType, targetId, roundId, "success");
    }

    /**
     * 构造幂等重复结果。
     *
     * @return 幂等重复结果
     */
    public static PopularityChangeResult duplicated() {
        return new PopularityChangeResult(false, true, 0L, null, null, null, "duplicated");
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isDuplicated() {
        return duplicated;
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

    public String getMessage() {
        return message;
    }
}
