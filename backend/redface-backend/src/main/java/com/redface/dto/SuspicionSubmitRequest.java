package com.redface.dto;

/**
 * C13 真相识破提交请求。userId 只能从 Bearer 登录态注入。
 */
public class SuspicionSubmitRequest {
    private Integer roundId;
    private Integer suspectPlayerId;

    public Integer getRoundId() { return roundId; }
    public void setRoundId(Integer roundId) { this.roundId = roundId; }
    public Integer getSuspectPlayerId() { return suspectPlayerId; }
    public void setSuspectPlayerId(Integer suspectPlayerId) { this.suspectPlayerId = suspectPlayerId; }
}
