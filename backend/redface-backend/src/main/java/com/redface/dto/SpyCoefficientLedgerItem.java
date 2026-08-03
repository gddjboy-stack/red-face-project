package com.redface.dto;

import java.time.LocalDateTime;

/**
 * C20-10 卧底人气系数账本条目。
 *
 * <p>{@code factor} 是乘数因子×100（130 表示 ×1.3，50 表示 ×0.5），<b>不是增量</b>。
 * 界面回显时应显示为「×1.3」而非「+30」，避免运营按加法理解累计结果。
 */
public class SpyCoefficientLedgerItem {
    private Long id;
    private Integer playerId;
    private Integer roundId;
    private Integer factor;
    private String factorType;
    private String operatorId;
    private String reason;
    private boolean revoked;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public Integer getRoundId() {
        return roundId;
    }

    public void setRoundId(Integer roundId) {
        this.roundId = roundId;
    }

    public Integer getFactor() {
        return factor;
    }

    public void setFactor(Integer factor) {
        this.factor = factor;
    }

    public String getFactorType() {
        return factorType;
    }

    public void setFactorType(String factorType) {
        this.factorType = factorType;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 供界面直接展示的乘数文本，如「×1.3」「×0.5」。
     */
    public String getFactorLabel() {
        if (factor == null) {
            return null;
        }
        if (factor % 100 == 0) {
            return "×" + (factor / 100);
        }
        return "×" + (factor / 100.0);
    }
}
