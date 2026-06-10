package com.redface.model;

import jakarta.persistence.*;
import com.redface.config.AppConstants;
import java.time.LocalDateTime;

@Entity
@Table(name = "tokens")
public class Token {

    @Id
    @Column(name = "token_id", length = AppConstants.TOKEN_TOTAL_LENGTH)
    private String tokenId;

    @Column(name = "player_id", nullable = false)
    private int playerId;

    @Column(name = "points", nullable = false)
    private long points;

    @Column(name = "photo_asset_id", length = 64)
    private String photoAssetId;

    @Column(name = "product_sku", length = 64)
    private String productSku;

    @Column(name = "aqiso_batch_id", length = 64)
    private String aqisoBatchId;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "redeem_source", length = 20)
    private String redeemSource;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Getters and Setters
    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
