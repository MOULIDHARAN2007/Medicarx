package com.medicarex.model;

public class EmergencyRequest {
    private int id;
    private int patientId;
    private String emergencyType; // CARDIAC, ACCIDENT, BREATHING, MATERNITY, STROKE, GENERAL
    private String severity; // CRITICAL, HIGH, MODERATE, LOW
    private double pickupLatitude;
    private double pickupLongitude;
    private String pickupAddress;
    private String landmark;
    private String contactPhone;
    private String patientNotes;
    private Integer assignedAmbulanceId;
    private Integer driverUserId;
    private String status; // REQUESTED, ASSIGNED, ACCEPTED, EN_ROUTE, ARRIVED, PATIENT_PICKED_UP, TRANSPORTING, ARRIVED_AT_HOSPITAL, COMPLETED, CANCELLED
    private Integer estimatedArrivalMinutes;
    private String cancellationReason;
    private String requestedAt;
    private String acceptedAt;
    private String enRouteAt;
    private String arrivedPickupAt;
    private String pickedUpAt;
    private String arrivedHospitalAt;
    private String completedAt;
    private String cancelledAt;

    // Joined info for responses
    private String patientName;
    private String ambulanceVehicleNumber;
    private String ambulanceVehicleType;
    private String driverName;
    private String driverPhone;
    private Double ambulanceLat;
    private Double ambulanceLng;
    private String hospitalAffiliation;
    private Double hospitalLatitude;
    private Double hospitalLongitude;

