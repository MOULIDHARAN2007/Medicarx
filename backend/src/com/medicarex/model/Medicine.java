package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class Medicine {
    private int id;
    private String name;
    private String genericName;
    private String brand;
    private String category;
    private String dosage;
    private String form;
    private boolean prescriptionRequired;
    private String description;
    private String uses;
    private String sideEffects;
    private String importantInfo;
    private String storageInfo;
    private String imageUrl;
    private double basePrice;
    private boolean isActive;
    private String barcode;
    private int totalStock;
    private int pharmacyCount;
    private String overallStatus; // "In Stock", "Low Stock", "Out of Stock"

    public Medicine() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGenericName() { return genericName; }
    public void setGenericName(String genericName) { this.genericName = genericName; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }

    public boolean isPrescriptionRequired() { return prescriptionRequired; }
    public void setPrescriptionRequired(boolean prescriptionRequired) { this.prescriptionRequired = prescriptionRequired; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getUses() { return uses; }
    public void setUses(String uses) { this.uses = uses; }

    public String getSideEffects() { return sideEffects; }
    public void setSideEffects(String sideEffects) { this.sideEffects = sideEffects; }

    public String getImportantInfo() { return importantInfo; }
    public void setImportantInfo(String importantInfo) { this.importantInfo = importantInfo; }

    public String getStorageInfo() { return storageInfo; }
    public void setStorageInfo(String storageInfo) { this.storageInfo = storageInfo; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public int getTotalStock() { return totalStock; }
    public void setTotalStock(int totalStock) { this.totalStock = totalStock; }

    public int getPharmacyCount() { return pharmacyCount; }
    public void setPharmacyCount(int pharmacyCount) { this.pharmacyCount = pharmacyCount; }

    public String getOverallStatus() { return overallStatus; }
    public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("barcode", barcode != null ? barcode : "");
        map.put("name", name);
        map.put("genericName", genericName);
        map.put("brand", brand);
        map.put("category", category);
        map.put("dosage", dosage);
        map.put("form", form);
        map.put("prescriptionRequired", prescriptionRequired);
        map.put("description", description);
        map.put("uses", uses);
        map.put("sideEffects", sideEffects);
        map.put("importantInfo", importantInfo);
        map.put("storageInfo", storageInfo);
        map.put("imageUrl", imageUrl);
        map.put("basePrice", basePrice);
        map.put("isActive", isActive);
        map.put("totalStock", totalStock);
        map.put("pharmacyCount", pharmacyCount);
        map.put("overallStatus", overallStatus != null ? overallStatus : (totalStock > 20 ? "In Stock" : (totalStock > 0 ? "Low Stock" : "Out of Stock")));
        return map;
    }
}
