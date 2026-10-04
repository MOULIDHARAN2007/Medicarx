package com.medicarex.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Order {
    private int id;
    private String orderNumber;
    private int pharmacyId;
    private Integer patientId;
    private String customerName;
    private String customerPhone;
    private double totalAmount;
    private String status; // Pending, Confirmed, Ready, Completed, Cancelled
    private String paymentMethod;
    private String orderDate;
    private String createdAt;
    private List<OrderItem> items = new ArrayList<>();

    public Order() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public int getPharmacyId() { return pharmacyId; }
    public void setPharmacyId(int pharmacyId) { this.pharmacyId = pharmacyId; }

    public Integer getPatientId() { return patientId; }
    public void setPatientId(Integer patientId) { this.patientId = patientId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("orderNumber", orderNumber);
        map.put("pharmacyId", pharmacyId);
        map.put("patientId", patientId);
        map.put("customerName", customerName);
        map.put("customerPhone", customerPhone);
        map.put("totalAmount", totalAmount);
        map.put("status", status);
        map.put("paymentMethod", paymentMethod);
        map.put("orderDate", orderDate);
        map.put("createdAt", createdAt);

        List<Map<String, Object>> itemsList = new ArrayList<>();
        for (OrderItem item : items) {
            itemsList.add(item.toMap());
        }
        map.put("items", itemsList);
        return map;
    }
}
