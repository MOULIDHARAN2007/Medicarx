package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Medicine;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MedicineRepository {

    public Map<String, Integer> getAllNameToIdMap() {
        Map<String, Integer> map = new HashMap<>();
        String sql = "SELECT id, name, generic_name FROM medicines WHERE is_active = 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String genericName = rs.getString("generic_name");
                if (name != null && !name.trim().isEmpty()) {
                    String cleanName = name.trim().toLowerCase();
                    map.put(cleanName, id);
                    map.put(cleanName.replaceAll("\\s+", " "), id);
                    map.put(cleanName.replaceAll("[^a-z0-9]", ""), id);
                }
                if (genericName != null && !genericName.trim().isEmpty()) {
                    String cleanGen = genericName.trim().toLowerCase();
                    map.putIfAbsent(cleanGen, id);
                    map.putIfAbsent(cleanGen.replaceAll("\\s+", " "), id);
                    map.putIfAbsent(cleanGen.replaceAll("[^a-z0-9]", ""), id);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    public Medicine findByName(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String sql = "SELECT m.*, " +
                "COALESCE((SELECT SUM(stock_quantity) FROM inventory WHERE medicine_id = m.id), 0) as total_stock, " +
                "COALESCE((SELECT COUNT(DISTINCT pharmacy_id) FROM inventory WHERE medicine_id = m.id AND stock_quantity > 0), 0) as pharmacy_count " +
                "FROM medicines m WHERE LOWER(m.name) = LOWER(?) OR LOWER(m.generic_name) = LOWER(?) LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setString(2, name.trim());
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

    public Medicine findByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) return null;
        String sql = "SELECT m.*, " +
                "COALESCE((SELECT SUM(stock_quantity) FROM inventory WHERE medicine_id = m.id), 0) as total_stock, " +
                "COALESCE((SELECT COUNT(DISTINCT pharmacy_id) FROM inventory WHERE medicine_id = m.id AND stock_quantity > 0), 0) as pharmacy_count " +
                "FROM medicines m WHERE m.barcode = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, barcode.trim());
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

    public List<Medicine> findAll() {
        return search("", "All", "All", "name", 100);
    }

    public Medicine findById(int id) {
        String sql = "SELECT m.*, " +
                "COALESCE((SELECT SUM(stock_quantity) FROM inventory WHERE medicine_id = m.id), 0) as total_stock, " +
                "COALESCE((SELECT COUNT(DISTINCT pharmacy_id) FROM inventory WHERE medicine_id = m.id AND stock_quantity > 0), 0) as pharmacy_count " +
                "FROM medicines m WHERE m.id = ?";
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

    public List<Medicine> search(String query, String category, String availability, String sortBy, int limit) {
        List<Medicine> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT m.*, " +
                "COALESCE((SELECT SUM(stock_quantity) FROM inventory WHERE medicine_id = m.id), 0) as total_stock, " +
                "COALESCE((SELECT COUNT(DISTINCT pharmacy_id) FROM inventory WHERE medicine_id = m.id AND stock_quantity > 0), 0) as pharmacy_count " +
                "FROM medicines m WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (LOWER(m.name) LIKE ? OR LOWER(m.generic_name) LIKE ? OR LOWER(m.brand) LIKE ? OR LOWER(m.category) LIKE ?) ");
            String q = "%" + query.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }

        if (category != null && !category.equalsIgnoreCase("All") && !category.trim().isEmpty()) {
            sql.append("AND (LOWER(m.category) = LOWER(?) OR LOWER(m.category) LIKE ?) ");
            params.add(category.trim());
            params.add("%" + category.trim().toLowerCase() + "%");
        }

        if ("In Stock".equalsIgnoreCase(availability)) {
            sql.append("AND (SELECT SUM(stock_quantity) FROM inventory WHERE medicine_id = m.id) > 0 ");
        } else if ("Out of Stock".equalsIgnoreCase(availability)) {
            sql.append("AND (COALESCE((SELECT SUM(stock_quantity) FROM inventory WHERE medicine_id = m.id), 0) = 0) ");
        }

        if ("price_asc".equalsIgnoreCase(sortBy)) {
            sql.append("ORDER BY m.base_price ASC ");
        } else if ("price_desc".equalsIgnoreCase(sortBy)) {
            sql.append("ORDER BY m.base_price DESC ");
        } else if ("popular".equalsIgnoreCase(sortBy)) {
            sql.append("ORDER BY pharmacy_count DESC, m.name ASC ");
        } else {
            sql.append("ORDER BY m.id ASC ");
        }

        sql.append("LIMIT ?");
        params.add(limit > 0 ? limit : 50);

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

    public synchronized Medicine createQuick(String name, String genericName, String category, String dosage, String form, double basePrice) {
        if (name == null || name.trim().isEmpty()) return null;
        Medicine m = new Medicine();
        m.setName(name.trim());
        m.setGenericName(genericName != null && !genericName.trim().isEmpty() ? genericName.trim() : name.trim());
        m.setBrand("Generic");
        m.setCategory(category != null && !category.trim().isEmpty() ? category.trim() : "General");
        m.setDosage(dosage != null && !dosage.trim().isEmpty() ? dosage.trim() : "Standard");
        m.setForm(form != null && !form.trim().isEmpty() ? form.trim() : "Tablet");
        m.setPrescriptionRequired(false);
        m.setDescription("Imported medicine: " + name.trim());
        m.setUses(m.getCategory() + " treatment");
        m.setSideEffects("Consult physician or pharmacist if adverse effects occur.");
        m.setImportantInfo("Take as directed by a healthcare professional.");
        m.setStorageInfo("Store in a cool, dry place away from direct sunlight.");
        m.setImageUrl("https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300");
        m.setBasePrice(basePrice > 0 ? basePrice : 10.0);
        m.setActive(true);
        return create(m);
    }

    public Medicine create(Medicine m) {
        String sql = "INSERT INTO medicines (name, generic_name, brand, category, dosage, form, prescription_required, description, uses, side_effects, important_info, storage_info, image_url, base_price, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getName());
            ps.setString(2, m.getGenericName());
            ps.setString(3, m.getBrand());
            ps.setString(4, m.getCategory());
            ps.setString(5, m.getDosage());
            ps.setString(6, m.getForm());
            ps.setBoolean(7, m.isPrescriptionRequired());
            ps.setString(8, m.getDescription());
            ps.setString(9, m.getUses());
            ps.setString(10, m.getSideEffects());
            ps.setString(11, m.getImportantInfo());
            ps.setString(12, m.getStorageInfo());
            ps.setString(13, m.getImageUrl() != null && !m.getImageUrl().isEmpty() ? m.getImageUrl() : "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300");
            ps.setDouble(14, m.getBasePrice());
            ps.setBoolean(15, m.isActive());

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

    public boolean update(Medicine m) {
        String sql = "UPDATE medicines SET name=?, generic_name=?, brand=?, category=?, dosage=?, form=?, prescription_required=?, description=?, uses=?, side_effects=?, important_info=?, storage_info=?, image_url=?, base_price=?, is_active=? WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.getName());
            ps.setString(2, m.getGenericName());
            ps.setString(3, m.getBrand());
            ps.setString(4, m.getCategory());
            ps.setString(5, m.getDosage());
            ps.setString(6, m.getForm());
            ps.setBoolean(7, m.isPrescriptionRequired());
            ps.setString(8, m.getDescription());
            ps.setString(9, m.getUses());
            ps.setString(10, m.getSideEffects());
            ps.setString(11, m.getImportantInfo());
            ps.setString(12, m.getStorageInfo());
            ps.setString(13, m.getImageUrl());
            ps.setDouble(14, m.getBasePrice());
            ps.setBoolean(15, m.isActive());
            ps.setInt(16, m.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(int id) {
        String sql = "DELETE FROM medicines WHERE id = ?";
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
        String sql = "SELECT COUNT(*) FROM medicines";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public List<Medicine> getSavedMedicines(int userId) {
        List<Medicine> list = new ArrayList<>();
        String sql = "SELECT m.*, " +
                "COALESCE((SELECT SUM(stock_quantity) FROM inventory WHERE medicine_id = m.id), 0) as total_stock, " +
                "COALESCE((SELECT COUNT(DISTINCT pharmacy_id) FROM inventory WHERE medicine_id = m.id AND stock_quantity > 0), 0) as pharmacy_count " +
                "FROM medicines m INNER JOIN saved_medicines sm ON m.id = sm.medicine_id WHERE sm.user_id = ? ORDER BY sm.saved_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
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

    public boolean toggleSave(int userId, int medicineId) {
        String checkSql = "SELECT 1 FROM saved_medicines WHERE user_id = ? AND medicine_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setInt(1, userId);
            ps.setInt(2, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Already saved -> remove
                    String delSql = "DELETE FROM saved_medicines WHERE user_id = ? AND medicine_id = ?";
                    try (PreparedStatement delPs = conn.prepareStatement(delSql)) {
                        delPs.setInt(1, userId);
                        delPs.setInt(2, medicineId);
                        delPs.executeUpdate();
                        return false; // Not saved now
                    }
                } else {
                    // Save
                    String insSql = "INSERT INTO saved_medicines (user_id, medicine_id) VALUES (?, ?)";
                    try (PreparedStatement insPs = conn.prepareStatement(insSql)) {
                        insPs.setInt(1, userId);
                        insPs.setInt(2, medicineId);
                        insPs.executeUpdate();
                        return true; // Saved now
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private Medicine mapRow(ResultSet rs) throws SQLException {
        Medicine m = new Medicine();
        m.setId(rs.getInt("id"));
        try {
            m.setBarcode(rs.getString("barcode"));
        } catch (SQLException ignored) {}
        m.setName(rs.getString("name"));
        m.setGenericName(rs.getString("generic_name"));
        m.setBrand(rs.getString("brand"));
        m.setCategory(rs.getString("category"));
        m.setDosage(rs.getString("dosage"));
        m.setForm(rs.getString("form"));
        m.setPrescriptionRequired(rs.getBoolean("prescription_required"));
        m.setDescription(rs.getString("description"));
        m.setUses(rs.getString("uses"));
        m.setSideEffects(rs.getString("side_effects"));
        m.setImportantInfo(rs.getString("important_info"));
        m.setStorageInfo(rs.getString("storage_info"));
        m.setImageUrl(rs.getString("image_url"));
        m.setBasePrice(rs.getDouble("base_price"));
        m.setActive(rs.getBoolean("is_active"));
        m.setTotalStock(rs.getInt("total_stock"));
        m.setPharmacyCount(rs.getInt("pharmacy_count"));
        m.setOverallStatus(m.getTotalStock() > 20 ? "In Stock" : (m.getTotalStock() > 0 ? "Low Stock" : "Out of Stock"));
        return m;
    }
}
