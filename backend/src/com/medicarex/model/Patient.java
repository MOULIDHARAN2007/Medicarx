package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class Patient {
    private int id;
    private int userId;
    private String dob;
    private String gender;
    private String bloodGroup;
    private String address;
    private String emergencyContact;
    private String insuranceProvider;
    private String createdAt;

    public Patient() {}

    public Patient(int id, int userId, String dob, String gender, String bloodGroup, String address, String emergencyContact, String insuranceProvider) {
        this.id = id;
        this.userId = userId;
        this.dob = dob;
        this.gender = gender;
        this.bloodGroup = bloodGroup;
        this.address = address;
        this.emergencyContact = emergencyContact;
        this.insuranceProvider = insuranceProvider;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

    public String getInsuranceProvider() { return insuranceProvider; }
    public void setInsuranceProvider(String insuranceProvider) { this.insuranceProvider = insuranceProvider; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("userId", userId);
        m.put("dob", dob);
        m.put("gender", gender);
        m.put("bloodGroup", bloodGroup);
        m.put("address", address);
        m.put("emergencyContact", emergencyContact);
        m.put("insuranceProvider", insuranceProvider);
        return m;
    }
}
