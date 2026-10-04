package com.medicarex.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Prescription {
    private int id;
    private String prescriptionNumber;
    private int doctorId;
    private String doctorName;
    private String doctorSpecialization;
    private String doctorLicense;
    private String hospitalAffiliation;
    private int patientId;
    private String patientName;
    private String patientGender;
    private String patientDob;
    private String diagnosis;
    private String notes;
    private String doctorSignature;
    private String status;
    private String issueDate;
    private String validUntil;
    private String createdAt;
    private List<Map<String, Object>> items = new ArrayList<>();

    public Prescription() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPrescriptionNumber() { return prescriptionNumber; }
    public void setPrescriptionNumber(String prescriptionNumber) { this.prescriptionNumber = prescriptionNumber; }

    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getDoctorSpecialization() { return doctorSpecialization; }
    public void setDoctorSpecialization(String doctorSpecialization) { this.doctorSpecialization = doctorSpecialization; }

    public String getDoctorLicense() { return doctorLicense; }
    public void setDoctorLicense(String doctorLicense) { this.doctorLicense = doctorLicense; }

    public String getHospitalAffiliation() { return hospitalAffiliation; }
    public void setHospitalAffiliation(String hospitalAffiliation) { this.hospitalAffiliation = hospitalAffiliation; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getPatientGender() { return patientGender; }
    public void setPatientGender(String patientGender) { this.patientGender = patientGender; }

    public String getPatientDob() { return patientDob; }
    public void setPatientDob(String patientDob) { this.patientDob = patientDob; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDoctorSignature() { return doctorSignature; }
    public void setDoctorSignature(String doctorSignature) { this.doctorSignature = doctorSignature; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIssueDate() { return issueDate; }
    public void setIssueDate(String issueDate) { this.issueDate = issueDate; }

    public String getValidUntil() { return validUntil; }
    public void setValidUntil(String validUntil) { this.validUntil = validUntil; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("prescriptionNumber", prescriptionNumber);
        map.put("doctorId", doctorId);
        map.put("doctorName", doctorName);
        map.put("doctorSpecialization", doctorSpecialization);
        map.put("doctorLicense", doctorLicense);
        map.put("hospitalAffiliation", hospitalAffiliation);
        map.put("patientId", patientId);
        map.put("patientName", patientName);
        map.put("patientGender", patientGender);
        map.put("patientDob", patientDob);
        map.put("diagnosis", diagnosis);
        map.put("notes", notes);
        map.put("doctorSignature", doctorSignature);
        map.put("status", status);
        map.put("issueDate", issueDate);
        map.put("validUntil", validUntil);
        map.put("createdAt", createdAt);
        map.put("items", items);
        return map;
    }
}
