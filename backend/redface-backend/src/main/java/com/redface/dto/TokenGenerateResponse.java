package com.redface.dto;

public class TokenGenerateResponse {
    private String batchId;
    private int generatedCount;

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public int getGeneratedCount() { return generatedCount; }
    public void setGeneratedCount(int generatedCount) { this.generatedCount = generatedCount; }
}
