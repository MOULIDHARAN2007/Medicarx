package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Pharmacy;

import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PharmacyRepository {

    // Default reference coordinates: Bangalore City Center (12.9716, 77.5946)
    private static final double DEFAULT_LAT = 12.9716;
    private static final double DEFAULT_LNG = 77.5946;

    public List<Pharmacy> findAll() {
        return search(null, null, DEFAULT_LAT, DEFAULT_LNG);
    }

    public Pharmacy findById(int id) {
        String sql = "SELECT * FROM pharmacies WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Pharmacy p = mapRow(rs);
                    p.setDistanceKm(calculateDistance(DEFAULT_LAT, DEFAULT_LNG, p.getLatitude(), p.getLongitude()));
                    return p;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Pharmacy findByUserId(int userId) {
        String sql = "SELECT * FROM pharmacies WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Pharmacy p = mapRow(rs);
                    p.setDistanceKm(calculateDistance(DEFAULT_LAT, DEFAULT_LNG, p.getLatitude(), p.getLongitude()));
                    return p;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Pharmacy> search(String query, String status, double userLat, double userLng) {
        return search(query, status, userLat, userLng, 0.0);
    }

    public List<Pharmacy> search(String query, String status, double userLat, double userLng, double radiusKm) {
        return findNearby(userLat, userLng, radiusKm, null, query, status);
    }

    public List<Pharmacy> findPharmaciesByMedicine(int medicineId, double userLat, double userLng) {
        return findPharmaciesByMedicine(medicineId, userLat, userLng, 0.0);
    }

    public List<Pharmacy> findPharmaciesByMedicine(int medicineId, double userLat, double userLng, double radiusKm) {
        return findNearby(userLat, userLng, radiusKm, medicineId, null, "Active");
    }

    public List<Pharmacy> findNearby(double userLat, double userLng, double radiusKm, Integer medicineId, String query, String status) {
        List<Pharmacy> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (medicineId != null && medicineId > 0) {
            sql.append("SELECT p.*, i.unit_price, i.stock_quantity, i.status as stock_status ")
               .append("FROM pharmacies p ")
               .append("LEFT JOIN inventory i ON p.id = i.pharmacy_id AND i.medicine_id = ? ")
               .append("WHERE 1=1 ");
            params.add(medicineId);
        } else {
            sql.append("SELECT p.*, NULL as unit_price, 0 as stock_quantity, NULL as stock_status ")
               .append("FROM pharmacies p ")
               .append("WHERE 1=1 ");
        }

        if (status != null && !status.equalsIgnoreCase("All") && !status.trim().isEmpty()) {
            sql.append("AND LOWER(p.status) = LOWER(?) ");
            params.add(status.trim());
        }

        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (LOWER(p.pharmacy_name) LIKE ? OR LOWER(p.city) LIKE ? OR LOWER(p.address) LIKE ? OR LOWER(p.postal_code) LIKE ?) ");
            String q = "%" + query.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }

        sql.append("ORDER BY p.id ASC");

        double targetLat = userLat != 0.0 ? userLat : DEFAULT_LAT;
        double targetLng = userLng != 0.0 ? userLng : DEFAULT_LNG;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Pharmacy p = mapRow(rs);
                    
                    if (medicineId != null && medicineId > 0) {
                        double price = rs.getDouble("unit_price");
                        int qty = rs.getInt("stock_quantity");
                        String stockStatus = rs.getString("stock_status");

                        if (price > 0) {
                            p.setMedicinePrice(price);
                        }
                        p.setMedicineStockQty(qty);
                        p.setMedicineStockStatus(stockStatus != null ? stockStatus : (qty > 0 ? "In Stock" : "Out of Stock"));
                    }

                    double dist = calculateDistance(targetLat, targetLng, p.getLatitude(), p.getLongitude());
                    p.setDistanceKm(dist);

                    // Radius filtering if radius specified (> 0)
                    if (radiusKm <= 0.0 || dist <= radiusKm) {
                        list.add(p);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[PharmacyRepository] findNearby error: " + e.getMessage());
            e.printStackTrace();
        }

        // Sorting
        if (medicineId != null && medicineId > 0) {
            list.sort((a, b) -> {
                boolean aStock = "In Stock".equalsIgnoreCase(a.getMedicineStockStatus()) || "Low Stock".equalsIgnoreCase(a.getMedicineStockStatus());
                boolean bStock = "In Stock".equalsIgnoreCase(b.getMedicineStockStatus()) || "Low Stock".equalsIgnoreCase(b.getMedicineStockStatus());
                if (aStock != bStock) {
                    return aStock ? -1 : 1;
                }
                return Double.compare(a.getDistanceKm(), b.getDistanceKm());
            });
        } else {
            list.sort(Comparator.comparingDouble(Pharmacy::getDistanceKm));
        }

        return list;
    }

    public Pharmacy create(Pharmacy p) {
        String sql = "INSERT INTO pharmacies (user_id, pharmacy_name, license_number, owner_name, address, city, state, postal_code, latitude, longitude, phone, email, opening_hours, is_24_hours, is_verified, rating, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (p.getUserId() != null) ps.setInt(1, p.getUserId()); else ps.setNull(1, Types.INTEGER);
            ps.setString(2, p.getPharmacyName());
            ps.setString(3, p.getLicenseNumber());
            ps.setString(4, p.getOwnerName());
            ps.setString(5, p.getAddress());
            ps.setString(6, p.getCity());
            ps.setString(7, p.getState());
            ps.setString(8, p.getPostalCode());
            ps.setDouble(9, p.getLatitude());
            ps.setDouble(10, p.getLongitude());
            ps.setString(11, p.getPhone());
            ps.setString(12, p.getEmail());
            ps.setString(13, p.getOpeningHours() != null ? p.getOpeningHours() : "8:00 AM - 10:00 PM");
            ps.setBoolean(14, p.is24Hours());
            ps.setBoolean(15, p.isVerified());
            ps.setDouble(16, p.getRating() > 0 ? p.getRating() : 4.8);
            ps.setString(17, p.getStatus() != null ? p.getStatus() : "Active");

            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return findById(rs.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean update(Pharmacy p) {
        String sql = "UPDATE pharmacies SET pharmacy_name=?, license_number=?, owner_name=?, address=?, city=?, state=?, postal_code=?, latitude=?, longitude=?, phone=?, email=?, opening_hours=?, is_24_hours=?, is_verified=?, rating=?, status=? WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getPharmacyName());
            ps.setString(2, p.getLicenseNumber());
            ps.setString(3, p.getOwnerName());
            ps.setString(4, p.getAddress());
            ps.setString(5, p.getCity());
            ps.setString(6, p.getState());
            ps.setString(7, p.getPostalCode());
            ps.setDouble(8, p.getLatitude());
            ps.setDouble(9, p.getLongitude());
            ps.setString(10, p.getPhone());
            ps.setString(11, p.getEmail());
            ps.setString(12, p.getOpeningHours());
            ps.setBoolean(13, p.is24Hours());
            ps.setBoolean(14, p.isVerified());
            ps.setDouble(15, p.getRating());
            ps.setString(16, p.getStatus());
            ps.setInt(17, p.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(int id) {
        String sql = "DELETE FROM pharmacies WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int count() {
        String sql = "SELECT COUNT(*) FROM pharmacies";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public java.util.Map<String, Object> getMedicineStockInPharmacy(int pharmacyId, int medicineId) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("medicineId", medicineId);
        map.put("pharmacyId", pharmacyId);
        map.put("inStock", false);
        map.put("stockQuantity", 0);
        map.put("unitPrice", 0.0);
        map.put("status", "Out of Stock");

        String sql = "SELECT i.stock_quantity, i.unit_price, i.status, m.name, m.generic_name " +
                     "FROM inventory i " +
                     "JOIN medicines m ON m.id = i.medicine_id " +
                     "WHERE i.pharmacy_id = ? AND i.medicine_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pharmacyId);
            ps.setInt(2, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int qty = rs.getInt("stock_quantity");
                    double price = rs.getDouble("unit_price");
                    String status = rs.getString("status");
                    map.put("medicineName", rs.getString("name"));
                    map.put("genericName", rs.getString("generic_name"));
                    map.put("stockQuantity", qty);
                    map.put("unitPrice", price);
                    map.put("status", status != null ? status : (qty > 0 ? "In Stock" : "Out of Stock"));
                    map.put("inStock", qty > 0);
                } else {
                    // Fetch medicine name if not in inventory
                    String medSql = "SELECT name, generic_name, base_price FROM medicines WHERE id = ?";
                    try (PreparedStatement medPs = conn.prepareStatement(medSql)) {
                        medPs.setInt(1, medicineId);
                        try (ResultSet medRs = medPs.executeQuery()) {
                            if (medRs.next()) {
                                map.put("medicineName", medRs.getString("name"));
                                map.put("genericName", medRs.getString("generic_name"));
                                map.put("unitPrice", medRs.getDouble("base_price"));
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private Pharmacy mapRow(ResultSet rs) throws SQLException {
        Pharmacy p = new Pharmacy();
        p.setId(rs.getInt("id"));
        int userId = rs.getInt("user_id");
        if (!rs.wasNull()) p.setUserId(userId);
        p.setPharmacyName(rs.getString("pharmacy_name"));
        p.setLicenseNumber(rs.getString("license_number"));
        p.setOwnerName(rs.getString("owner_name"));
        p.setAddress(rs.getString("address"));
        p.setCity(rs.getString("city"));
        p.setState(rs.getString("state"));
        p.setPostalCode(rs.getString("postal_code"));
        p.setLatitude(rs.getDouble("latitude"));
        p.setLongitude(rs.getDouble("longitude"));
        p.setPhone(rs.getString("phone"));
        p.setEmail(rs.getString("email"));
        p.setOpeningHours(rs.getString("opening_hours"));
        p.set24Hours(rs.getBoolean("is_24_hours"));
        p.setVerified(rs.getBoolean("is_verified"));
        p.setRating(rs.getDouble("rating"));
        p.setStatus(rs.getString("status"));
        return p;
    }
}
