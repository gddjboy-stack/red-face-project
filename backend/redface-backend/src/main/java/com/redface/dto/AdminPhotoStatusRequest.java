package com.redface.dto;

/**
 * C17 后台写真状态更新请求。
 */
public class AdminPhotoStatusRequest {
    private String operatorId;
    private String status;

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
