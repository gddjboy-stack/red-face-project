package com.redface.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * C13 真相识破状态响应。用户侧不返回真实卧底身份。
 */
public class SuspicionStatusResponse {
    private Integer roundId;
    private String roundName;
    private boolean open;
    private boolean submitted;
    private Integer submittedPlayerId;
    private List<SuspicionCandidateView> candidates = new ArrayList<>();
    private long updatedAt;

    public Integer getRoundId() { return roundId; }
    public void setRoundId(Integer roundId) { this.roundId = roundId; }
    public String getRoundName() { return roundName; }
    public void setRoundName(String roundName) { this.roundName = roundName; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public boolean isSubmitted() { return submitted; }
    public void setSubmitted(boolean submitted) { this.submitted = submitted; }
    public Integer getSubmittedPlayerId() { return submittedPlayerId; }
    public void setSubmittedPlayerId(Integer submittedPlayerId) { this.submittedPlayerId = submittedPlayerId; }
    public List<SuspicionCandidateView> getCandidates() { return candidates; }
    public void setCandidates(List<SuspicionCandidateView> candidates) { this.candidates = candidates; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
