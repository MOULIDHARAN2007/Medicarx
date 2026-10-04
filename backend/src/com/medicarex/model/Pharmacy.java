package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class Pharmacy {
    private int id;
    private Integer userId;
    private String pharmacyName;
    private String licenseNumber;
    private String ownerName;
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private double latitude;
    private double longitude;
    private String phone;
    private String email;
    private String openingHours;
    private boolean is24Hours;
    private boolean isVerified;
    private double rating;
    private String status;
    private double distanceKm; // Computed dynamically for location queries
    private Double medicinePrice; // When querying for specific medicine
    private String medicineStockStatus; // When querying for specific medicine
    private int medicineStockQty;

    public Pharmacy() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getPharmacyName() { return pharmacyName; }
    public void setPharmacyName(String pharmacyName) { this.pharmacyName = pharmacyName; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

    public boolean is24Hours() { return is24Hours; }
    public void set24Hours(boolean a24Hours) { is24Hours = a24Hours; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }

    public Double getMedicinePrice() { return medicinePrice; }
    public void setMedicinePrice(Double medicinePrice) { this.medicinePrice = medicinePrice; }

    public String getMedicineStockStatus() { return medicineStockStatus; }
    public void setMedicineStockStatus(String medicineStockStatus) { this.medicineStockStatus = medicineStockStatus; }

    public int getMedicineStockQty() { return medicineStockQty; }
    public void setMedicineStockQty(int medicineStockQty) { this.medicineStockQty = medicineStockQty; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("userId", userId);
        map.put("pharmacyName", pharmacyName);
        map.put("licenseNumber", licenseNumber);
        map.put("ownerName", ownerName);
        map.put("address", address);
        map.put("city", city);
        map.put("state", state);
        map.put("postalCode", postalCode);
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("phone", phone);
        map.put("email", email);
        map.put("openingHours", openingHours);
        map.put("is24Hours", is24Hours);
        map.put("isVerified", isVerified);
        map.put("rating", rating);
        map.put("status", status);
        map.put("distanceKm", Math.round(distanceKm * 10.0) / 10.0);
        if (medicinePrice != null) map.put("medicinePrice", medicinePrice);
        if (medicineStockStatus != null) map.put("medicineStockStatus", medicineStockStatus);
        map.put("medicineStockQty", medicineStockQty);
        return map;
    }
}
