package com.redface.dto;

/**
 * 加成系数调整结果。用于返回本次系数调整是否成功、是否幂等重复、调整幅度和最新系数。
 */
public class CoefficientResult {
    private final boolean success;
    private final boolean duplicated;
    private final int playerId;
    private final int roundId;
    private final int delta;
    private final int coefficient;
    private final String idempotencyKey;
    private final String message;

    private CoefficientResult(boolean success,
                              boolean duplicated,
                              int playerId,
                              int roundId,
                              int delta,
                              int coefficient,
                              String idempotencyKey,
                              String message) {
        this.success = success;
        this.duplicated = duplicated;
        this.playerId = playerId;
        this.roundId = roundId;
        this.delta = delta;
        this.coefficient = coefficient;
        this.idempotencyKey = idempotencyKey;
        this.message = message;
    }

    public static CoefficientResult success(int playerId,
                                            int roundId,
                                            int delta,
                                            int coefficient,
                                            String idempotencyKey) {
        return new CoefficientResult(true, false, playerId, roundId, delta, coefficient, idempotencyKey, "success");
    }

    public static CoefficientResult duplicated(int playerId,
                                               int roundId,
                                               String idempotencyKey,
                                               int coefficient) {
        return new CoefficientResult(false, true, playerId, roundId, 0, coefficient, idempotencyKey, "duplicated");
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isDuplicated() {
        return duplicated;
    }

    public int getPlayerId() {
        return playerId;
    }

    public int getRoundId() {
        return roundId;
    }

    public int getDelta() {
        return delta;
    }

    public int getCoefficient() {
        return coefficient;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getMessage() {
        return message;
    }
}
