package com.redface.entity;

import java.time.LocalDateTime;

/**
 * C20-4A 直播数据来源水位线实体。全场维度，与选手无关。
 */
public class LiveMetricWatermark {

    private String metricType;
    private long lastTotal;
    private String sessionSeq;
    private Long prevTotal;
    private String prevSessionSeq;
    private LocalDateTime calibratedAt;
    private int entryCount;
    private String operatorId;
    private LocalDateTime updatedAt;

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public long getLastTotal() {
        return lastTotal;
    }

    public void setLastTotal(long lastTotal) {
        this.lastTotal = lastTotal;
    }

    public String getSessionSeq() {
        return sessionSeq;
    }

    public void setSessionSeq(String sessionSeq) {
        this.sessionSeq = sessionSeq;
    }

    public Long getPrevTotal() {
        return prevTotal;
    }

    public void setPrevTotal(Long prevTotal) {
        this.prevTotal = prevTotal;
    }

    public String getPrevSessionSeq() {
        return prevSessionSeq;
    }

    public void setPrevSessionSeq(String prevSessionSeq) {
        this.prevSessionSeq = prevSessionSeq;
    }

    public LocalDateTime getCalibratedAt() {
        return calibratedAt;
    }

    public void setCalibratedAt(LocalDateTime calibratedAt) {
        this.calibratedAt = calibratedAt;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(int entryCount) {
        this.entryCount = entryCount;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
