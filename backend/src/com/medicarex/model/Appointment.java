package com.medicarex.model;

import java.util.HashMap;
import java.util.Map;

public class Appointment {
    private int id;
    private int doctorId;
    private String doctorName;
    private int patientId;
    private String patientName;
    private String patientPhone;
    private String appointmentDate;
    private String appointmentTime;
    private String type;
    private String status; // Confirmed, Pending, Completed, Cancelled
    private String notes;
    private String createdAt;

    private String doctorSpecialization;
    private String hospitalAffiliation;
    private String doctorAvatarUrl;
    private int doctorUserId;
    private int patientUserId;
    private String patientEmail;

    public Appointment() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getDoctorSpecialization() { return doctorSpecialization; }
    public void setDoctorSpecialization(String doctorSpecialization) { this.doctorSpecialization = doctorSpecialization; }

    public String getHospitalAffiliation() { return hospitalAffiliation; }
    public void setHospitalAffiliation(String hospitalAffiliation) { this.hospitalAffiliation = hospitalAffiliation; }

    public String getDoctorAvatarUrl() { return doctorAvatarUrl; }
    public void setDoctorAvatarUrl(String doctorAvatarUrl) { this.doctorAvatarUrl = doctorAvatarUrl; }

    public int getDoctorUserId() { return doctorUserId; }
    public void setDoctorUserId(int doctorUserId) { this.doctorUserId = doctorUserId; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public int getPatientUserId() { return patientUserId; }
    public void setPatientUserId(int patientUserId) { this.patientUserId = patientUserId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }

    public String getPatientEmail() { return patientEmail; }
    public void setPatientEmail(String patientEmail) { this.patientEmail = patientEmail; }

    public String getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(String appointmentDate) { this.appointmentDate = appointmentDate; }

    public String getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(String appointmentTime) { this.appointmentTime = appointmentTime; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getReason() { return notes; }
    public void setReason(String reason) { this.notes = reason; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("doctorId", doctorId);
        map.put("doctorName", doctorName);
        map.put("doctorSpecialization", doctorSpecialization != null ? doctorSpecialization : "");
        map.put("specialization", doctorSpecialization != null ? doctorSpecialization : "");
        map.put("hospitalAffiliation", hospitalAffiliation != null ? hospitalAffiliation : "");
        map.put("hospitalName", hospitalAffiliation != null ? hospitalAffiliation : "");
        map.put("doctorAvatarUrl", doctorAvatarUrl != null ? doctorAvatarUrl : "");
        map.put("doctorUserId", doctorUserId);
        map.put("patientId", patientId);
        map.put("patientUserId", patientUserId);
        map.put("patientName", patientName);
        map.put("patientPhone", patientPhone);
        map.put("patientEmail", patientEmail != null ? patientEmail : "");
        map.put("appointmentDate", appointmentDate);
        map.put("appointmentTime", appointmentTime);
        map.put("type", type);
        map.put("status", status);
        map.put("notes", notes);
        map.put("reason", notes);
        map.put("createdAt", createdAt);
        return map;
    }
}
