package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Hospital;

import java.sql.*;
import java.util.*;

public class HospitalRepository {

    public List<Hospital> getAllHospitals(String search, String status, Boolean emergencyOnly, Boolean acceptingOnly) {
        List<Hospital> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM hospitals WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            String term = "%" + search.trim().toLowerCase() + "%";
            sql.append("AND (LOWER(name) LIKE ? OR LOWER(city) LIKE ? OR LOWER(state) LIKE ? OR LOWER(address) LIKE ? OR pincode LIKE ?) ");
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }

        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            if ("ACTIVE".equalsIgnoreCase(status)) {
                sql.append("AND is_active = 1 AND (approval_status IS NULL OR approval_status = 'ACTIVE') ");
            } else if ("INACTIVE".equalsIgnoreCase(status)) {
                sql.append("AND (is_active = 0 OR approval_status = 'INACTIVE') AND (approval_status IS NULL OR (approval_status != 'PENDING_REVIEW' AND approval_status != 'REJECTED')) ");
            } else if ("PENDING_REVIEW".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)) {
                sql.append("AND approval_status = 'PENDING_REVIEW' ");
            } else if ("REJECTED".equalsIgnoreCase(status)) {
                sql.append("AND approval_status = 'REJECTED' ");
            }
        }

        if (emergencyOnly != null && emergencyOnly) {
            sql.append("AND emergency_available = 1 ");
        }

        if (acceptingOnly != null && acceptingOnly) {
            sql.append("AND accepting_ambulances = 1 ");
        }

        sql.append("ORDER BY CASE WHEN approval_status = 'PENDING_REVIEW' THEN 0 WHEN is_active = 1 THEN 1 ELSE 2 END, name ASC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
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

    public Hospital getHospitalById(int id) {
        String sql = "SELECT * FROM hospitals WHERE id = ?";
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

    public Hospital findPotentialDuplicate(String name, String city, String address, double lat, double lng) {
        if (name == null || name.trim().isEmpty()) return null;
        String normName = name.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        String normCity = city != null ? city.trim().toLowerCase().replaceAll("[^a-z0-9]", "") : "";
        String normAddress = address != null ? address.trim().toLowerCase().replaceAll("[^a-z0-9]", "") : "";

        String sql = "SELECT * FROM hospitals WHERE approval_status != 'REJECTED'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Hospital h = mapRow(rs);
                String existingNormName = h.getName() != null ? h.getName().trim().toLowerCase().replaceAll("[^a-z0-9]", "") : "";
                String existingNormCity = h.getCity() != null ? h.getCity().trim().toLowerCase().replaceAll("[^a-z0-9]", "") : "";
                String existingNormAddress = h.getAddress() != null ? h.getAddress().trim().toLowerCase().replaceAll("[^a-z0-9]", "") : "";

                // 1. Exact or near match on normalized name in the same city
                if (!normName.isEmpty() && normName.equals(existingNormName)) {
                    if (normCity.isEmpty() || existingNormCity.isEmpty() || normCity.equals(existingNormCity)) {
                        return h;
                    }
                }

                // 2. Name + address match
                if (!normName.isEmpty() && !normAddress.isEmpty() && normName.equals(existingNormName) && normAddress.equals(existingNormAddress)) {
                    return h;
                }

                // 3. Coordinate match (< 50 meters) with matching name or address
                if (lat != 0.0 && lng != 0.0 && h.getLatitude() != 0.0 && h.getLongitude() != 0.0) {
                    double dist = calculateDistanceKm(lat, lng, h.getLatitude(), h.getLongitude());
                    if (dist <= 0.05 && (normName.equals(existingNormName) || normAddress.equals(existingNormAddress))) {
                        return h;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Hospital createHospital(Hospital h) {
        String sql = "INSERT INTO hospitals (name, registration_number, address, city, state, pincode, latitude, longitude, phone, emergency_contact, emergency_available, icu_available, available_beds, total_beds, accepting_ambulances, is_active, approval_status, created_by_user_id, created_by_role, rejection_reason) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, h.getName());
            ps.setString(2, h.getRegistrationNumber());
            ps.setString(3, h.getAddress());
            ps.setString(4, h.getCity());
            ps.setString(5, h.getState());
            ps.setString(6, h.getPincode());
            ps.setDouble(7, h.getLatitude());
            ps.setDouble(8, h.getLongitude());
            ps.setString(9, h.getPhone());
            ps.setString(10, h.getEmergencyContact());
            ps.setInt(11, h.isEmergencyAvailable() ? 1 : 0);
            ps.setInt(12, h.isIcuAvailable() ? 1 : 0);
            ps.setInt(13, h.getAvailableBeds());
            ps.setInt(14, h.getTotalBeds());
            ps.setInt(15, h.isAcceptingAmbulances() ? 1 : 0);
            ps.setInt(16, h.isActive() ? 1 : 0);
            ps.setString(17, h.getApprovalStatus() != null ? h.getApprovalStatus() : "ACTIVE");
            if (h.getCreatedByUserId() != null) ps.setInt(18, h.getCreatedByUserId());
            else ps.setNull(18, Types.INTEGER);
            ps.setString(19, h.getCreatedByRole() != null ? h.getCreatedByRole() : "ADMIN");
            ps.setString(20, h.getRejectionReason());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        h.setId(rs.getInt(1));
                        return h;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean updateHospital(Hospital h) {
        String sql = "UPDATE hospitals SET name = ?, registration_number = ?, address = ?, city = ?, state = ?, pincode = ?, latitude = ?, longitude = ?, phone = ?, emergency_contact = ?, emergency_available = ?, icu_available = ?, available_beds = ?, total_beds = ?, accepting_ambulances = ?, is_active = ?, approval_status = ?, rejection_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, h.getName());
            ps.setString(2, h.getRegistrationNumber());
            ps.setString(3, h.getAddress());
            ps.setString(4, h.getCity());
            ps.setString(5, h.getState());
            ps.setString(6, h.getPincode());
            ps.setDouble(7, h.getLatitude());
            ps.setDouble(8, h.getLongitude());
            ps.setString(9, h.getPhone());
            ps.setString(10, h.getEmergencyContact());
            ps.setInt(11, h.isEmergencyAvailable() ? 1 : 0);
            ps.setInt(12, h.isIcuAvailable() ? 1 : 0);
            ps.setInt(13, h.getAvailableBeds());
            ps.setInt(14, h.getTotalBeds());
            ps.setInt(15, h.isAcceptingAmbulances() ? 1 : 0);
            ps.setInt(16, h.isActive() ? 1 : 0);
            ps.setString(17, h.getApprovalStatus() != null ? h.getApprovalStatus() : (h.isActive() ? "ACTIVE" : "INACTIVE"));
            ps.setString(18, h.getRejectionReason());
            ps.setInt(19, h.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateHospitalStatus(int id, boolean active) {
        String sql = "UPDATE hospitals SET is_active = ?, approval_status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setString(2, active ? "ACTIVE" : "INACTIVE");
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean approveHospital(int id) {
        String sql = "UPDATE hospitals SET approval_status = 'ACTIVE', is_active = 1, rejection_reason = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean rejectHospital(int id, String reason) {
        String sql = "UPDATE hospitals SET approval_status = 'REJECTED', is_active = 0, rejection_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason != null && !reason.trim().isEmpty() ? reason.trim() : "Rejected by administrator review.");
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean hasActiveEmergencyReference(int hospitalId) {
        String sql = "SELECT COUNT(*) FROM emergency_requests WHERE destination_hospital_id = ? AND status NOT IN ('COMPLETED', 'CANCELLED', 'REJECTED')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hospitalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean hasHistoricalEmergencyReference(int hospitalId) {
        String sql = "SELECT COUNT(*) FROM emergency_requests WHERE destination_hospital_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hospitalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean softRemoveHospital(int id) {
        String sql = "UPDATE hospitals SET is_active = 0, approval_status = 'REMOVED', accepting_ambulances = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteHospital(int id) {
        String sql = "DELETE FROM hospitals WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<Map<String, Object>> getNearbyActiveHospitals(double lat, double lng, double maxDistanceKm) {
        List<Map<String, Object>> results = new ArrayList<>();
        // Only active approved hospitals accepting ambulances
        String sql = "SELECT * FROM hospitals WHERE is_active = 1 AND (approval_status IS NULL OR approval_status = 'ACTIVE') AND accepting_ambulances = 1 ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Hospital h = mapRow(rs);
                double dist = calculateDistanceKm(lat, lng, h.getLatitude(), h.getLongitude());
                if (maxDistanceKm <= 0 || dist <= maxDistanceKm) {
                    Map<String, Object> map = h.toMap();
                    map.put("distanceKm", Math.round(dist * 10.0) / 10.0);
                    map.put("distanceMeters", Math.round(dist * 1000.0));
                    results.add(map);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // If no accepting ambulance hospitals found in radius, fallback to all active approved emergency capable hospitals
        if (results.isEmpty()) {
            String fallbackSql = "SELECT * FROM hospitals WHERE is_active = 1 AND (approval_status IS NULL OR approval_status = 'ACTIVE') ORDER BY id ASC";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(fallbackSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Hospital h = mapRow(rs);
                    double dist = calculateDistanceKm(lat, lng, h.getLatitude(), h.getLongitude());
                    Map<String, Object> map = h.toMap();
                    map.put("distanceKm", Math.round(dist * 10.0) / 10.0);
                    map.put("distanceMeters", Math.round(dist * 1000.0));
                    results.add(map);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        // Sort by distance ascending
        results.sort((a, b) -> {
            Double d1 = (Double) a.get("distanceKm");
            Double d2 = (Double) b.get("distanceKm");
            if (d1 == null) d1 = 9999.0;
            if (d2 == null) d2 = 9999.0;
            return Double.compare(d1, d2);
        });

        return results;
    }

    public Map<String, Object> getHospitalStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        int total = 0;
        int active = 0;
        int inactive = 0;
        int pendingReview = 0;
        int rejected = 0;
        int emergencyReady = 0;
        int acceptingAmbulances = 0;
        int totalBeds = 0;
        int availableBeds = 0;

        String sql = "SELECT " +
                "COUNT(*) as total, " +
                "SUM(CASE WHEN is_active = 1 AND (approval_status IS NULL OR approval_status = 'ACTIVE') THEN 1 ELSE 0 END) as active_count, " +
                "SUM(CASE WHEN is_active = 0 AND (approval_status IS NULL OR (approval_status != 'PENDING_REVIEW' AND approval_status != 'REJECTED')) THEN 1 ELSE 0 END) as inactive_count, " +
                "SUM(CASE WHEN approval_status = 'PENDING_REVIEW' THEN 1 ELSE 0 END) as pending_count, " +
                "SUM(CASE WHEN approval_status = 'REJECTED' THEN 1 ELSE 0 END) as rejected_count, " +
                "SUM(CASE WHEN is_active = 1 AND (approval_status IS NULL OR approval_status = 'ACTIVE') AND emergency_available = 1 THEN 1 ELSE 0 END) as emergency_count, " +
                "SUM(CASE WHEN is_active = 1 AND (approval_status IS NULL OR approval_status = 'ACTIVE') AND accepting_ambulances = 1 THEN 1 ELSE 0 END) as accepting_count, " +
                "SUM(CASE WHEN is_active = 1 AND (approval_status IS NULL OR approval_status = 'ACTIVE') THEN available_beds ELSE 0 END) as total_avail_beds, " +
                "SUM(CASE WHEN is_active = 1 AND (approval_status IS NULL OR approval_status = 'ACTIVE') THEN total_beds ELSE 0 END) as total_cap_beds " +
                "FROM hospitals";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                total = rs.getInt("total");
                active = rs.getInt("active_count");
                inactive = rs.getInt("inactive_count");
                pendingReview = rs.getInt("pending_count");
                rejected = rs.getInt("rejected_count");
                emergencyReady = rs.getInt("emergency_count");
                acceptingAmbulances = rs.getInt("accepting_count");
                availableBeds = rs.getInt("total_avail_beds");
                totalBeds = rs.getInt("total_cap_beds");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        stats.put("total", total);
        stats.put("totalHospitals", total);
        stats.put("active", active);
        stats.put("activeHospitals", active);
        stats.put("inactive", inactive);
        stats.put("inactiveHospitals", inactive);
        stats.put("pendingReview", pendingReview);
        stats.put("pendingReviewHospitals", pendingReview);
        stats.put("rejected", rejected);
        stats.put("rejectedHospitals", rejected);
        stats.put("emergencyReady", emergencyReady);
        stats.put("emergencyReadyHospitals", emergencyReady);
        stats.put("acceptingAmbulances", acceptingAmbulances);
        stats.put("acceptingAmbulancesHospitals", acceptingAmbulances);
        stats.put("availableBeds", availableBeds);
        stats.put("totalAvailableBeds", availableBeds);
        stats.put("totalBeds", totalBeds);
        stats.put("totalBedsCapacity", totalBeds);
        return stats;
    }

    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        if (lat1 == 0.0 || lon1 == 0.0 || lat2 == 0.0 || lon2 == 0.0) return 0.0;
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private Hospital mapRow(ResultSet rs) throws SQLException {
        Hospital h = new Hospital();
        h.setId(rs.getInt("id"));
        h.setName(rs.getString("name"));
        h.setRegistrationNumber(rs.getString("registration_number"));
        h.setAddress(rs.getString("address"));
        h.setCity(rs.getString("city"));
        h.setState(rs.getString("state"));
        h.setPincode(rs.getString("pincode"));
        h.setLatitude(rs.getDouble("latitude"));
        h.setLongitude(rs.getDouble("longitude"));
        h.setPhone(rs.getString("phone"));
        h.setEmergencyContact(rs.getString("emergency_contact"));
        h.setEmergencyAvailable(rs.getInt("emergency_available") == 1);
        h.setIcuAvailable(rs.getInt("icu_available") == 1);
        h.setAvailableBeds(rs.getInt("available_beds"));
        h.setTotalBeds(rs.getInt("total_beds"));
        h.setAcceptingAmbulances(rs.getInt("accepting_ambulances") == 1);
        h.setActive(rs.getInt("is_active") == 1);
        try {
            String appStatus = rs.getString("approval_status");
            h.setApprovalStatus(appStatus != null ? appStatus : (h.isActive() ? "ACTIVE" : "INACTIVE"));
        } catch (Exception ignored) {}
        try {
            int uid = rs.getInt("created_by_user_id");
            if (!rs.wasNull()) h.setCreatedByUserId(uid);
        } catch (Exception ignored) {}
        try { h.setCreatedByRole(rs.getString("created_by_role")); } catch (Exception ignored) {}
        try { h.setRejectionReason(rs.getString("rejection_reason")); } catch (Exception ignored) {}
        try { h.setCreatedAt(rs.getString("created_at")); } catch (Exception ignored) {}
        try { h.setUpdatedAt(rs.getString("updated_at")); } catch (Exception ignored) {}
        return h;
    }
}
