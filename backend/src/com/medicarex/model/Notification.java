package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class Notification {
    private int id;
    private int userId;
    private String type; // All, Medicine, Pharmacy, Prescription, System
    private String title;
    private String message;
    private Integer referenceId;
    private String referenceType;
    private boolean isRead;
    private String timeGroup; // Today, Yesterday, This Week
    private String createdAt;

    public Notification() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Integer getReferenceId() { return referenceId; }
    public void setReferenceId(Integer referenceId) { this.referenceId = referenceId; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public String getTimeGroup() { return timeGroup; }
    public void setTimeGroup(String timeGroup) { this.timeGroup = timeGroup; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("userId", userId);
        map.put("type", type);
        map.put("title", title);
        map.put("message", message);
        map.put("referenceId", referenceId);
        map.put("referenceType", referenceType);
        map.put("isRead", isRead);
        map.put("timeGroup", timeGroup);
        map.put("createdAt", createdAt);
        return map;
    }
}
