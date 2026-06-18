package com.redface.dto;

/**
 * C15 选手详情写真预览项。
 */
public class PlayerPhotoItem {
    private String assetId;
    private String previewUrl;

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }
}