    public EmergencyRequest() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public String getEmergencyType() { return emergencyType; }
    public void setEmergencyType(String emergencyType) { this.emergencyType = emergencyType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public double getPickupLatitude() { return pickupLatitude; }
    public void setPickupLatitude(double pickupLatitude) { this.pickupLatitude = pickupLatitude; }

    public double getPickupLongitude() { return pickupLongitude; }
    public void setPickupLongitude(double pickupLongitude) { this.pickupLongitude = pickupLongitude; }

    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    public String getLandmark() { return landmark; }
    public void setLandmark(String landmark) { this.landmark = landmark; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getPatientNotes() { return patientNotes; }
    public void setPatientNotes(String patientNotes) { this.patientNotes = patientNotes; }

    public Integer getAssignedAmbulanceId() { return assignedAmbulanceId; }
    public void setAssignedAmbulanceId(Integer assignedAmbulanceId) { this.assignedAmbulanceId = assignedAmbulanceId; }

    public Integer getDriverUserId() { return driverUserId; }
    public void setDriverUserId(Integer driverUserId) { this.driverUserId = driverUserId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getEstimatedArrivalMinutes() { return estimatedArrivalMinutes; }
    public void setEstimatedArrivalMinutes(Integer estimatedArrivalMinutes) { this.estimatedArrivalMinutes = estimatedArrivalMinutes; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public String getRequestedAt() { return requestedAt; }
    public void setRequestedAt(String requestedAt) { this.requestedAt = requestedAt; }

    public String getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(String acceptedAt) { this.acceptedAt = acceptedAt; }

    public String getEnRouteAt() { return enRouteAt; }
    public void setEnRouteAt(String enRouteAt) { this.enRouteAt = enRouteAt; }

    public String getArrivedPickupAt() { return arrivedPickupAt; }
    public void setArrivedPickupAt(String arrivedPickupAt) { this.arrivedPickupAt = arrivedPickupAt; }

    public String getPickedUpAt() { return pickedUpAt; }
    public void setPickedUpAt(String pickedUpAt) { this.pickedUpAt = pickedUpAt; }

    public String getArrivedHospitalAt() { return arrivedHospitalAt; }
    public void setArrivedHospitalAt(String arrivedHospitalAt) { this.arrivedHospitalAt = arrivedHospitalAt; }

    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }

    public String getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(String cancelledAt) { this.cancelledAt = cancelledAt; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getAmbulanceVehicleNumber() { return ambulanceVehicleNumber; }
    public void setAmbulanceVehicleNumber(String ambulanceVehicleNumber) { this.ambulanceVehicleNumber = ambulanceVehicleNumber; }

    public String getAmbulanceVehicleType() { return ambulanceVehicleType; }
    public void setAmbulanceVehicleType(String ambulanceVehicleType) { this.ambulanceVehicleType = ambulanceVehicleType; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getDriverPhone() { return driverPhone; }
    public void setDriverPhone(String driverPhone) { this.driverPhone = driverPhone; }

    public Double getAmbulanceLat() { return ambulanceLat; }
    public void setAmbulanceLat(Double ambulanceLat) { this.ambulanceLat = ambulanceLat; }

    public Double getAmbulanceLng() { return ambulanceLng; }
    public void setAmbulanceLng(Double ambulanceLng) { this.ambulanceLng = ambulanceLng; }

    public String getHospitalAffiliation() { return hospitalAffiliation; }
    public void setHospitalAffiliation(String hospitalAffiliation) { this.hospitalAffiliation = hospitalAffiliation; }

    public Double getHospitalLatitude() { return hospitalLatitude; }
    public void setHospitalLatitude(Double hospitalLatitude) { this.hospitalLatitude = hospitalLatitude; }

    public Double getHospitalLongitude() { return hospitalLongitude; }
    public void setHospitalLongitude(Double hospitalLongitude) { this.hospitalLongitude = hospitalLongitude; }

    private Integer destinationHospitalId;
    private String destinationHospitalName;
    private String destinationHospitalAddress;
    private String destinationHospitalPhone;
    private Double destinationLatitude;
    private Double destinationLongitude;

    public Integer getDestinationHospitalId() { return destinationHospitalId; }
    public void setDestinationHospitalId(Integer destinationHospitalId) { this.destinationHospitalId = destinationHospitalId; }

    public String getDestinationHospitalName() { return destinationHospitalName; }
    public void setDestinationHospitalName(String destinationHospitalName) { this.destinationHospitalName = destinationHospitalName; }

    public String getDestinationHospitalAddress() { return destinationHospitalAddress; }
    public void setDestinationHospitalAddress(String destinationHospitalAddress) { this.destinationHospitalAddress = destinationHospitalAddress; }

    public String getDestinationHospitalPhone() { return destinationHospitalPhone; }
    public void setDestinationHospitalPhone(String destinationHospitalPhone) { this.destinationHospitalPhone = destinationHospitalPhone; }

    public Double getDestinationLatitude() { return destinationLatitude; }
    public void setDestinationLatitude(Double destinationLatitude) { this.destinationLatitude = destinationLatitude; }

    public Double getDestinationLongitude() { return destinationLongitude; }
    public void setDestinationLongitude(Double destinationLongitude) { this.destinationLongitude = destinationLongitude; }

    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", id);
        map.put("patientId", patientId);
        map.put("emergencyType", emergencyType);
        map.put("severity", severity);
        map.put("pickupLatitude", pickupLatitude);
        map.put("pickupLongitude", pickupLongitude);
        map.put("pickupAddress", pickupAddress);
        map.put("landmark", landmark);
        map.put("contactPhone", contactPhone);
        map.put("patientNotes", patientNotes);
        map.put("assignedAmbulanceId", assignedAmbulanceId);
        map.put("driverUserId", driverUserId);
        map.put("status", status);
        map.put("estimatedArrivalMinutes", estimatedArrivalMinutes);
        map.put("cancellationReason", cancellationReason);
        map.put("requestedAt", requestedAt);
        map.put("acceptedAt", acceptedAt);
        map.put("enRouteAt", enRouteAt);
        map.put("arrivedPickupAt", arrivedPickupAt);
        map.put("pickedUpAt", pickedUpAt);
        map.put("arrivedHospitalAt", arrivedHospitalAt);
        map.put("completedAt", completedAt);
        map.put("cancelledAt", cancelledAt);
        map.put("patientName", patientName);
        map.put("ambulanceVehicleNumber", ambulanceVehicleNumber);
        map.put("ambulanceVehicleType", ambulanceVehicleType);
        map.put("driverName", driverName);
        map.put("driverPhone", driverPhone);
        map.put("ambulanceLat", ambulanceLat);
        map.put("ambulanceLng", ambulanceLng);
        map.put("hospitalAffiliation", hospitalAffiliation != null ? hospitalAffiliation : destinationHospitalName);
        map.put("hospitalLatitude", (destinationLatitude != null && destinationLatitude != 0.0) ? destinationLatitude : hospitalLatitude);
        map.put("hospitalLongitude", (destinationLongitude != null && destinationLongitude != 0.0) ? destinationLongitude : hospitalLongitude);
        map.put("destinationHospitalId", destinationHospitalId);
        map.put("destinationHospitalName", destinationHospitalName);
        map.put("destinationHospitalAddress", destinationHospitalAddress);
        map.put("destinationHospitalPhone", destinationHospitalPhone);
        map.put("destinationLatitude", destinationLatitude);
        map.put("destinationLongitude", destinationLongitude);
        return map;
    }
}
