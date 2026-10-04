package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class AccountApplication {
    private int id;
    private int userId;
    private String applicantName;
    private String email;
    private String phone;
    private String role; // DOCTOR or PHARMACY
    private String status; // PENDING, APPROVED, REJECTED
    private String rejectionReason;
    private String detailsJson;
    private String submittedAt;
    private String reviewedAt;
    private Integer reviewedBy;

    // Extra flattened metadata for easy JSON rendering
    private Map<String, Object> details = new HashMap<>();

    public AccountApplication() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }

    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }

    public String getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(String reviewedAt) { this.reviewedAt = reviewedAt; }

    public Integer getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Integer reviewedBy) { this.reviewedBy = reviewedBy; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("userId", userId);
        m.put("applicantName", applicantName);
        m.put("email", email);
        m.put("phone", phone);
        m.put("role", role);
        m.put("status", status);
        m.put("rejectionReason", rejectionReason);
        m.put("submittedAt", submittedAt);
        m.put("reviewedAt", reviewedAt);
        m.put("reviewedBy", reviewedBy);
        if (details != null && !details.isEmpty()) {
            m.put("details", details);
            // also spread key attributes for straightforward table display
            for (Map.Entry<String, Object> e : details.entrySet()) {
                if (!m.containsKey(e.getKey())) {
                    m.put(e.getKey(), e.getValue());
                }
            }
        }
        return m;
    }
}
