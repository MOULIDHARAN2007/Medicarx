package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class OrderItem {
    private int id;
    private int orderId;
    private Integer medicineId;
    private String medicineName;
    private int quantity;
    private double unitPrice;

    public OrderItem() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public Integer getMedicineId() { return medicineId; }
    public void setMedicineId(Integer medicineId) { this.medicineId = medicineId; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("orderId", orderId);
        map.put("medicineId", medicineId);
        map.put("medicineName", medicineName);
        map.put("quantity", quantity);
        map.put("unitPrice", unitPrice);
        map.put("itemTotal", quantity * unitPrice);
        return map;
    }
}
