package com.medicarex.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrescriptionUpload {
    private int id;
    private int patientId;
    private String originalFilename;
    private String storedFilename;
    private String fileType;
    private int fileSize;
    private String extractionStatus;
    private String rawText;
    private String createdAt;
    private List<PrescriptionExtractedMedicine> extractedMedicines = new ArrayList<>();

    public PrescriptionUpload() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getStoredFilename() { return storedFilename; }
    public void setStoredFilename(String storedFilename) { this.storedFilename = storedFilename; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public int getFileSize() { return fileSize; }
    public void setFileSize(int fileSize) { this.fileSize = fileSize; }

    public String getExtractionStatus() { return extractionStatus; }
    public void setExtractionStatus(String extractionStatus) { this.extractionStatus = extractionStatus; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public List<PrescriptionExtractedMedicine> getExtractedMedicines() { return extractedMedicines; }
    public void setExtractedMedicines(List<PrescriptionExtractedMedicine> extractedMedicines) { this.extractedMedicines = extractedMedicines; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("patientId", patientId);
        map.put("originalFilename", originalFilename);
        map.put("storedFilename", storedFilename);
        map.put("fileType", fileType);
        map.put("fileSize", fileSize);
        map.put("extractionStatus", extractionStatus);
        map.put("rawText", rawText);
        map.put("createdAt", createdAt);

        List<Map<String, Object>> items = new ArrayList<>();
        if (extractedMedicines != null) {
            for (PrescriptionExtractedMedicine m : extractedMedicines) {
                items.add(m.toMap());
            }
        }
        map.put("extractedMedicines", items);
        return map;
    }
}
