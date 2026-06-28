package com.redface.dto;

public class TokenGenerateRequest {
    private String operatorId;
    private Integer playerId;
    private String photoAssetId;
    private Long points;
    private Integer count;
    private String productSku;
    private String idempotencyKey;

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public Integer getPlayerId() { return playerId; }
    public void setPlayerId(Integer playerId) { this.playerId = playerId; }
    public String getPhotoAssetId() { return photoAssetId; }
    public void setPhotoAssetId(String photoAssetId) { this.photoAssetId = photoAssetId; }
    public Long getPoints() { return points; }
    public void setPoints(Long points) { this.points = points; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public String getProductSku() { return productSku; }
    public void setProductSku(String productSku) { this.productSku = productSku; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
