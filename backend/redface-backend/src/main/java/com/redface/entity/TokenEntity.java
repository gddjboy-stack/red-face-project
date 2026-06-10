package com.redface.entity;

import java.time.LocalDateTime;

/**
 * tokens 表实体，表示一张可核销的卡密。
 */
public class TokenEntity {
    private String tokenId;
    private Integer playerId;
    private long points;
    private String photoAssetId;
    private String productSku;
    private String aqisoBatchId;
    private String status;
    private String orderId;
    private String userId;
    private String redeemSource;
    private LocalDateTime usedAt;

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public long getPoints() {
        return points;
    }

    public void setPoints(long points) {
        this.points = points;
    }

    public String getPhotoAssetId() {
        return photoAssetId;
    }

    public void setPhotoAssetId(String photoAssetId) {
        this.photoAssetId = photoAssetId;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public String getAqisoBatchId() {
        return aqisoBatchId;
    }

    public void setAqisoBatchId(String aqisoBatchId) {
        this.aqisoBatchId = aqisoBatchId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRedeemSource() {
        return redeemSource;
    }

    public void setRedeemSource(String redeemSource) {
        this.redeemSource = redeemSource;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
