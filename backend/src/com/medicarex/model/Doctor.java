package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class Doctor {
    private int id;
    private int userId;
    private String specialization;
    private String licenseNumber;
    private String hospitalAffiliation;
    private int experienceYears;
    private double consultationFee;
    private double rating;
    private String availableHours;
    private String createdAt;

    public Doctor() {}

    public Doctor(int id, int userId, String specialization, String licenseNumber, String hospitalAffiliation, int experienceYears, double consultationFee, double rating, String availableHours) {
        this.id = id;
        this.userId = userId;
        this.specialization = specialization;
        this.licenseNumber = licenseNumber;
        this.hospitalAffiliation = hospitalAffiliation;
        this.experienceYears = experienceYears;
        this.consultationFee = consultationFee;
        this.rating = rating;
        this.availableHours = availableHours;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getHospitalAffiliation() { return hospitalAffiliation; }
    public void setHospitalAffiliation(String hospitalAffiliation) { this.hospitalAffiliation = hospitalAffiliation; }

    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int experienceYears) { this.experienceYears = experienceYears; }

    public double getConsultationFee() { return consultationFee; }
    public void setConsultationFee(double consultationFee) { this.consultationFee = consultationFee; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public String getAvailableHours() { return availableHours; }
    public void setAvailableHours(String availableHours) { this.availableHours = availableHours; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("userId", userId);
        m.put("specialization", specialization);
        m.put("licenseNumber", licenseNumber);
        m.put("hospitalAffiliation", hospitalAffiliation);
        m.put("experienceYears", experienceYears);
        m.put("consultationFee", consultationFee);
        m.put("rating", rating);
        m.put("availableHours", availableHours);
        return m;
    }
}
