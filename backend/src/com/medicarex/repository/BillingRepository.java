package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Bill;
import com.medicarex.model.BillItem;
import com.medicarex.model.InventoryTransaction;

import java.sql.*;
import java.util.*;

public class BillingRepository {

    private final NotificationRepository notificationRepo = new NotificationRepository();

    public static class BillCreationResult {
        public Bill bill;
        public List<String> stockWarnings = new ArrayList<>();
    }

    public synchronized BillCreationResult createBillWithInventoryDeduction(
            int pharmacyId,
            int userId,
            Integer patientId,
            String customerName,
            String customerPhone,
            Integer prescriptionId,
            double discount,
            double tax,
            String paymentMethod,
            String notes,
            List<Map<String, Object>> items,
            String createdBy) throws Exception {

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Billing cart cannot be empty.");
        }

        Connection conn = null;
        BillCreationResult result = new BillCreationResult();

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Validate all cart items and check inventory stock
            @SuppressWarnings("unused")
            class ItemDetail {
                int medicineId;
                String medicineName;
                String brand;
                String dosage;
                String batchNumber;
                int requestedQty;
                double unitPrice;
                int currentStock;
                boolean prescriptionRequired;
            }

            List<ItemDetail> validatedItems = new ArrayList<>();
            double subtotal = 0.0;

            String selectInvSql = "SELECT i.id, i.stock_quantity, i.unit_price, i.batch_number, " +
                    "m.name as medicine_name, m.brand, m.dosage, m.prescription_required " +
                    "FROM inventory i " +
                    "JOIN medicines m ON i.medicine_id = m.id " +
                    "WHERE i.pharmacy_id = ? AND i.medicine_id = ?";

