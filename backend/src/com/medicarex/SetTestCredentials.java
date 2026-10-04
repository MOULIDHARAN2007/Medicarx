package com.medicarex;

import com.medicarex.auth.PasswordHasher;
import com.medicarex.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class SetTestCredentials {
    public static void main(String[] args) {
        DatabaseConnection.initialize();
        String defaultPassword = "Password@123";
        String salt = PasswordHasher.generateSalt();
        String passwordHash = PasswordHasher.hashPassword(defaultPassword, salt);

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Update default seeded accounts
            String sql = "UPDATE users SET password_hash = ?, salt = ?, is_active = 1 WHERE email IN (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, passwordHash);
                ps.setString(2, salt);
                ps.setString(3, "priya@example.com");
                ps.setString(4, "arjun@example.com");
                ps.setString(5, "healthplus@example.com");
                ps.setString(6, "admin@example.com");
                ps.setString(7, "ambulance@example.com");
                int updated = ps.executeUpdate();
                System.out.println("Updated " + updated + " pre-seeded accounts with password: " + defaultPassword);
            }

            // Also ensure ambulance user exists for AMBULANCE role testing
            String ambCheck = "SELECT id FROM users WHERE email = 'ambulance@example.com'";
            try (Statement stmt = conn.createStatement();
                 var rs = stmt.executeQuery(ambCheck)) {
                if (!rs.next()) {
                    String insertAmb = "INSERT INTO users (email, password_hash, salt, full_name, phone, role, is_active) VALUES " +
                            "('ambulance@example.com', '" + passwordHash + "', '" + salt + "', 'Ambulance Unit 108', '+91 91100 11223', 'AMBULANCE', 1)";
                    stmt.execute(insertAmb);
                    System.out.println("Created ambulance test user: ambulance@example.com");
                }
            }
            System.out.println("Credentials update complete!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
