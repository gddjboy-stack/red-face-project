package com.redface.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * API-4 我的写真响应。
 */
public class MyPhotosResponse {
    private int total;
    private List<MyPhotoItem> items = new ArrayList<>();
    private UserMembershipSummary membership = UserMembershipSummary.inactive();

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public List<MyPhotoItem> getItems() { return items; }
    public void setItems(List<MyPhotoItem> items) { this.items = items; }
    public UserMembershipSummary getMembership() { return membership; }
    public void setMembership(UserMembershipSummary membership) { this.membership = membership; }
}
