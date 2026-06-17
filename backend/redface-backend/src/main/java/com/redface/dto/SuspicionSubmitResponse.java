package com.redface.dto;

/**
 * C13 真相识破提交成功响应。
 */
public class SuspicionSubmitResponse {
    private Integer roundId;
    private boolean submitted;
    private Integer submittedPlayerId;
    private String message;

    public Integer getRoundId() { return roundId; }
    public void setRoundId(Integer roundId) { this.roundId = roundId; }
    public boolean isSubmitted() { return submitted; }
    public void setSubmitted(boolean submitted) { this.submitted = submitted; }
    public Integer getSubmittedPlayerId() { return submittedPlayerId; }
    public void setSubmittedPlayerId(Integer submittedPlayerId) { this.submittedPlayerId = submittedPlayerId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
