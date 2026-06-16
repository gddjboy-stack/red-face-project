package com.redface.dto;

import java.time.LocalDateTime;

/**
 * API-4 我的写真条目。
 */
public class MyPhotoItem {
    private String assetId;
    private String previewUrl;
    private String playerName;
    private LocalDateTime createdAt;

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
