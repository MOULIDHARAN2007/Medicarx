package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class BillItem {
    private int id;
    private int billId;
    private int medicineId;
    private String medicineName;
    private String brand;
    private String dosage;
    private String batchNumber;
    private int quantity;
    private double unitPrice;
    private double subtotal;

    public BillItem() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getBillId() { return billId; }
    public void setBillId(int billId) { this.billId = billId; }

    public int getMedicineId() { return medicineId; }
    public void setMedicineId(int medicineId) { this.medicineId = medicineId; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("billId", billId);
        map.put("medicineId", medicineId);
        map.put("medicineName", medicineName != null ? medicineName : "");
        map.put("brand", brand != null ? brand : "");
        map.put("dosage", dosage != null ? dosage : "");
        map.put("batchNumber", batchNumber != null ? batchNumber : "");
        map.put("quantity", quantity);
        map.put("unitPrice", unitPrice);
        map.put("subtotal", subtotal);
        return map;
    }
}