            try (PreparedStatement invPs = conn.prepareStatement(selectInvSql)) {
                for (Map<String, Object> itm : items) {
                    int medId = ((Number) itm.get("medicineId")).intValue();
                    int qty = ((Number) itm.get("quantity")).intValue();

                    if (qty <= 0) {
                        throw new IllegalArgumentException("Item quantity must be greater than 0.");
                    }

                    invPs.setInt(1, pharmacyId);
                    invPs.setInt(2, medId);

                    try (ResultSet rs = invPs.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Medicine (ID " + medId + ") is not available in your pharmacy inventory.");
                        }

                        int currentStock = rs.getInt("stock_quantity");
                        String medName = rs.getString("medicine_name");
                        String brand = rs.getString("brand");
                        String dosage = rs.getString("dosage");
                        String batchNumber = rs.getString("batch_number");
                        double unitPrice = rs.getDouble("unit_price");
                        boolean rxReq = rs.getBoolean("prescription_required");

                        if (currentStock < qty) {
                            throw new IllegalStateException("Insufficient stock for \"" + medName + "\". Available: " + currentStock + ", Requested: " + qty);
                        }

                        ItemDetail detail = new ItemDetail();
                        detail.medicineId = medId;
                        detail.medicineName = medName;
                        detail.brand = brand;
                        detail.dosage = dosage;
                        detail.batchNumber = batchNumber != null ? batchNumber : "BATCH-DEF";
                        detail.requestedQty = qty;
                        detail.unitPrice = unitPrice;
                        detail.currentStock = currentStock;
                        detail.prescriptionRequired = rxReq;

                        validatedItems.add(detail);
                        subtotal += (unitPrice * qty);
                    }
                }
            }

            // 2. Compute financial totals
            double finalDiscount = Math.max(0.0, discount);
            double finalTax = Math.max(0.0, tax);
            double totalAmount = Math.max(0.0, (subtotal - finalDiscount) + finalTax);

            // Generate unique Bill Number
            String billNumber = "INV-" + (System.currentTimeMillis() % 10000000) + "-" + ((int) (Math.random() * 900 + 100));

            // 3. Insert Bill record
            String insertBillSql = "INSERT INTO bills (pharmacy_id, bill_number, patient_id, customer_name, customer_phone, prescription_id, subtotal, discount, tax, total_amount, payment_method, payment_status, notes, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PAID', ?, ?)";

            int generatedBillId = 0;
            try (PreparedStatement billPs = conn.prepareStatement(insertBillSql, Statement.RETURN_GENERATED_KEYS)) {
                billPs.setInt(1, pharmacyId);
                billPs.setString(2, billNumber);
                if (patientId != null && patientId > 0) billPs.setInt(3, patientId); else billPs.setNull(3, Types.INTEGER);
                billPs.setString(4, customerName != null && !customerName.trim().isEmpty() ? customerName.trim() : "Walk-in Customer");
                billPs.setString(5, customerPhone != null ? customerPhone.trim() : "");
                if (prescriptionId != null && prescriptionId > 0) billPs.setInt(6, prescriptionId); else billPs.setNull(6, Types.INTEGER);
                billPs.setDouble(7, subtotal);
                billPs.setDouble(8, finalDiscount);
                billPs.setDouble(9, finalTax);
                billPs.setDouble(10, totalAmount);
                billPs.setString(11, paymentMethod != null && !paymentMethod.trim().isEmpty() ? paymentMethod.trim().toUpperCase() : "CASH");
                billPs.setString(12, notes != null ? notes.trim() : "");
                billPs.setString(13, createdBy != null ? createdBy : "Staff");

                int affected = billPs.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("Failed to create bill record.");
                }

                try (ResultSet gk = billPs.getGeneratedKeys()) {
                    if (gk.next()) {
                        generatedBillId = gk.getInt(1);
                    }
                }
            }

            if (generatedBillId == 0) {
                throw new SQLException("Failed to retrieve generated bill ID.");
            }

            // 4. Insert Bill Items and Atomically Deduct Inventory Stock
            String insertItemSql = "INSERT INTO bill_items (bill_id, medicine_id, medicine_name, batch_number, quantity, unit_price, subtotal) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            String updateStockSql = "UPDATE inventory SET stock_quantity = stock_quantity - ?, " +
                    "status = CASE WHEN stock_quantity - ? > 20 THEN 'In Stock' WHEN stock_quantity - ? > 0 THEN 'Low Stock' ELSE 'Out of Stock' END, " +
                    "last_updated = CURRENT_TIMESTAMP " +
                    "WHERE pharmacy_id = ? AND medicine_id = ? AND stock_quantity >= ?";

            String insertAuditSql = "INSERT INTO inventory_transactions (pharmacy_id, medicine_id, transaction_type, quantity_change, previous_stock, new_stock, reference_id, notes, created_by) " +
                    "VALUES (?, ?, 'SALE', ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement itemPs = conn.prepareStatement(insertItemSql);
                 PreparedStatement stockPs = conn.prepareStatement(updateStockSql);
                 PreparedStatement auditPs = conn.prepareStatement(insertAuditSql)) {

                for (ItemDetail item : validatedItems) {
                    double itemSubtotal = item.unitPrice * item.requestedQty;

                    // A. Bill item insert
                    itemPs.setInt(1, generatedBillId);
                    itemPs.setInt(2, item.medicineId);
                    itemPs.setString(3, item.medicineName);
                    itemPs.setString(4, item.batchNumber);
                    itemPs.setInt(5, item.requestedQty);
                    itemPs.setDouble(6, item.unitPrice);
                    itemPs.setDouble(7, itemSubtotal);
                    itemPs.executeUpdate();

                    // B. Inventory stock update with concurrency guard
                    stockPs.setInt(1, item.requestedQty);
                    stockPs.setInt(2, item.requestedQty);
                    stockPs.setInt(3, item.requestedQty);
                    stockPs.setInt(4, pharmacyId);
                    stockPs.setInt(5, item.medicineId);
                    stockPs.setInt(6, item.requestedQty); // MUST have at least requestedQty in stock

                    int rows = stockPs.executeUpdate();
                    if (rows == 0) {
                        throw new ConcurrentModificationException("Stock conflict: Insufficient stock remaining for \"" + item.medicineName + "\". Transaction aborted.");
                    }

                    // C. Inventory transaction audit entry
                    int newStock = item.currentStock - item.requestedQty;
                    auditPs.setInt(1, pharmacyId);
                    auditPs.setInt(2, item.medicineId);
                    auditPs.setInt(3, -item.requestedQty);
                    auditPs.setInt(4, item.currentStock);
                    auditPs.setInt(5, newStock);
                    auditPs.setString(6, billNumber);
                    auditPs.setString(7, "POS Sale Bill #" + billNumber);
                    auditPs.setString(8, createdBy != null ? createdBy : "Staff");
                    auditPs.executeUpdate();

                    // Check low stock / out of stock warning
                    if (newStock == 0) {
                        result.stockWarnings.add("Out of stock: " + item.medicineName);
                    } else if (newStock <= 5) {
                        result.stockWarnings.add("Low stock: " + item.medicineName + " — " + newStock + " remaining.");
                    }
                }
            }

            // 5. Commit Transaction
            conn.commit();

            // 6. Push notifications for low stock / out of stock items
            if (userId > 0) {
                for (String warning : result.stockWarnings) {
                    notificationRepo.create(userId, "Inventory", "Inventory Alert", warning, generatedBillId, "Bill");
                }
            }

            // 7. Load and return completed Bill model
            result.bill = findBillById(generatedBillId);
            return result;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rbEx) {
                    rbEx.printStackTrace();
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException cEx) {
                    cEx.printStackTrace();
                }
            }
        }
    }

    public List<Bill> findBillsByPharmacy(int pharmacyId, String query, String paymentStatus, int limit, int offset) {
        List<Bill> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT b.*, p.pharmacy_name, p.address as pharmacy_address, p.phone as pharmacy_phone " +
                "FROM bills b " +
                "JOIN pharmacies p ON b.pharmacy_id = p.id " +
                "WHERE b.pharmacy_id = ? ");

        List<Object> params = new ArrayList<>();
        params.add(pharmacyId);

        if (paymentStatus != null && !paymentStatus.equalsIgnoreCase("All") && !paymentStatus.trim().isEmpty()) {
            sql.append("AND LOWER(b.payment_status) = LOWER(?) ");
            params.add(paymentStatus.trim());
        }

        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (LOWER(b.bill_number) LIKE ? OR LOWER(b.customer_name) LIKE ? OR LOWER(b.customer_phone) LIKE ?) ");
            String q = "%" + query.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }

        sql.append("ORDER BY b.id DESC LIMIT ? OFFSET ?");
        params.add(limit > 0 ? limit : 50);
        params.add(offset >= 0 ? offset : 0);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Bill bill = mapBillRow(rs);
                    bill.setItems(findBillItems(conn, bill.getId()));
                    list.add(bill);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Bill findBillById(int billId) {
        String sql = "SELECT b.*, p.pharmacy_name, p.address as pharmacy_address, p.phone as pharmacy_phone " +
                "FROM bills b " +
                "JOIN pharmacies p ON b.pharmacy_id = p.id " +
                "WHERE b.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Bill bill = mapBillRow(rs);
                    bill.setItems(findBillItems(conn, bill.getId()));
                    return bill;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Bill findBillByNumber(String billNumber) {
        String sql = "SELECT b.*, p.pharmacy_name, p.address as pharmacy_address, p.phone as pharmacy_phone " +
                "FROM bills b " +
                "JOIN pharmacies p ON b.pharmacy_id = p.id " +
                "WHERE b.bill_number = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, billNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Bill bill = mapBillRow(rs);
                    bill.setItems(findBillItems(conn, bill.getId()));
                    return bill;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<BillItem> findBillItems(Connection conn, int billId) throws SQLException {
        List<BillItem> items = new ArrayList<>();
        String sql = "SELECT bi.*, m.brand, m.dosage " +
                "FROM bill_items bi " +
                "LEFT JOIN medicines m ON bi.medicine_id = m.id " +
                "WHERE bi.bill_id = ? ORDER BY bi.id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BillItem item = new BillItem();
                    item.setId(rs.getInt("id"));
                    item.setBillId(rs.getInt("bill_id"));
                    item.setMedicineId(rs.getInt("medicine_id"));
                    item.setMedicineName(rs.getString("medicine_name"));
                    item.setBrand(rs.getString("brand"));
                    item.setDosage(rs.getString("dosage"));
                    item.setBatchNumber(rs.getString("batch_number"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getDouble("unit_price"));
                    item.setSubtotal(rs.getDouble("subtotal"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    public synchronized boolean cancelBillWithRestock(int billId, int pharmacyId, String reason, String cancelledBy) throws Exception {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Fetch bill and verify ownership
            Bill bill = findBillById(billId);
            if (bill == null) {
                throw new IllegalArgumentException("Bill not found.");
            }
            if (bill.getPharmacyId() != pharmacyId) {
                throw new SecurityException("Unauthorized: This bill belongs to another pharmacy.");
            }
            if ("CANCELLED".equalsIgnoreCase(bill.getPaymentStatus())) {
                throw new IllegalStateException("Bill is already cancelled.");
            }

            // 2. Mark bill as CANCELLED
            String updateBillSql = "UPDATE bills SET payment_status = 'CANCELLED', notes = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateBillSql)) {
                String updatedNotes = (bill.getNotes() != null ? bill.getNotes() : "") + " [CANCELLED: " + (reason != null ? reason : "Cancelled by pharmacy") + "]";
                ps.setString(1, updatedNotes.trim());
                ps.setInt(2, billId);
                ps.executeUpdate();
            }

            // 3. Restock inventory and write audit trail
            String selectStockSql = "SELECT stock_quantity FROM inventory WHERE pharmacy_id = ? AND medicine_id = ?";
            String restockSql = "UPDATE inventory SET stock_quantity = stock_quantity + ?, " +
                    "status = CASE WHEN stock_quantity + ? > 20 THEN 'In Stock' WHEN stock_quantity + ? > 0 THEN 'Low Stock' ELSE 'Out of Stock' END, " +
                    "last_updated = CURRENT_TIMESTAMP " +
                    "WHERE pharmacy_id = ? AND medicine_id = ?";

            String auditSql = "INSERT INTO inventory_transactions (pharmacy_id, medicine_id, transaction_type, quantity_change, previous_stock, new_stock, reference_id, notes, created_by) " +
                    "VALUES (?, ?, 'CANCEL', ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement selectPs = conn.prepareStatement(selectStockSql);
                 PreparedStatement restockPs = conn.prepareStatement(restockSql);
                 PreparedStatement auditPs = conn.prepareStatement(auditSql)) {

                for (BillItem item : bill.getItems()) {
                    int prevStock = 0;
                    selectPs.setInt(1, pharmacyId);
                    selectPs.setInt(2, item.getMedicineId());
                    try (ResultSet rs = selectPs.executeQuery()) {
                        if (rs.next()) {
                            prevStock = rs.getInt("stock_quantity");
                        }
                    }

                    int restockQty = item.getQuantity();
                    restockPs.setInt(1, restockQty);
                    restockPs.setInt(2, restockQty);
                    restockPs.setInt(3, restockQty);
                    restockPs.setInt(4, pharmacyId);
                    restockPs.setInt(5, item.getMedicineId());
                    restockPs.executeUpdate();

                    int newStock = prevStock + restockQty;
                    auditPs.setInt(1, pharmacyId);
                    auditPs.setInt(2, item.getMedicineId());
                    auditPs.setInt(3, restockQty);
                    auditPs.setInt(4, prevStock);
                    auditPs.setInt(5, newStock);
                    auditPs.setString(6, bill.getBillNumber());
                    auditPs.setString(7, "Restock from Cancelled Bill #" + bill.getBillNumber() + (reason != null ? " (" + reason + ")" : ""));
                    auditPs.setString(8, cancelledBy != null ? cancelledBy : "Staff");
                    auditPs.executeUpdate();
                }
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rbEx) {
                    rbEx.printStackTrace();
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException cEx) {
                    cEx.printStackTrace();
                }
            }
        }
    }

    public List<InventoryTransaction> findInventoryTransactions(int pharmacyId, Integer medicineId, int limit) {
        List<InventoryTransaction> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT it.*, m.name as medicine_name " +
                "FROM inventory_transactions it " +
                "JOIN medicines m ON it.medicine_id = m.id " +
                "WHERE it.pharmacy_id = ? ");

        List<Object> params = new ArrayList<>();
        params.add(pharmacyId);

        if (medicineId != null && medicineId > 0) {
            sql.append("AND it.medicine_id = ? ");
            params.add(medicineId);
        }

        sql.append("ORDER BY it.id DESC LIMIT ?");
        params.add(limit > 0 ? limit : 50);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InventoryTransaction tx = new InventoryTransaction();
                    tx.setId(rs.getInt("id"));
                    tx.setPharmacyId(rs.getInt("pharmacy_id"));
                    tx.setMedicineId(rs.getInt("medicine_id"));
                    tx.setMedicineName(rs.getString("medicine_name"));
                    tx.setTransactionType(rs.getString("transaction_type"));
                    tx.setQuantityChange(rs.getInt("quantity_change"));
                    tx.setPreviousStock(rs.getInt("previous_stock"));
                    tx.setNewStock(rs.getInt("new_stock"));
                    tx.setReferenceId(rs.getString("reference_id"));
                    tx.setNotes(rs.getString("notes"));
                    tx.setCreatedAt(rs.getString("created_at"));
                    tx.setCreatedBy(rs.getString("created_by"));
                    list.add(tx);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private Bill mapBillRow(ResultSet rs) throws SQLException {
        Bill bill = new Bill();
        bill.setId(rs.getInt("id"));
        bill.setPharmacyId(rs.getInt("pharmacy_id"));
        try {
            bill.setPharmacyName(rs.getString("pharmacy_name"));
            bill.setPharmacyAddress(rs.getString("pharmacy_address"));
            bill.setPharmacyPhone(rs.getString("pharmacy_phone"));
        } catch (SQLException ignored) {}
        bill.setBillNumber(rs.getString("bill_number"));
        int patId = rs.getInt("patient_id");
        if (!rs.wasNull()) bill.setPatientId(patId);
        bill.setCustomerName(rs.getString("customer_name"));
        bill.setCustomerPhone(rs.getString("customer_phone"));
        int rxId = rs.getInt("prescription_id");
        if (!rs.wasNull()) bill.setPrescriptionId(rxId);
        bill.setSubtotal(rs.getDouble("subtotal"));
        bill.setDiscount(rs.getDouble("discount"));
        bill.setTax(rs.getDouble("tax"));
        bill.setTotalAmount(rs.getDouble("total_amount"));
        bill.setPaymentMethod(rs.getString("payment_method"));
        bill.setPaymentStatus(rs.getString("payment_status"));
        bill.setNotes(rs.getString("notes"));
        bill.setCreatedAt(rs.getString("created_at"));
        bill.setCreatedBy(rs.getString("created_by"));
        return bill;
    }
}
