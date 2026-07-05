package com.redface.dto;

/**
 * C13 真相识破提交请求。userId 只能从 Bearer 登录态注入。
 */
public class SuspicionSubmitRequest {
    private Integer roundId;
    private java.util.List<Integer> suspectPlayerIds;

    public Integer getRoundId() { return roundId; }
    public void setRoundId(Integer roundId) { this.roundId = roundId; }
    public java.util.List<Integer> getSuspectPlayerIds() { return suspectPlayerIds; }
    public void setSuspectPlayerIds(java.util.List<Integer> suspectPlayerIds) { this.suspectPlayerIds = suspectPlayerIds; }
}
