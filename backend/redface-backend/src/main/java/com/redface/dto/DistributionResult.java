package com.redface.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 团队池分配结果。用于返回批次 ID、分配总额和每位成员获得的份额。
 */
public class DistributionResult {
    private final boolean success;
    private final long batchId;
    private final int teamId;
    private final int roundId;
    private final long totalValue;
    private final long distributedValue;
    private final String method;
    private final Map<Integer, Long> memberShares;
    private final String message;

    private DistributionResult(boolean success,
                               long batchId,
                               int teamId,
                               int roundId,
                               long totalValue,
                               long distributedValue,
                               String method,
                               Map<Integer, Long> memberShares,
                               String message) {
        this.success = success;
        this.batchId = batchId;
        this.teamId = teamId;
        this.roundId = roundId;
        this.totalValue = totalValue;
        this.distributedValue = distributedValue;
        this.method = method;
        this.memberShares = Collections.unmodifiableMap(new LinkedHashMap<>(memberShares));
        this.message = message;
    }

    public static DistributionResult success(long batchId,
                                             int teamId,
                                             int roundId,
                                             long totalValue,
                                             String method,
                                             Map<Integer, Long> memberShares) {
        long distributedValue = memberShares.values().stream().mapToLong(Long::longValue).sum();
        return new DistributionResult(true, batchId, teamId, roundId, totalValue, distributedValue,
                method, memberShares, "success");
    }

    public boolean isSuccess() {
        return success;
    }

    public long getBatchId() {
        return batchId;
    }

    public int getTeamId() {
        return teamId;
    }

    public int getRoundId() {
        return roundId;
    }

    public long getTotalValue() {
        return totalValue;
    }

    public long getDistributedValue() {
        return distributedValue;
    }

    public String getMethod() {
        return method;
    }

    public Map<Integer, Long> getMemberShares() {
        return memberShares;
    }

    public String getMessage() {
        return message;
    }
}
