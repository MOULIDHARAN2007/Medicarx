package com.medicarex.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreatmentGoal {
    private int id;
    private int patientId;
    private int doctorId;
    private String doctorName;
    private String title;
    private String metricName;
    private String unit;
    private String baselineValue;
    private String targetValue;
    private String latestValue;
    private String status; // NOT_STARTED, IN_PROGRESS, IMPROVING, STABLE, COMPLETED
    private String createdAt;
    private String updatedAt;
    private List<TreatmentProgressEntry> progressEntries = new ArrayList<>();

    public TreatmentGoal() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getBaselineValue() { return baselineValue; }
    public void setBaselineValue(String baselineValue) { this.baselineValue = baselineValue; }

    public String getTargetValue() { return targetValue; }
    public void setTargetValue(String targetValue) { this.targetValue = targetValue; }

    public String getLatestValue() { return latestValue; }
    public void setLatestValue(String latestValue) { this.latestValue = latestValue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public List<TreatmentProgressEntry> getProgressEntries() { return progressEntries; }
    public void setProgressEntries(List<TreatmentProgressEntry> progressEntries) { this.progressEntries = progressEntries; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("patientId", patientId);
        map.put("doctorId", doctorId);
        map.put("doctorName", doctorName != null ? doctorName : "");
        map.put("title", title != null ? title : "");
        map.put("metricName", metricName != null ? metricName : "");
        map.put("unit", unit != null ? unit : "");
        map.put("baselineValue", baselineValue != null ? baselineValue : "");
        map.put("targetValue", targetValue != null ? targetValue : "");
        map.put("latestValue", latestValue != null ? latestValue : baselineValue);
        map.put("status", status != null ? status : "IN_PROGRESS");
        map.put("createdAt", createdAt != null ? createdAt : "");
        map.put("updatedAt", updatedAt != null ? updatedAt : "");

        List<Map<String, Object>> entriesList = new ArrayList<>();
        if (progressEntries != null) {
            for (TreatmentProgressEntry entry : progressEntries) {
                entriesList.add(entry.toMap());
            }
        }
        map.put("progressEntries", entriesList);
        map.put("entryCount", progressEntries != null ? progressEntries.size() : 0);
        return map;
    }
}
