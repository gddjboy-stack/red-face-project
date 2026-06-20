package com.redface.dto;

/**
 * C14 退款结果，回显本次退款扣减的人气值与回滚归属，供后台展示与审计核对。
 */
public class RefundResult {
    private final String tokenId;
    private final Integer playerId;
    private final long refundedPoints;
    private final Integer roundId;

    public RefundResult(String tokenId, Integer playerId, long refundedPoints, Integer roundId) {
        this.tokenId = tokenId;
        this.playerId = playerId;
        this.refundedPoints = refundedPoints;
        this.roundId = roundId;
    }

    public String getTokenId() {
        return tokenId;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    /**
     * 本次退款扣减的人气值绝对值（正数展示，实际写入流水为负数）。
     */
    public long getRefundedPoints() {
        return refundedPoints;
    }

    public Integer getRoundId() {
        return roundId;
    }
}
