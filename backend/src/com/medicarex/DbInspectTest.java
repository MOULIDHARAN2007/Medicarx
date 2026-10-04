package com.medicarex;

import com.medicarex.config.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbInspectTest {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   DATABASE INSPECTION & VERIFICATION TOOL        ");
        System.out.println("==================================================");

        DatabaseConnection.initialize();
        System.out.println("Active DB Type: " + DatabaseConnection.getActiveDbType());

        try (Connection conn = DatabaseConnection.getConnection()) {
            // 1. Medicines count
            int medCount = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM medicines");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) medCount = rs.getInt(1);
            }
            System.out.println("1. Total medicines count: " + medCount);

            // 2. Inventory count
            int invCount = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM inventory");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) invCount = rs.getInt(1);
            }
            System.out.println("2. Total inventory count: " + invCount);

            // 3. Unique medicine_id count in inventory
            int uniqueMedInInv = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(DISTINCT medicine_id) FROM inventory");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) uniqueMedInInv = rs.getInt(1);
            }
            System.out.println("3. Distinct medicine_id in inventory: " + uniqueMedInInv);

            // 4. Orphan inventory records
            int orphanCount = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM inventory i LEFT JOIN medicines m ON m.id = i.medicine_id WHERE m.id IS NULL");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) orphanCount = rs.getInt(1);
            }
            System.out.println("4. Orphan inventory count (inventory without matching medicine): " + orphanCount);

            // 5. Duplicate inventory records per (pharmacy_id, medicine_id)
            int dupInvCount = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT pharmacy_id, medicine_id, COUNT(*) as cnt FROM inventory GROUP BY pharmacy_id, medicine_id HAVING COUNT(*) > 1");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    dupInvCount++;
                    System.out.println("   - Duplicate found: Pharmacy " + rs.getInt("pharmacy_id") + ", Medicine " + rs.getInt("medicine_id") + " (count=" + rs.getInt("cnt") + ")");
                }
            }
            System.out.println("5. Duplicate inventory count: " + dupInvCount);

            // 6. Duplicate medicines count by name
            int dupMedCount = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT name, COUNT(*) as cnt FROM medicines GROUP BY LOWER(name) HAVING COUNT(*) > 1");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    dupMedCount++;
                    System.out.println("   - Duplicate medicine name: '" + rs.getString("name") + "' (count=" + rs.getInt("cnt") + ")");
                }
            }
            System.out.println("6. Duplicate medicines count: " + dupMedCount);

            // 7. Show sample medicines
            System.out.println("\nSample Medicines in Catalog:");
            try (PreparedStatement ps = conn.prepareStatement("SELECT id, name, generic_name, category, dosage, form, base_price FROM medicines LIMIT 10");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("   #" + rs.getInt("id") + " | " + rs.getString("name") + " | " + rs.getString("generic_name") + " | " + rs.getString("category") + " | " + rs.getString("dosage") + " | " + rs.getString("form") + " | ₹" + rs.getDouble("base_price"));
                }
            }

            // 8. Show inventory per pharmacy
            System.out.println("\nInventory distribution by Pharmacy:");
            try (PreparedStatement ps = conn.prepareStatement("SELECT p.id, p.pharmacy_name, COUNT(i.id) as total_items, SUM(i.stock_quantity) as total_stock FROM pharmacies p LEFT JOIN inventory i ON p.id = i.pharmacy_id GROUP BY p.id, p.pharmacy_name");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("   Pharmacy #" + rs.getInt("id") + " (" + rs.getString("pharmacy_name") + "): items=" + rs.getInt("total_items") + ", total_stock=" + rs.getInt("total_stock"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
