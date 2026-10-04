package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.InventoryItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryRepository {

    public List<InventoryItem> findByPharmacyId(int pharmacyId, String category, String status, String query) {
        List<InventoryItem> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT i.*, m.name as medicine_name, m.generic_name, m.category, m.form, p.pharmacy_name " +
                "FROM inventory i " +
                "JOIN medicines m ON i.medicine_id = m.id " +
                "JOIN pharmacies p ON i.pharmacy_id = p.id " +
                "WHERE i.pharmacy_id = ? ");

        List<Object> params = new ArrayList<>();
        params.add(pharmacyId);

        if (category != null && !category.equalsIgnoreCase("All") && !category.trim().isEmpty()) {
            sql.append("AND LOWER(m.category) = LOWER(?) ");
            params.add(category.trim());
        }

        if (status != null && !status.equalsIgnoreCase("All") && !status.trim().isEmpty()) {
            sql.append("AND LOWER(i.status) = LOWER(?) ");
            params.add(status.trim());
        }

        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (LOWER(m.name) LIKE ? OR LOWER(m.generic_name) LIKE ? OR LOWER(i.batch_number) LIKE ?) ");
            String q = "%" + query.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }

        sql.append("ORDER BY i.id ASC");

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

    public List<InventoryItem> findAll(Integer pharmacyId, String status, String query) {
        List<InventoryItem> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT i.*, m.name as medicine_name, m.generic_name, m.category, m.form, p.pharmacy_name " +
                "FROM inventory i " +
                "JOIN medicines m ON i.medicine_id = m.id " +
                "JOIN pharmacies p ON i.pharmacy_id = p.id " +
                "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (pharmacyId != null && pharmacyId > 0) {
            sql.append("AND i.pharmacy_id = ? ");
            params.add(pharmacyId);
        }

        if (status != null && !status.equalsIgnoreCase("All") && !status.trim().isEmpty()) {
            sql.append("AND LOWER(i.status) = LOWER(?) ");
            params.add(status.trim());
        }

        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (LOWER(m.name) LIKE ? OR LOWER(p.pharmacy_name) LIKE ? OR LOWER(i.batch_number) LIKE ?) ");
            String q = "%" + query.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }

        sql.append("ORDER BY i.id ASC");

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

    public InventoryItem findById(int id) {
        String sql = "SELECT i.*, m.name as medicine_name, m.generic_name, m.category, m.form, p.pharmacy_name " +
                "FROM inventory i " +
                "JOIN medicines m ON i.medicine_id = m.id " +
                "JOIN pharmacies p ON i.pharmacy_id = p.id " +
                "WHERE i.id = ?";
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

    public boolean updateStock(int inventoryId, int newQuantity, double newPrice, String newStatus, String expiryDate) {
        String status = newStatus;
        if (status == null || status.isEmpty()) {
            status = newQuantity > 20 ? "In Stock" : (newQuantity > 0 ? "Low Stock" : "Out of Stock");
        }
        String sql = "UPDATE inventory SET stock_quantity = ?, unit_price = ?, status = ?, expiry_date = ?, last_updated = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newQuantity);
            ps.setDouble(2, newPrice);
            ps.setString(3, status);
            ps.setString(4, expiryDate != null ? expiryDate : "2028-12-31");
            ps.setInt(5, inventoryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public Map<String, Object> importCsvBatch(int pharmacyId, List<Map<String, Object>> validRows) {
        Map<String, Object> result = new HashMap<>();
        int updatedCount = 0;
        int addedCount = 0;
        int importedCount = 0;

        if (validRows == null || validRows.isEmpty()) {
            result.put("imported", 0);
            result.put("updated", 0);
            result.put("added", 0);
            result.put("failed", 0);
            return result;
        }

        // Deduplicate within the batch: last row for same medicine_id wins snapshot
        Map<Integer, Map<String, Object>> deduplicatedMap = new HashMap<>();
        for (Map<String, Object> row : validRows) {
            int medId = ((Number) row.get("medicineId")).intValue();
            deduplicatedMap.put(medId, row);
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Fetch existing inventory for this pharmacy to know which medicines exist
            Map<Integer, Integer> existingMap = new HashMap<>();
            String fetchSql = "SELECT id, medicine_id FROM inventory WHERE pharmacy_id = ?";
            try (PreparedStatement fetchPs = conn.prepareStatement(fetchSql)) {
                fetchPs.setInt(1, pharmacyId);
                try (ResultSet rs = fetchPs.executeQuery()) {
                    while (rs.next()) {
                        existingMap.put(rs.getInt("medicine_id"), rs.getInt("id"));
                    }
                }
            }

            String updateSql = "UPDATE inventory SET stock_quantity = ?, unit_price = ?, batch_number = ?, expiry_date = ?, status = ?, last_updated = CURRENT_TIMESTAMP WHERE id = ?";
            String insertSql = "INSERT INTO inventory (pharmacy_id, medicine_id, batch_number, stock_quantity, unit_price, expiry_date, status, last_updated) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

            try (PreparedStatement updatePs = conn.prepareStatement(updateSql);
                 PreparedStatement insertPs = conn.prepareStatement(insertSql)) {

                int updateBatchSize = 0;
                int insertBatchSize = 0;

                for (Map.Entry<Integer, Map<String, Object>> entry : deduplicatedMap.entrySet()) {
                    int medId = entry.getKey();
                    Map<String, Object> row = entry.getValue();

                    int qty = ((Number) row.get("stockQuantity")).intValue();
                    double price = ((Number) row.get("unitPrice")).doubleValue();
                    String batch = (String) row.get("batchNumber");
                    if (batch == null || batch.trim().isEmpty()) {
                        batch = "CSV-BATCH-" + medId;
                    }
                    String expiry = (String) row.get("expiryDate");
                    if (expiry == null || expiry.trim().isEmpty()) {
                        expiry = "2028-12-31";
                    }
                    String status = qty > 20 ? "In Stock" : (qty > 0 ? "Low Stock" : "Out of Stock");

                    if (existingMap.containsKey(medId)) {
                        int invId = existingMap.get(medId);
                        updatePs.setInt(1, qty);
                        updatePs.setDouble(2, price);
                        updatePs.setString(3, batch.trim());
                        updatePs.setString(4, expiry.trim());
                        updatePs.setString(5, status);
                        updatePs.setInt(6, invId);
                        updatePs.addBatch();
                        updateBatchSize++;
                        updatedCount++;

                        if (updateBatchSize % 1000 == 0) {
                            updatePs.executeBatch();
                        }
                    } else {
                        insertPs.setInt(1, pharmacyId);
                        insertPs.setInt(2, medId);
                        insertPs.setString(3, batch.trim());
                        insertPs.setInt(4, qty);
                        insertPs.setDouble(5, price);
                        insertPs.setString(6, expiry.trim());
                        insertPs.setString(7, status);
                        insertPs.addBatch();
                        insertBatchSize++;
                        addedCount++;

                        if (insertBatchSize % 1000 == 0) {
                            insertPs.executeBatch();
                        }
                        // Mark as existing in case of subsequent refs
                        existingMap.put(medId, -1);
                    }
                }

                if (updateBatchSize > 0) {
                    updatePs.executeBatch();
                }
                if (insertBatchSize > 0) {
                    insertPs.executeBatch();
                }

                conn.commit();
                importedCount = updatedCount + addedCount;
            }
        } catch (SQLException e) {
            System.err.println("[InventoryRepository] Error in importCsvBatch: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            result.put("error", e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }

        result.put("imported", importedCount);
        result.put("updated", updatedCount);
        result.put("added", addedCount);
        result.put("distinctMedicines", (Integer) deduplicatedMap.size());
        result.put("distinctUpdated", updatedCount);
        result.put("distinctAdded", addedCount);
        return result;
    }

    public boolean addOrUpdateItem(int pharmacyId, int medicineId, String batchNumber, int quantity, double price, String expiryDate) {
        String checkSql = "SELECT id, stock_quantity, unit_price, expiry_date, batch_number FROM inventory WHERE pharmacy_id = ? AND medicine_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setInt(1, pharmacyId);
            checkPs.setInt(2, medicineId);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    int existingId = rs.getInt("id");
                    int existingQty = rs.getInt("stock_quantity");
                    int newQty = existingQty + quantity;
                    String status = newQty > 20 ? "In Stock" : (newQty > 0 ? "Low Stock" : "Out of Stock");
                    double finalPrice = price > 0 ? price : rs.getDouble("unit_price");
                    String finalExpiry = expiryDate != null && !expiryDate.trim().isEmpty() ? expiryDate.trim() : rs.getString("expiry_date");
                    return updateStock(existingId, newQty, finalPrice, status, finalExpiry);
                } else {
                    String status = quantity > 20 ? "In Stock" : (quantity > 0 ? "Low Stock" : "Out of Stock");
                    String insSql = "INSERT INTO inventory (pharmacy_id, medicine_id, batch_number, stock_quantity, unit_price, expiry_date, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement insPs = conn.prepareStatement(insSql)) {
                        insPs.setInt(1, pharmacyId);
                        insPs.setInt(2, medicineId);
                        insPs.setString(3, batchNumber != null && !batchNumber.trim().isEmpty() ? batchNumber.trim() : "BATCH-" + (int)(Math.random()*9000+1000));
                        insPs.setInt(4, quantity);
                        insPs.setDouble(5, price);
                        insPs.setString(6, expiryDate != null && !expiryDate.trim().isEmpty() ? expiryDate.trim() : "2028-12-31");
                        insPs.setString(7, status);
                        return insPs.executeUpdate() > 0;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean increaseStock(int inventoryId, int quantityToAdd) {
        if (quantityToAdd <= 0) return false;
        String sql = "UPDATE inventory SET stock_quantity = stock_quantity + ?, " +
                     "status = CASE WHEN stock_quantity + ? > 20 THEN 'In Stock' WHEN stock_quantity + ? > 0 THEN 'Low Stock' ELSE 'Out of Stock' END, " +
                     "last_updated = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantityToAdd);
            ps.setInt(2, quantityToAdd);
            ps.setInt(3, quantityToAdd);
            ps.setInt(4, inventoryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public InventoryItem findByPharmacyAndMedicine(int pharmacyId, int medicineId) {
        String sql = "SELECT i.*, m.name as medicine_name, m.generic_name, m.category, m.form, p.pharmacy_name " +
                "FROM inventory i " +
                "JOIN medicines m ON i.medicine_id = m.id " +
                "JOIN pharmacies p ON i.pharmacy_id = p.id " +
                "WHERE i.pharmacy_id = ? AND i.medicine_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pharmacyId);
            ps.setInt(2, medicineId);
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

    public InventoryItem findByPharmacyAndBarcode(int pharmacyId, String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) return null;
        String sql = "SELECT i.*, m.name as medicine_name, m.generic_name, m.category, m.form, p.pharmacy_name " +
                "FROM inventory i " +
                "JOIN medicines m ON i.medicine_id = m.id " +
                "JOIN pharmacies p ON i.pharmacy_id = p.id " +
                "WHERE i.pharmacy_id = ? AND m.barcode = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pharmacyId);
            ps.setString(2, barcode.trim());
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

    public boolean delete(int id) {
        String sql = "DELETE FROM inventory WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteByPharmacyAndMedicine(int pharmacyId, int medicineId) {
        String sql = "DELETE FROM inventory WHERE pharmacy_id = ? AND medicine_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pharmacyId);
            ps.setInt(2, medicineId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[InventoryRepository] deleteByPharmacyAndMedicine error: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteByPharmacyAndId(int pharmacyId, int id) {
        String sql = "DELETE FROM inventory WHERE pharmacy_id = ? AND id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pharmacyId);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[InventoryRepository] deleteByPharmacyAndId error: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean clearStockByPharmacyAndMedicine(int pharmacyId, int medicineId) {
        String sql = "UPDATE inventory SET stock_quantity = 0, status = 'Out of Stock', last_updated = CURRENT_TIMESTAMP WHERE pharmacy_id = ? AND medicine_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pharmacyId);
            ps.setInt(2, medicineId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[InventoryRepository] clearStockByPharmacyAndMedicine error: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean clearStockByPharmacyAndId(int pharmacyId, int id) {
        String sql = "UPDATE inventory SET stock_quantity = 0, status = 'Out of Stock', last_updated = CURRENT_TIMESTAMP WHERE pharmacy_id = ? AND id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pharmacyId);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[InventoryRepository] clearStockByPharmacyAndId error: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public int clearAllStock(int pharmacyId) {
        String sql = "UPDATE inventory SET stock_quantity = 0, status = 'Out of Stock', last_updated = CURRENT_TIMESTAMP WHERE pharmacy_id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pharmacyId);
                int rows = ps.executeUpdate();
                conn.commit();
                return rows;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            System.err.println("[InventoryRepository] clearAllStock error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
        return 0;
    }

    public int removeAllMedicines(int pharmacyId) {
        String sql = "DELETE FROM inventory WHERE pharmacy_id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pharmacyId);
                int rows = ps.executeUpdate();
                conn.commit();
                return rows;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            System.err.println("[InventoryRepository] removeAllMedicines error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
        return 0;
    }

    public Map<String, Object> getPharmacyStockStats(int pharmacyId) {
        Map<String, Object> stats = new HashMap<>();
        String sql = "SELECT " +
                "COUNT(*) as total_items, " +
                "COALESCE(SUM(CASE WHEN stock_quantity > 20 THEN 1 ELSE 0 END), 0) as in_stock_count, " +
                "COALESCE(SUM(CASE WHEN stock_quantity > 0 AND stock_quantity <= 20 THEN 1 ELSE 0 END), 0) as low_stock_count, " +
                "COALESCE(SUM(CASE WHEN stock_quantity = 0 THEN 1 ELSE 0 END), 0) as out_of_stock_count " +
                "FROM inventory WHERE pharmacy_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pharmacyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalMedicines", rs.getInt("total_items"));
                    stats.put("inStock", rs.getInt("in_stock_count"));
                    stats.put("lowStock", rs.getInt("low_stock_count"));
                    stats.put("outOfStock", rs.getInt("out_of_stock_count"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    public List<InventoryItem> getExpiringSoon(int pharmacyId) {
        List<InventoryItem> list = new ArrayList<>();
        String sql = "SELECT i.*, m.name as medicine_name, m.generic_name, m.category, m.form, p.pharmacy_name " +
                "FROM inventory i " +
                "JOIN medicines m ON i.medicine_id = m.id " +
                "JOIN pharmacies p ON i.pharmacy_id = p.id " +
                "WHERE i.pharmacy_id = ? " +
                "ORDER BY i.expiry_date ASC LIMIT 5";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pharmacyId);
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

    public List<Map<String, Object>> searchPharmacyMedicines(int pharmacyId, String query, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        String cleanQ = (query != null) ? query.trim().toLowerCase() : "";
        String likeQ = "%" + cleanQ + "%";
        String startQ = cleanQ + "%";
        int maxResults = (limit > 0) ? limit : 30;

        String sql;
        boolean hasQuery = !cleanQ.isEmpty();

        if (hasQuery) {
            sql = "SELECT m.id as med_id, m.name as medicine_name, m.generic_name, m.brand, m.category, m.dosage, m.form, " +
                    "m.prescription_required, m.barcode, m.base_price, m.image_url, " +
                    "i.id as inventory_id, i.stock_quantity, i.unit_price, i.batch_number, i.expiry_date, i.status " +
                    "FROM medicines m " +
                    "LEFT JOIN inventory i ON i.medicine_id = m.id AND i.pharmacy_id = ? " +
                    "WHERE m.is_active = 1 " +
                    "  AND (LOWER(m.name) LIKE ? OR LOWER(m.generic_name) LIKE ? OR LOWER(COALESCE(m.brand, '')) LIKE ? OR LOWER(COALESCE(m.barcode, '')) = ?) " +
                    "ORDER BY " +
                    "  CASE " +
                    "    WHEN LOWER(m.name) = ? THEN 1 " +
                    "    WHEN LOWER(m.name) LIKE ? THEN 2 " +
                    "    WHEN LOWER(m.generic_name) LIKE ? THEN 3 " +
                    "    WHEN LOWER(COALESCE(m.brand, '')) LIKE ? THEN 4 " +
                    "    ELSE 5 " +
                    "  END, " +
                    "  COALESCE(i.stock_quantity, 0) DESC, " +
                    "  m.name ASC " +
                    "LIMIT ?";
        } else {
            sql = "SELECT m.id as med_id, m.name as medicine_name, m.generic_name, m.brand, m.category, m.dosage, m.form, " +
                    "m.prescription_required, m.barcode, m.base_price, m.image_url, " +
                    "i.id as inventory_id, i.stock_quantity, i.unit_price, i.batch_number, i.expiry_date, i.status " +
                    "FROM medicines m " +
                    "LEFT JOIN inventory i ON i.medicine_id = m.id AND i.pharmacy_id = ? " +
                    "WHERE m.is_active = 1 " +
                    "ORDER BY COALESCE(i.stock_quantity, 0) DESC, m.name ASC " +
                    "LIMIT ?";
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pharmacyId);
            if (hasQuery) {
                ps.setString(2, likeQ);
                ps.setString(3, likeQ);
                ps.setString(4, likeQ);
                ps.setString(5, cleanQ);
                ps.setString(6, cleanQ);
                ps.setString(7, startQ);
                ps.setString(8, startQ);
                ps.setString(9, startQ);
                ps.setInt(10, maxResults);
            } else {
                ps.setInt(2, maxResults);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    int medId = rs.getInt("med_id");
                    String medName = rs.getString("medicine_name");
                    String genName = rs.getString("generic_name");
                    String brand = rs.getString("brand");
                    String cat = rs.getString("category");
                    String dosage = rs.getString("dosage");
                    String form = rs.getString("form");
                    boolean rxReq = rs.getInt("prescription_required") == 1;
                    String barcode = rs.getString("barcode");
                    double basePrice = rs.getDouble("base_price");
                    String img = rs.getString("image_url");

                    int invId = rs.getInt("inventory_id");
                    int stock = rs.getInt("stock_quantity");
                    double unitPrice = rs.getDouble("unit_price");
                    if (unitPrice <= 0) unitPrice = basePrice;
                    String batch = rs.getString("batch_number");
                    String expiry = rs.getString("expiry_date");
                    String status = rs.getString("status");
                    if (status == null || status.trim().isEmpty()) {
                        status = stock > 20 ? "In Stock" : (stock > 0 ? "Low Stock" : "Out of Stock");
                    }

                    row.put("medicineId", medId);
                    row.put("inventoryId", invId);
                    row.put("name", medName);
                    row.put("medicineName", medName);
                    row.put("genericName", genName != null ? genName : "");
                    row.put("brand", brand != null ? brand : "");
                    row.put("category", cat != null ? cat : "");
                    row.put("dosage", dosage != null ? dosage : "");
                    row.put("form", form != null ? form : "");
                    row.put("prescriptionRequired", rxReq);
                    row.put("barcode", barcode != null ? barcode : "");
                    row.put("stockQuantity", stock);
                    row.put("stock", stock);
                    row.put("unitPrice", unitPrice);
                    row.put("price", unitPrice);
                    row.put("basePrice", basePrice);
                    row.put("batchNumber", batch != null ? batch : "");
                    row.put("expiryDate", expiry != null ? expiry : "");
                    row.put("status", status);
                    row.put("imageUrl", img != null ? img : "");

                    results.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
    }

    private InventoryItem mapRow(ResultSet rs) throws SQLException {
        InventoryItem item = new InventoryItem();
        item.setId(rs.getInt("id"));
        item.setPharmacyId(rs.getInt("pharmacy_id"));
        item.setPharmacyName(rs.getString("pharmacy_name"));
        item.setMedicineId(rs.getInt("medicine_id"));
        item.setMedicineName(rs.getString("medicine_name"));
        item.setGenericName(rs.getString("generic_name"));
        item.setCategory(rs.getString("category"));
        item.setForm(rs.getString("form"));
        item.setBatchNumber(rs.getString("batch_number"));
        item.setStockQuantity(rs.getInt("stock_quantity"));
        item.setUnitPrice(rs.getDouble("unit_price"));
        item.setExpiryDate(rs.getString("expiry_date"));
        item.setStatus(rs.getString("status"));
        item.setLastUpdated(rs.getString("last_updated"));
        return item;
    }
}
