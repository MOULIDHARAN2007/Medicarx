package com.medicarex.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bill {
    private int id;
    private int pharmacyId;
    private String pharmacyName;
    private String pharmacyAddress;
    private String pharmacyPhone;
    private String billNumber;
    private Integer patientId;
    private String customerName;
    private String customerPhone;
    private Integer prescriptionId;
    private double subtotal;
    private double discount;
    private double tax;
    private double totalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String notes;
    private String createdAt;
    private String createdBy;
    private List<BillItem> items = new ArrayList<>();

    public Bill() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPharmacyId() { return pharmacyId; }
    public void setPharmacyId(int pharmacyId) { this.pharmacyId = pharmacyId; }

    public String getPharmacyName() { return pharmacyName; }
    public void setPharmacyName(String pharmacyName) { this.pharmacyName = pharmacyName; }

    public String getPharmacyAddress() { return pharmacyAddress; }
    public void setPharmacyAddress(String pharmacyAddress) { this.pharmacyAddress = pharmacyAddress; }

    public String getPharmacyPhone() { return pharmacyPhone; }
    public void setPharmacyPhone(String pharmacyPhone) { this.pharmacyPhone = pharmacyPhone; }

    public String getBillNumber() { return billNumber; }
    public void setBillNumber(String billNumber) { this.billNumber = billNumber; }

    public Integer getPatientId() { return patientId; }
    public void setPatientId(Integer patientId) { this.patientId = patientId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public Integer getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(Integer prescriptionId) { this.prescriptionId = prescriptionId; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }

    public double getTax() { return tax; }
    public void setTax(double tax) { this.tax = tax; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public List<BillItem> getItems() { return items; }
    public void setItems(List<BillItem> items) { this.items = items; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("pharmacyId", pharmacyId);
        map.put("pharmacyName", pharmacyName != null ? pharmacyName : "");
        map.put("pharmacyAddress", pharmacyAddress != null ? pharmacyAddress : "");
        map.put("pharmacyPhone", pharmacyPhone != null ? pharmacyPhone : "");
        map.put("billNumber", billNumber != null ? billNumber : "");
        map.put("patientId", patientId);
        map.put("customerName", customerName != null ? customerName : "");
        map.put("customerPhone", customerPhone != null ? customerPhone : "");
        map.put("prescriptionId", prescriptionId);
        map.put("subtotal", subtotal);
        map.put("discount", discount);
        map.put("tax", tax);
        map.put("totalAmount", totalAmount);
        map.put("paymentMethod", paymentMethod != null ? paymentMethod : "CASH");
        map.put("paymentStatus", paymentStatus != null ? paymentStatus : "PAID");
        map.put("notes", notes != null ? notes : "");
        map.put("createdAt", createdAt != null ? createdAt : "");
        map.put("createdBy", createdBy != null ? createdBy : "");

        List<Map<String, Object>> itemMaps = new ArrayList<>();
        if (items != null) {
            for (BillItem item : items) {
                itemMaps.add(item.toMap());
            }
        }
        map.put("items", itemMaps);
        map.put("itemCount", items != null ? items.size() : 0);
        return map;
    }
}
