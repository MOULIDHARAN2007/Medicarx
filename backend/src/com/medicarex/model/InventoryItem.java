package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class InventoryItem {
    private int id;
    private int pharmacyId;
    private String pharmacyName;
    private int medicineId;
    private String medicineName;
    private String genericName;
    private String category;
    private String form;
    private String batchNumber;
    private int stockQuantity;
    private double unitPrice;
    private String expiryDate;
    private String status; // "In Stock", "Low Stock", "Out of Stock"
    private String lastUpdated;

    public InventoryItem() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPharmacyId() { return pharmacyId; }
    public void setPharmacyId(int pharmacyId) { this.pharmacyId = pharmacyId; }

    public String getPharmacyName() { return pharmacyName; }
    public void setPharmacyName(String pharmacyName) { this.pharmacyName = pharmacyName; }

    public int getMedicineId() { return medicineId; }
    public void setMedicineId(int medicineId) { this.medicineId = medicineId; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public String getGenericName() { return genericName; }
    public void setGenericName(String genericName) { this.genericName = genericName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("pharmacyId", pharmacyId);
        map.put("pharmacyName", pharmacyName);
        map.put("medicineId", medicineId);
        map.put("medicineName", medicineName);
        map.put("genericName", genericName);
        map.put("category", category);
        map.put("form", form);
        map.put("batchNumber", batchNumber);
        map.put("stockQuantity", stockQuantity);
        map.put("unitPrice", unitPrice);
        map.put("expiryDate", expiryDate);
        map.put("status", status != null ? status : (stockQuantity > 20 ? "In Stock" : (stockQuantity > 0 ? "Low Stock" : "Out of Stock")));
        map.put("lastUpdated", lastUpdated);
        return map;
    }
}
