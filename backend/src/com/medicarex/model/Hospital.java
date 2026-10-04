package com.medicarex.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Hospital {
    private int id;
    private String name;
    private String registrationNumber;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private double latitude;
    private double longitude;
    private String phone;
    private String emergencyContact;
    private boolean emergencyAvailable = true; // 24/7 Emergency Service
    private boolean icuAvailable = true;
    private int availableBeds = 10;
    private int totalBeds = 50;
    private boolean acceptingAmbulances = true;
    private boolean active = true;
    private String approvalStatus = "ACTIVE"; // ACTIVE, PENDING_REVIEW, REJECTED
    private Integer createdByUserId;
    private String createdByRole = "ADMIN"; // ADMIN, AMBULANCE
    private String rejectionReason;
    private String createdAt;
    private String updatedAt;

    public Hospital() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

    public boolean isEmergencyAvailable() { return emergencyAvailable; }
    public void setEmergencyAvailable(boolean emergencyAvailable) { this.emergencyAvailable = emergencyAvailable; }

    public boolean isIcuAvailable() { return icuAvailable; }
    public void setIcuAvailable(boolean icuAvailable) { this.icuAvailable = icuAvailable; }

    public int getAvailableBeds() { return availableBeds; }
    public void setAvailableBeds(int availableBeds) { this.availableBeds = availableBeds; }

    public int getTotalBeds() { return totalBeds; }
    public void setTotalBeds(int totalBeds) { this.totalBeds = totalBeds; }

    public boolean isAcceptingAmbulances() { return acceptingAmbulances; }
    public void setAcceptingAmbulances(boolean acceptingAmbulances) { this.acceptingAmbulances = acceptingAmbulances; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public Integer getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Integer createdByUserId) { this.createdByUserId = createdByUserId; }

    public String getCreatedByRole() { return createdByRole; }
    public void setCreatedByRole(String createdByRole) { this.createdByRole = createdByRole; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("registrationNumber", registrationNumber);
        map.put("address", address);
        map.put("city", city);
        map.put("state", state);
        map.put("pincode", pincode);
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("phone", phone);
        map.put("emergencyContact", emergencyContact);
        map.put("emergencyAvailable", emergencyAvailable);
        map.put("icuAvailable", icuAvailable);
        map.put("availableBeds", availableBeds);
        map.put("totalBeds", totalBeds);
        map.put("acceptingAmbulances", acceptingAmbulances);
        map.put("isActive", active);
        map.put("active", active);
        map.put("approvalStatus", approvalStatus != null ? approvalStatus : (active ? "ACTIVE" : "INACTIVE"));
        map.put("status", approvalStatus != null ? approvalStatus : (active ? "ACTIVE" : "INACTIVE"));
        map.put("createdByUserId", createdByUserId);
        map.put("createdByRole", createdByRole != null ? createdByRole : "ADMIN");
        map.put("rejectionReason", rejectionReason);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }
}
