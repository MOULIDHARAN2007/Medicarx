package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Ambulance;
import com.medicarex.model.EmergencyRequest;
import com.medicarex.model.Hospital;

import java.sql.*;
import java.util.*;

public class EmergencyRepository {

    public EmergencyRequest createRequest(EmergencyRequest req) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Step 1: Find nearest available active ambulance
            int matchedAmbulanceId = -1;
            Integer driverUserId = null;
            double shortestDistance = Double.MAX_VALUE;

            String findSql = "SELECT id, driver_user_id, current_latitude, current_longitude FROM ambulances WHERE is_active = 1 AND status = 'AVAILABLE'";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(findSql)) {
                while (rs.next()) {
                    int ambId = rs.getInt("id");
                    double lat = rs.getDouble("current_latitude");
                    double lng = rs.getDouble("current_longitude");
                    int dUid = rs.getInt("driver_user_id");

                    double dist = calculateDistance(req.getPickupLatitude(), req.getPickupLongitude(), lat, lng);
                    if (dist < shortestDistance) {
                        shortestDistance = dist;
                        matchedAmbulanceId = ambId;
                        driverUserId = dUid > 0 ? dUid : null;
                    }
                }
            }

            // Step 2: Attempt atomic reservation of ambulance
            boolean assigned = false;
            int etaMinutes = 8;
            if (matchedAmbulanceId > 0) {
                String lockSql = "UPDATE ambulances SET status = 'ASSIGNED', updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'AVAILABLE'";
                try (PreparedStatement psLock = conn.prepareStatement(lockSql)) {
                    psLock.setInt(1, matchedAmbulanceId);
                    int rows = psLock.executeUpdate();
                    if (rows > 0) {
                        assigned = true;
                        etaMinutes = Math.max(3, (int) Math.round(shortestDistance * 2.5) + 2);
                    }
                }
            }

            // Step 3: Insert emergency request
            String status = assigned ? "ASSIGNED" : "REQUESTED";
            String insertSql = "INSERT INTO emergency_requests (patient_id, emergency_type, severity, pickup_latitude, pickup_longitude, pickup_address, landmark, contact_phone, patient_notes, assigned_ambulance_id, driver_user_id, status, estimated_arrival_minutes, requested_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
            
            int newId = -1;
            try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, req.getPatientId());
                ps.setString(2, req.getEmergencyType() != null ? req.getEmergencyType() : "GENERAL");
                ps.setString(3, req.getSeverity() != null ? req.getSeverity() : "CRITICAL");
                ps.setDouble(4, req.getPickupLatitude());
                ps.setDouble(5, req.getPickupLongitude());
                ps.setString(6, req.getPickupAddress());
                ps.setString(7, req.getLandmark());
                ps.setString(8, req.getContactPhone());
                ps.setString(9, req.getPatientNotes());
                if (assigned) {
                    ps.setInt(10, matchedAmbulanceId);
                    if (driverUserId != null) ps.setInt(11, driverUserId);
                    else ps.setNull(11, Types.INTEGER);
                } else {
                    ps.setNull(10, Types.INTEGER);
                    ps.setNull(11, Types.INTEGER);
                }
                ps.setString(12, status);
                ps.setInt(13, etaMinutes);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        newId = keys.getInt(1);
                    }
                }
            }

            conn.commit();
            if (newId > 0) {
                return getRequestById(newId);
            }
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
        return null;
    }

    public EmergencyRequest getRequestById(int id) {
        String sql = "SELECT er.*, u.full_name as patient_name, a.vehicle_number, a.vehicle_type, a.driver_name, a.driver_phone, a.current_latitude as amb_lat, a.current_longitude as amb_lng, a.hospital_affiliation " +
                "FROM emergency_requests er " +
                "LEFT JOIN users u ON er.patient_id = u.id " +
                "LEFT JOIN ambulances a ON er.assigned_ambulance_id = a.id " +
                "WHERE er.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public EmergencyRequest getActiveRequestForPatient(int patientId) {
        String sql = "SELECT er.*, u.full_name as patient_name, a.vehicle_number, a.vehicle_type, a.driver_name, a.driver_phone, a.current_latitude as amb_lat, a.current_longitude as amb_lng, a.hospital_affiliation " +
                "FROM emergency_requests er " +
                "LEFT JOIN users u ON er.patient_id = u.id " +
                "LEFT JOIN ambulances a ON er.assigned_ambulance_id = a.id " +
                "WHERE er.patient_id = ? AND er.status NOT IN ('COMPLETED', 'CANCELLED') " +
                "ORDER BY er.id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public EmergencyRequest getActiveRequestForDriver(int driverUserId) {
        String sql = "SELECT er.*, u.full_name as patient_name, a.vehicle_number, a.vehicle_type, a.driver_name, a.driver_phone, a.current_latitude as amb_lat, a.current_longitude as amb_lng, a.hospital_affiliation " +
                "FROM emergency_requests er " +
                "LEFT JOIN users u ON er.patient_id = u.id " +
                "LEFT JOIN ambulances a ON er.assigned_ambulance_id = a.id " +
                "WHERE (er.driver_user_id = ? OR a.driver_user_id = ?) AND er.status NOT IN ('COMPLETED', 'CANCELLED', 'REJECTED') " +
                "ORDER BY er.id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverUserId);
            ps.setInt(2, driverUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean verifyEmergencyDriverAccess(int emergencyId, int driverUserId) {
        String sql = "SELECT er.id FROM emergency_requests er " +
                "LEFT JOIN ambulances a ON er.assigned_ambulance_id = a.id " +
                "WHERE er.id = ? AND (er.driver_user_id = ? OR a.driver_user_id = ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, emergencyId);
            ps.setInt(2, driverUserId);
            ps.setInt(3, driverUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<EmergencyRequest> getPatientRequests(int patientId) {
        List<EmergencyRequest> list = new ArrayList<>();
        String sql = "SELECT er.*, u.full_name as patient_name, a.vehicle_number, a.vehicle_type, a.driver_name, a.driver_phone, a.current_latitude as amb_lat, a.current_longitude as amb_lng, a.hospital_affiliation " +
                "FROM emergency_requests er " +
                "LEFT JOIN users u ON er.patient_id = u.id " +
                "LEFT JOIN ambulances a ON er.assigned_ambulance_id = a.id " +
                "WHERE er.patient_id = ? ORDER BY er.id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<EmergencyRequest> getRequestsForAmbulanceDriver(int driverUserId) {
        List<EmergencyRequest> list = new ArrayList<>();
        String sql = "SELECT er.*, u.full_name as patient_name, a.vehicle_number, a.vehicle_type, a.driver_name, a.driver_phone, a.current_latitude as amb_lat, a.current_longitude as amb_lng, a.hospital_affiliation " +
                "FROM emergency_requests er " +
                "LEFT JOIN users u ON er.patient_id = u.id " +
                "LEFT JOIN ambulances a ON er.assigned_ambulance_id = a.id " +
                "WHERE (er.driver_user_id = ? OR a.driver_user_id = ?) " +
                "ORDER BY er.id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverUserId);
            ps.setInt(2, driverUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<EmergencyRequest> getPendingOrAssignedRequests() {
        List<EmergencyRequest> list = new ArrayList<>();
        String sql = "SELECT er.*, u.full_name as patient_name, a.vehicle_number, a.vehicle_type, a.driver_name, a.driver_phone, a.current_latitude as amb_lat, a.current_longitude as amb_lng, a.hospital_affiliation " +
                "FROM emergency_requests er " +
                "LEFT JOIN users u ON er.patient_id = u.id " +
                "LEFT JOIN ambulances a ON er.assigned_ambulance_id = a.id " +
                "WHERE er.status IN ('REQUESTED', 'ASSIGNED') ORDER BY er.id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean acceptRequest(int requestId, int driverUserId) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Fetch ambulance ID for this driver
            int ambId = -1;
            String ambSql = "SELECT id FROM ambulances WHERE driver_user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(ambSql)) {
                ps.setInt(1, driverUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) ambId = rs.getInt("id");
                }
            }

            // Update request
            String updateSql = "UPDATE emergency_requests SET status = 'ACCEPTED', driver_user_id = ?, assigned_ambulance_id = COALESCE(assigned_ambulance_id, ?), accepted_at = CURRENT_TIMESTAMP WHERE id = ? AND status IN ('REQUESTED', 'ASSIGNED')";
            int rows;
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, driverUserId);
                if (ambId > 0) ps.setInt(2, ambId);
                else ps.setNull(2, Types.INTEGER);
                ps.setInt(3, requestId);
                rows = ps.executeUpdate();
            }

            if (rows > 0) {
                if (ambId > 0) {
                    String updateAmb = "UPDATE ambulances SET status = 'BUSY', updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateAmb)) {
                        ps.setInt(1, ambId);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
                return true;
            }
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
        return false;
    }

    public boolean updateRequestStatus(int requestId, String newStatus, String notes) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            StringBuilder sb = new StringBuilder("UPDATE emergency_requests SET status = ?");
            if ("EN_ROUTE".equalsIgnoreCase(newStatus)) {
                sb.append(", en_route_at = CURRENT_TIMESTAMP");
            } else if ("ARRIVED".equalsIgnoreCase(newStatus)) {
                sb.append(", arrived_pickup_at = CURRENT_TIMESTAMP");
            } else if ("PATIENT_PICKED_UP".equalsIgnoreCase(newStatus)) {
                sb.append(", picked_up_at = CURRENT_TIMESTAMP");
            } else if ("ARRIVED_AT_HOSPITAL".equalsIgnoreCase(newStatus)) {
                sb.append(", arrived_hospital_at = CURRENT_TIMESTAMP");
            } else if ("COMPLETED".equalsIgnoreCase(newStatus)) {
                sb.append(", completed_at = CURRENT_TIMESTAMP");
            } else if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                sb.append(", cancelled_at = CURRENT_TIMESTAMP");
                if (notes != null && !notes.isEmpty()) {
                    sb.append(", cancellation_reason = ?");
                }
            }
            sb.append(" WHERE id = ?");

            int rows;
            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                ps.setString(1, newStatus);
                int idx = 2;
                if ("CANCELLED".equalsIgnoreCase(newStatus) && notes != null && !notes.isEmpty()) {
                    ps.setString(idx++, notes);
                }
                ps.setInt(idx, requestId);
                rows = ps.executeUpdate();
            }

            if (rows > 0) {
                // If COMPLETED or CANCELLED, free the ambulance
                if ("COMPLETED".equalsIgnoreCase(newStatus) || "CANCELLED".equalsIgnoreCase(newStatus)) {
                    String freeAmb = "UPDATE ambulances SET status = 'AVAILABLE', updated_at = CURRENT_TIMESTAMP " +
                            "WHERE id = (SELECT assigned_ambulance_id FROM emergency_requests WHERE id = ?)";
                    try (PreparedStatement ps = conn.prepareStatement(freeAmb)) {
                        ps.setInt(1, requestId);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
                return true;
            }
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
        return false;
    }

    public boolean cancelRequest(int requestId, String reason) {
        return updateRequestStatus(requestId, "CANCELLED", reason);
    }

    public Ambulance getAmbulanceByDriverUserId(int driverUserId) {
        String sql = "SELECT * FROM ambulances WHERE driver_user_id = ? AND is_active = 1 LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAmbulance(rs);
                }
            }

            // Fallback: If no ambulance explicitly assigned to this driver user ID yet, find first active ambulance with null or matching driver_user_id and associate it
            String fallbackSql = "SELECT * FROM ambulances WHERE is_active = 1 AND (driver_user_id IS NULL OR driver_user_id = ?) ORDER BY id ASC LIMIT 1";
            try (PreparedStatement ps2 = conn.prepareStatement(fallbackSql)) {
                ps2.setInt(1, driverUserId);
                try (ResultSet rs2 = ps2.executeQuery()) {
                    if (rs2.next()) {
                        int ambId = rs2.getInt("id");
                        String updateSql = "UPDATE ambulances SET driver_user_id = ? WHERE id = ?";
                        try (PreparedStatement psUp = conn.prepareStatement(updateSql)) {
                            psUp.setInt(1, driverUserId);
                            psUp.setInt(2, ambId);
                            psUp.executeUpdate();
                        }
                        return getAmbulanceById(ambId);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Ambulance getAmbulanceById(int id) {
        String sql = "SELECT * FROM ambulances WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAmbulance(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean updateAmbulanceLocation(int ambulanceId, double lat, double lng) {
        String sql = "UPDATE ambulances SET current_latitude = ?, current_longitude = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, lat);
            ps.setDouble(2, lng);
            ps.setInt(3, ambulanceId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateAmbulanceStatus(int ambulanceId, String status) {
        String sql = "UPDATE ambulances SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, ambulanceId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<Ambulance> getAllAmbulances() {
        List<Ambulance> list = new ArrayList<>();
        String sql = "SELECT * FROM ambulances ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapAmbulance(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean createAmbulance(Ambulance a) {
        String sql = "INSERT INTO ambulances (vehicle_number, vehicle_type, driver_name, driver_phone, driver_user_id, status, current_latitude, current_longitude, hospital_affiliation, equipment_summary, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.getVehicleNumber());
            ps.setString(2, a.getVehicleType() != null ? a.getVehicleType() : "BASIC");
            ps.setString(3, a.getDriverName());
            ps.setString(4, a.getDriverPhone());
            if (a.getDriverUserId() != null && a.getDriverUserId() > 0) ps.setInt(5, a.getDriverUserId());
            else ps.setNull(5, Types.INTEGER);
            ps.setString(6, a.getStatus() != null ? a.getStatus() : "AVAILABLE");
            ps.setDouble(7, a.getCurrentLatitude());
            ps.setDouble(8, a.getCurrentLongitude());
            ps.setString(9, a.getHospitalAffiliation());
            ps.setString(10, a.getEquipmentSummary());
            ps.setBoolean(11, true);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateAmbulance(Ambulance a) {
        String sql = "UPDATE ambulances SET vehicle_number = ?, vehicle_type = ?, driver_name = ?, driver_phone = ?, driver_user_id = ?, status = ?, current_latitude = ?, current_longitude = ?, hospital_affiliation = ?, equipment_summary = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.getVehicleNumber());
            ps.setString(2, a.getVehicleType());
            ps.setString(3, a.getDriverName());
            ps.setString(4, a.getDriverPhone());
            if (a.getDriverUserId() != null && a.getDriverUserId() > 0) ps.setInt(5, a.getDriverUserId());
            else ps.setNull(5, Types.INTEGER);
            ps.setString(6, a.getStatus());
            ps.setDouble(7, a.getCurrentLatitude());
            ps.setDouble(8, a.getCurrentLongitude());
            ps.setString(9, a.getHospitalAffiliation());
            ps.setString(10, a.getEquipmentSummary());
            ps.setBoolean(11, a.isActive());
            ps.setInt(12, a.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteAmbulance(int id) {
        String sql = "DELETE FROM ambulances WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<EmergencyRequest> getAllEmergencyRequests(String statusFilter) {
        List<EmergencyRequest> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder("SELECT er.*, u.full_name as patient_name, a.vehicle_number, a.vehicle_type, a.driver_name, a.driver_phone, a.current_latitude as amb_lat, a.current_longitude as amb_lng, a.hospital_affiliation " +
                "FROM emergency_requests er " +
                "LEFT JOIN users u ON er.patient_id = u.id " +
                "LEFT JOIN ambulances a ON er.assigned_ambulance_id = a.id ");
        if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            sb.append("WHERE er.status = ? ");
        }
        sb.append("ORDER BY er.id DESC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                ps.setString(1, statusFilter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Map<String, Object> getEmergencyStats() {
        Map<String, Object> stats = new HashMap<>();
        String sql = "SELECT " +
                "COUNT(*) as total_requests, " +
                "SUM(CASE WHEN status IN ('REQUESTED', 'ASSIGNED', 'ACCEPTED', 'EN_ROUTE', 'ARRIVED', 'PATIENT_PICKED_UP', 'TRANSPORTING', 'ARRIVED_AT_HOSPITAL') THEN 1 ELSE 0 END) as active_emergencies, " +
                "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_trips, " +
                "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled_requests " +
                "FROM emergency_requests";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                stats.put("totalRequests", rs.getInt("total_requests"));
                stats.put("activeEmergencies", rs.getInt("active_emergencies"));
                stats.put("completedTrips", rs.getInt("completed_trips"));
                stats.put("cancelledRequests", rs.getInt("cancelled_requests"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String ambStatsSql = "SELECT " +
                "COUNT(*) as total_fleet, " +
                "SUM(CASE WHEN status = 'AVAILABLE' THEN 1 ELSE 0 END) as available_units, " +
                "SUM(CASE WHEN status IN ('BUSY', 'ASSIGNED', 'EN_ROUTE') THEN 1 ELSE 0 END) as busy_units, " +
                "SUM(CASE WHEN status = 'OFFLINE' OR is_active = 0 THEN 1 ELSE 0 END) as offline_units " +
                "FROM ambulances";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(ambStatsSql)) {
            if (rs.next()) {
                stats.put("totalFleet", rs.getInt("total_fleet"));
                stats.put("availableUnits", rs.getInt("available_units"));
                stats.put("busyUnits", rs.getInt("busy_units"));
                stats.put("offlineUnits", rs.getInt("offline_units"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return stats;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        if (lat1 == 0 || lon1 == 0 || lat2 == 0 || lon2 == 0) return 10.0;
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public static double[] getHospitalCoordinates(String hospitalName) {
        if (hospitalName == null || hospitalName.trim().isEmpty()) {
            return new double[]{12.9716, 77.5946};
        }
        String h = hospitalName.toLowerCase();
        if (h.contains("manipal") || h.contains("indiranagar")) {
            return new double[]{12.9587, 77.6486};
        } else if (h.contains("koramangala") || h.contains("apollo")) {
            return new double[]{12.9345, 77.6245};
        } else if (h.contains("whitefield") || h.contains("cloudnine")) {
            return new double[]{12.9864, 77.7345};
        } else if (h.contains("fortis")) {
            return new double[]{12.8945, 77.5985};
        } else if (h.contains("chennai") || h.contains("t nagar")) {
            return new double[]{13.0418, 80.2341};
        } else if (h.contains("anna nagar")) {
            return new double[]{13.0850, 80.2100};
        }
        return new double[]{12.9716, 77.5946};
    }

    private EmergencyRequest mapRow(ResultSet rs) throws SQLException {
        EmergencyRequest req = new EmergencyRequest();
        req.setId(rs.getInt("id"));
        req.setPatientId(rs.getInt("patient_id"));
        req.setEmergencyType(rs.getString("emergency_type"));
        req.setSeverity(rs.getString("severity"));
        req.setPickupLatitude(rs.getDouble("pickup_latitude"));
        req.setPickupLongitude(rs.getDouble("pickup_longitude"));
        req.setPickupAddress(rs.getString("pickup_address"));
        req.setLandmark(rs.getString("landmark"));
        req.setContactPhone(rs.getString("contact_phone"));
        req.setPatientNotes(rs.getString("patient_notes"));
        int ambId = rs.getInt("assigned_ambulance_id");
        if (!rs.wasNull()) req.setAssignedAmbulanceId(ambId);
        int dId = rs.getInt("driver_user_id");
        if (!rs.wasNull()) req.setDriverUserId(dId);
        req.setStatus(rs.getString("status"));
        req.setEstimatedArrivalMinutes(rs.getInt("estimated_arrival_minutes"));
        req.setCancellationReason(rs.getString("cancellation_reason"));
        req.setRequestedAt(rs.getString("requested_at"));
        req.setAcceptedAt(rs.getString("accepted_at"));
        req.setEnRouteAt(rs.getString("en_route_at"));
        req.setArrivedPickupAt(rs.getString("arrived_pickup_at"));
        req.setPickedUpAt(rs.getString("picked_up_at"));
        req.setArrivedHospitalAt(rs.getString("arrived_hospital_at"));
        req.setCompletedAt(rs.getString("completed_at"));
        req.setCancelledAt(rs.getString("cancelled_at"));

        try { req.setPatientName(rs.getString("patient_name")); } catch (Exception ignored) {}
        try { req.setAmbulanceVehicleNumber(rs.getString("vehicle_number")); } catch (Exception ignored) {}
        try { req.setAmbulanceVehicleType(rs.getString("vehicle_type")); } catch (Exception ignored) {}
        try { req.setDriverName(rs.getString("driver_name")); } catch (Exception ignored) {}
        try { req.setDriverPhone(rs.getString("driver_phone")); } catch (Exception ignored) {}
        try {
            double lat = rs.getDouble("amb_lat");
            if (!rs.wasNull()) req.setAmbulanceLat(lat);
            double lng = rs.getDouble("amb_lng");
            if (!rs.wasNull()) req.setAmbulanceLng(lng);
        } catch (Exception ignored) {}

        try {
            int destHospId = rs.getInt("destination_hospital_id");
            if (!rs.wasNull()) req.setDestinationHospitalId(destHospId);
        } catch (Exception ignored) {}
        try { req.setDestinationHospitalName(rs.getString("destination_hospital_name")); } catch (Exception ignored) {}
        try { req.setDestinationHospitalAddress(rs.getString("destination_hospital_address")); } catch (Exception ignored) {}
        try { req.setDestinationHospitalPhone(rs.getString("destination_hospital_phone")); } catch (Exception ignored) {}
        try {
            double destLat = rs.getDouble("destination_latitude");
            if (!rs.wasNull() && destLat != 0.0) req.setDestinationLatitude(destLat);
            double destLng = rs.getDouble("destination_longitude");
            if (!rs.wasNull() && destLng != 0.0) req.setDestinationLongitude(destLng);
        } catch (Exception ignored) {}

        try {
            if (req.getDestinationHospitalName() != null && !req.getDestinationHospitalName().trim().isEmpty()) {
                req.setHospitalAffiliation(req.getDestinationHospitalName());
                if (req.getDestinationLatitude() != null && req.getDestinationLongitude() != null) {
                    req.setHospitalLatitude(req.getDestinationLatitude());
                    req.setHospitalLongitude(req.getDestinationLongitude());
                }
            } else {
                String hospAff = rs.getString("hospital_affiliation");
                if (hospAff != null && !hospAff.trim().isEmpty()) {
                    req.setHospitalAffiliation(hospAff);
                    double[] hospCoords = getHospitalCoordinates(hospAff);
                    req.setHospitalLatitude(hospCoords[0]);
                    req.setHospitalLongitude(hospCoords[1]);
                }
            }
        } catch (Exception ignored) {}

        return req;
    }

    public boolean setEmergencyDestinationHospital(int emergencyId, int hospitalId, int driverUserId) {
        HospitalRepository hospRepo = new HospitalRepository();
        Hospital hosp = hospRepo.getHospitalById(hospitalId);
        if (hosp == null || !hosp.isActive()) {
            return false;
        }

        String sql = "UPDATE emergency_requests SET destination_hospital_id = ?, destination_hospital_name = ?, " +
                "destination_hospital_address = ?, destination_hospital_phone = ?, destination_latitude = ?, destination_longitude = ? " +
                "WHERE id = ? AND (driver_user_id = ? OR assigned_ambulance_id IN (SELECT id FROM ambulances WHERE driver_user_id = ?))";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hosp.getId());
            ps.setString(2, hosp.getName());
            ps.setString(3, hosp.getAddress() + (hosp.getCity() != null ? ", " + hosp.getCity() : ""));
            ps.setString(4, hosp.getEmergencyContact() != null ? hosp.getEmergencyContact() : hosp.getPhone());
            ps.setDouble(5, hosp.getLatitude());
            ps.setDouble(6, hosp.getLongitude());
            ps.setInt(7, emergencyId);
            ps.setInt(8, driverUserId);
            ps.setInt(9, driverUserId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private Ambulance mapAmbulance(ResultSet rs) throws SQLException {
        Ambulance a = new Ambulance();
        a.setId(rs.getInt("id"));
        a.setVehicleNumber(rs.getString("vehicle_number"));
        a.setVehicleType(rs.getString("vehicle_type"));
        a.setDriverName(rs.getString("driver_name"));
        a.setDriverPhone(rs.getString("driver_phone"));
        int dUid = rs.getInt("driver_user_id");
        if (!rs.wasNull()) a.setDriverUserId(dUid);
        a.setStatus(rs.getString("status"));
        a.setCurrentLatitude(rs.getDouble("current_latitude"));
        a.setCurrentLongitude(rs.getDouble("current_longitude"));
        a.setHospitalAffiliation(rs.getString("hospital_affiliation"));
        a.setEquipmentSummary(rs.getString("equipment_summary"));
        a.setActive(rs.getBoolean("is_active"));
        try { a.setCreatedAt(rs.getString("created_at")); } catch (Exception ignored) {}
        try { a.setUpdatedAt(rs.getString("updated_at")); } catch (Exception ignored) {}
        return a;
    }
}
