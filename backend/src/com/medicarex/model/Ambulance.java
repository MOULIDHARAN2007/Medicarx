package com.medicarex.model;

public class Ambulance {
    private int id;
    private String vehicleNumber;
    private String vehicleType; // BASIC, ADVANCED_LIFE_SUPPORT, PATIENT_TRANSPORT, ICU
    private String driverName;
    private String driverPhone;
    private Integer driverUserId;
    private String status; // AVAILABLE, BUSY, EN_ROUTE, MAINTENANCE, OFFLINE
    private double currentLatitude;
    private double currentLongitude;
    private String hospitalAffiliation;
    private String equipmentSummary;
    private boolean active;
    private String createdAt;
    private String updatedAt;

    public Ambulance() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getDriverPhone() { return driverPhone; }
    public void setDriverPhone(String driverPhone) { this.driverPhone = driverPhone; }

    public Integer getDriverUserId() { return driverUserId; }
    public void setDriverUserId(Integer driverUserId) { this.driverUserId = driverUserId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getCurrentLatitude() { return currentLatitude; }
    public void setCurrentLatitude(double currentLatitude) { this.currentLatitude = currentLatitude; }

    public double getCurrentLongitude() { return currentLongitude; }
    public void setCurrentLongitude(double currentLongitude) { this.currentLongitude = currentLongitude; }

    public String getHospitalAffiliation() { return hospitalAffiliation; }
    public void setHospitalAffiliation(String hospitalAffiliation) { this.hospitalAffiliation = hospitalAffiliation; }

    public String getEquipmentSummary() { return equipmentSummary; }
    public void setEquipmentSummary(String equipmentSummary) { this.equipmentSummary = equipmentSummary; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", id);
        map.put("vehicleNumber", vehicleNumber);
        map.put("vehicleType", vehicleType);
        map.put("driverName", driverName);
        map.put("driverPhone", driverPhone);
        map.put("driverUserId", driverUserId);
        map.put("status", status);
        map.put("currentLatitude", currentLatitude);
        map.put("currentLongitude", currentLongitude);
        map.put("hospitalAffiliation", hospitalAffiliation);
        map.put("equipmentSummary", equipmentSummary);
        map.put("active", active);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }
}
