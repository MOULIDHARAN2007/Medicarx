package com.medicarex.repository;

import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Order;
import com.medicarex.model.OrderItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderRepository {

    public List<Order> findByPharmacyId(int pharmacyId, String statusFilter) {
        List<Order> orders = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, order_number, pharmacy_id, patient_id, customer_name, customer_phone, total_amount, status, payment_method, order_date, created_at FROM orders WHERE pharmacy_id = ?");

        if (statusFilter != null && !statusFilter.isEmpty() && !"All".equalsIgnoreCase(statusFilter)) {
            sql.append(" AND status = ?");
        }
        sql.append(" ORDER BY id DESC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setInt(1, pharmacyId);
            if (statusFilter != null && !statusFilter.isEmpty() && !"All".equalsIgnoreCase(statusFilter)) {
                stmt.setString(2, statusFilter);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Order o = mapOrder(rs);
                    o.setItems(findItemsByOrderId(conn, o.getId()));
                    orders.add(o);
                }
            }
        } catch (SQLException e) {
            System.err.println("[OrderRepository] findByPharmacyId error: " + e.getMessage());
        }
        return orders;
    }

    public List<Order> findAll(String statusFilter) {
        List<Order> orders = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, order_number, pharmacy_id, patient_id, customer_name, customer_phone, total_amount, status, payment_method, order_date, created_at FROM orders");

        if (statusFilter != null && !statusFilter.isEmpty() && !"All".equalsIgnoreCase(statusFilter)) {
            sql.append(" WHERE status = ?");
        }
        sql.append(" ORDER BY id DESC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            if (statusFilter != null && !statusFilter.isEmpty() && !"All".equalsIgnoreCase(statusFilter)) {
                stmt.setString(1, statusFilter);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Order o = mapOrder(rs);
                    o.setItems(findItemsByOrderId(conn, o.getId()));
                    orders.add(o);
                }
            }
        } catch (SQLException e) {
            System.err.println("[OrderRepository] findAll error: " + e.getMessage());
        }
        return orders;
    }

    public Order findById(int orderId) {
        String sql = "SELECT id, order_number, pharmacy_id, patient_id, customer_name, customer_phone, total_amount, status, payment_method, order_date, created_at FROM orders WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Order o = mapOrder(rs);
                    o.setItems(findItemsByOrderId(conn, o.getId()));
                    return o;
                }
            }
        } catch (SQLException e) {
            System.err.println("[OrderRepository] findById error: " + e.getMessage());
        }
        return null;
    }

    public boolean updateStatus(int orderId, String newStatus) {
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newStatus);
            stmt.setInt(2, orderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[OrderRepository] updateStatus error: " + e.getMessage());
            return false;
        }
    }

    public Order createOrder(int pharmacyId, Integer patientId, String customerName, String customerPhone, double totalAmount, String paymentMethod, String orderDate, List<OrderItem> items) {
        String sql = "INSERT INTO orders (order_number, pharmacy_id, patient_id, customer_name, customer_phone, total_amount, status, payment_method, order_date) VALUES (?, ?, ?, ?, ?, ?, 'Pending', ?, ?)";
        String orderNum = "ORD-" + System.currentTimeMillis() % 1000000;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            int generatedId = -1;

            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, orderNum);
                stmt.setInt(2, pharmacyId);
                if (patientId != null) stmt.setInt(3, patientId); else stmt.setNull(3, Types.INTEGER);
                stmt.setString(4, customerName);
                stmt.setString(5, customerPhone);
                stmt.setDouble(6, totalAmount);
                stmt.setString(7, paymentMethod != null ? paymentMethod : "UPI / Card");
                stmt.setString(8, orderDate);

                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) generatedId = rs.getInt(1);
                }
            }

            if (generatedId > 0 && items != null && !items.isEmpty()) {
                String itemSql = "INSERT INTO order_items (order_id, medicine_id, medicine_name, quantity, unit_price) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement itemStmt = conn.prepareStatement(itemSql)) {
                    for (OrderItem item : items) {
                        itemStmt.setInt(1, generatedId);
                        if (item.getMedicineId() != null) itemStmt.setInt(2, item.getMedicineId()); else itemStmt.setNull(2, Types.INTEGER);
                        itemStmt.setString(3, item.getMedicineName());
                        itemStmt.setInt(4, item.getQuantity());
                        itemStmt.setDouble(5, item.getUnitPrice());
                        itemStmt.addBatch();
                    }
                    itemStmt.executeBatch();
                }
            }

            conn.commit();
            conn.setAutoCommit(true);
            return findById(generatedId);
        } catch (SQLException e) {
            System.err.println("[OrderRepository] createOrder error: " + e.getMessage());
            return null;
        }
    }

    private List<OrderItem> findItemsByOrderId(Connection conn, int orderId) throws SQLException {
        List<OrderItem> list = new ArrayList<>();
        String sql = "SELECT id, order_id, medicine_id, medicine_name, quantity, unit_price FROM order_items WHERE order_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setId(rs.getInt("id"));
                    item.setOrderId(rs.getInt("order_id"));
                    int medId = rs.getInt("medicine_id");
                    if (!rs.wasNull()) item.setMedicineId(medId);
                    item.setMedicineName(rs.getString("medicine_name"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getDouble("unit_price"));
                    list.add(item);
                }
            }
        }
        return list;
    }

    private Order mapOrder(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setId(rs.getInt("id"));
        o.setOrderNumber(rs.getString("order_number"));
        o.setPharmacyId(rs.getInt("pharmacy_id"));
        int patId = rs.getInt("patient_id");
        if (!rs.wasNull()) o.setPatientId(patId);
        o.setCustomerName(rs.getString("customer_name"));
        o.setCustomerPhone(rs.getString("customer_phone"));
        o.setTotalAmount(rs.getDouble("total_amount"));
        o.setStatus(rs.getString("status"));
        o.setPaymentMethod(rs.getString("payment_method"));
        o.setOrderDate(rs.getString("order_date"));
        o.setCreatedAt(rs.getString("created_at"));
        return o;
    }
}
