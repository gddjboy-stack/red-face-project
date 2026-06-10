package com.redface.dto;

/**
 * 积分计算结果对象。C4 将补全衰减与系数计算逻辑。
 */
public class ScoreResult {
    private final long score;

    public ScoreResult(long score) {
        this.score = score;
    }

    public long getScore() {
        return score;
    }
}
