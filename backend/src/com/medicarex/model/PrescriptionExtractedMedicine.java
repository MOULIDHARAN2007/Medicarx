package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class PrescriptionExtractedMedicine {
    private int id;
    private int uploadId;
    private Integer medicineId;
    private String extractedName;
    private String extractedDosage;
    private double confidence;
    private boolean isConfirmed;
    private String createdAt;

    // Extra display fields from catalog match
    private String catalogName;
    private String genericName;
    private String brand;
    private String category;
    private Double basePrice;
    private Integer totalStock;
    private Integer pharmacyCount;

    public PrescriptionExtractedMedicine() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUploadId() { return uploadId; }
    public void setUploadId(int uploadId) { this.uploadId = uploadId; }

    public Integer getMedicineId() { return medicineId; }
    public void setMedicineId(Integer medicineId) { this.medicineId = medicineId; }

    public String getExtractedName() { return extractedName; }
    public void setExtractedName(String extractedName) { this.extractedName = extractedName; }

    public String getExtractedDosage() { return extractedDosage; }
    public void setExtractedDosage(String extractedDosage) { this.extractedDosage = extractedDosage; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public boolean isConfirmed() { return isConfirmed; }
    public void setConfirmed(boolean confirmed) { isConfirmed = confirmed; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getCatalogName() { return catalogName; }
    public void setCatalogName(String catalogName) { this.catalogName = catalogName; }

    public String getGenericName() { return genericName; }
    public void setGenericName(String genericName) { this.genericName = genericName; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getBasePrice() { return basePrice; }
    public void setBasePrice(Double basePrice) { this.basePrice = basePrice; }

    public Integer getTotalStock() { return totalStock; }
    public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }

    public Integer getPharmacyCount() { return pharmacyCount; }
    public void setPharmacyCount(Integer pharmacyCount) { this.pharmacyCount = pharmacyCount; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("uploadId", uploadId);
        map.put("name", extractedName);
        map.put("extractedName", extractedName);
        map.put("dosage", extractedDosage != null ? extractedDosage : "");
        map.put("extractedDosage", extractedDosage != null ? extractedDosage : "");
        map.put("confidence", Math.round(confidence * 100.0) / 100.0);
        map.put("matchedMedicineId", medicineId);
        map.put("medicineId", medicineId);
        map.put("isConfirmed", isConfirmed);
        map.put("createdAt", createdAt);

        if (catalogName != null) map.put("catalogName", catalogName);
        if (genericName != null) map.put("genericName", genericName);
        if (brand != null) map.put("brand", brand);
        if (category != null) map.put("category", category);
        if (basePrice != null) map.put("basePrice", basePrice);
        if (totalStock != null) map.put("totalStock", totalStock);
        if (pharmacyCount != null) map.put("pharmacyCount", pharmacyCount);

        return map;
    }
}
