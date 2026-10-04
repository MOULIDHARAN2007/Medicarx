package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class HealthRecord {
    private int id;
    private int patientId;
    private String recordType; // Consultations, Prescriptions, Lab Reports, Documents
    private String title;
    private String providerName;
    private String description;
    private String documentUrl;
    private String fileSize;
    private String recordDate;
    private String createdAt;

    public HealthRecord() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

    public String getFileSize() { return fileSize; }
    public void setFileSize(String fileSize) { this.fileSize = fileSize; }

    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String recordDate) { this.recordDate = recordDate; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("patientId", patientId);
        map.put("recordType", recordType);
        map.put("title", title);
        map.put("providerName", providerName);
        map.put("description", description);
        map.put("documentUrl", documentUrl);
        map.put("fileSize", fileSize);
        map.put("recordDate", recordDate);
        map.put("createdAt", createdAt);
        return map;
    }
}
