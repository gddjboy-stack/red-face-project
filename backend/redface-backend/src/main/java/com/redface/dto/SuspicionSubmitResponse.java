package com.redface.dto;

/**
 * C13 真相识破提交成功响应。
 */
public class SuspicionSubmitResponse {
    private Integer roundId;
    private boolean submitted;
    private java.util.List<Integer> accepted;
    private java.util.List<Integer> duplicated;
    private String message;

    public Integer getRoundId() { return roundId; }
    public void setRoundId(Integer roundId) { this.roundId = roundId; }
    public boolean isSubmitted() { return submitted; }
    public void setSubmitted(boolean submitted) { this.submitted = submitted; }
    public java.util.List<Integer> getAccepted() { return accepted; }
    public java.util.List<Integer> getDuplicated() { return duplicated; }
    public void setAccepted(java.util.List<Integer> accepted) { this.accepted = accepted; }
    public void setDuplicated(java.util.List<Integer> duplicated) { this.duplicated = duplicated; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
