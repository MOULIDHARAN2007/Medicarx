package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class InventoryTransaction {
    private int id;
    private int pharmacyId;
    private int medicineId;
    private String medicineName;
    private String transactionType; // SALE, RESTOCK, MANUAL_ADJUSTMENT, CLEAR_STOCK, IMPORT, CANCEL, REFUND
    private int quantityChange;
    private int previousStock;
    private int newStock;
    private String referenceId; // e.g., Bill number "INV-1001" or Adjustment note
    private String notes;
    private String createdAt;
    private String createdBy;

    public InventoryTransaction() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPharmacyId() { return pharmacyId; }
    public void setPharmacyId(int pharmacyId) { this.pharmacyId = pharmacyId; }

    public int getMedicineId() { return medicineId; }
    public void setMedicineId(int medicineId) { this.medicineId = medicineId; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public int getQuantityChange() { return quantityChange; }
    public void setQuantityChange(int quantityChange) { this.quantityChange = quantityChange; }

    public int getPreviousStock() { return previousStock; }
    public void setPreviousStock(int previousStock) { this.previousStock = previousStock; }

    public int getNewStock() { return newStock; }
    public void setNewStock(int newStock) { this.newStock = newStock; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("pharmacyId", pharmacyId);
        map.put("medicineId", medicineId);
        map.put("medicineName", medicineName != null ? medicineName : "");
        map.put("transactionType", transactionType != null ? transactionType : "");
        map.put("quantityChange", quantityChange);
        map.put("previousStock", previousStock);
        map.put("newStock", newStock);
        map.put("referenceId", referenceId != null ? referenceId : "");
        map.put("notes", notes != null ? notes : "");
        map.put("createdAt", createdAt != null ? createdAt : "");
        map.put("createdBy", createdBy != null ? createdBy : "");
        return map;
    }
}
