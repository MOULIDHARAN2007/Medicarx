package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class User {
    private int id;
    private String email;
    private String passwordHash;
    private String salt;
    private String fullName;
    private String phone;
    private String role; // PATIENT, DOCTOR, PHARMACY, ADMIN
    private String avatarUrl;
    private boolean isActive;
    private String authProvider = "LOCAL"; // LOCAL, GOOGLE
    private String googleSubjectId;
    private Integer createdBy;
    private String createdAt;
    private String updatedAt;

    public User() {}

    public User(int id, String email, String passwordHash, String salt, String fullName, String phone, String role, String avatarUrl, boolean isActive) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.avatarUrl = avatarUrl;
        this.isActive = isActive;
        this.authProvider = "LOCAL";
    }

    public User(int id, String email, String passwordHash, String salt, String fullName, String phone, String role, String avatarUrl, boolean isActive, String authProvider, String googleSubjectId) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.avatarUrl = avatarUrl;
        this.isActive = isActive;
        this.authProvider = authProvider != null ? authProvider : "LOCAL";
        this.googleSubjectId = googleSubjectId;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getAuthProvider() { return authProvider; }
    public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }

    public String getGoogleSubjectId() { return googleSubjectId; }
    public void setGoogleSubjectId(String googleSubjectId) { this.googleSubjectId = googleSubjectId; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("email", email);
        map.put("fullName", fullName);
        map.put("phone", phone);
        map.put("role", role);
        map.put("avatarUrl", avatarUrl);
        map.put("isActive", isActive);
        map.put("authProvider", authProvider);
        map.put("googleSubjectId", googleSubjectId);
        map.put("createdBy", createdBy);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }
}

