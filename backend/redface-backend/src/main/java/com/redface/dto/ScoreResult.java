package com.redface.dto;

/**
 * 积分计算结果对象，返回原始人气、系数、衰减后人气值、最终积分和是否触发衰减。
 */
public class ScoreResult {
    private final long popularity;
    private final int coefficient;
    private final long decayedPopularity;
    private final long scoreFinal;
    private final boolean decayApplied;

    public ScoreResult(long popularity, int coefficient, long decayedPopularity, long scoreFinal, boolean decayApplied) {
        this.popularity = popularity;
        this.coefficient = coefficient;
        this.decayedPopularity = decayedPopularity;
        this.scoreFinal = scoreFinal;
        this.decayApplied = decayApplied;
    }

    public long getPopularity() {
        return popularity;
    }

    public int getCoefficient() {
        return coefficient;
    }

    public long getDecayedPopularity() {
        return decayedPopularity;
    }

    public long getScoreFinal() {
        return scoreFinal;
    }

    public boolean isDecayApplied() {
        return decayApplied;
    }
}
