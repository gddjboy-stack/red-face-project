package com.redface.entity;

import java.time.LocalDateTime;

/** 商品原价配置（C20-4B），单价以「分」存储，全链路整数运算避免浮点误差。 */
public class ProductPriceConfig {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";

    private String merchantCode;
    private String productName;
    private long unitPriceCent;
    private String status;
    private String operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getMerchantCode() { return merchantCode; }
    public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public long getUnitPriceCent() { return unitPriceCent; }
    public void setUnitPriceCent(long unitPriceCent) { this.unitPriceCent = unitPriceCent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
