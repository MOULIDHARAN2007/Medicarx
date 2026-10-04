package com.medicarex.repository;

import com.medicarex.auth.PasswordHasher;
import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UserRepository {

    public User findByEmail(String email) {
        if (email == null) return null;
        String sql = "SELECT id, email, password_hash, salt, full_name, phone, role, avatar_url, is_active, auth_provider, google_subject_id, created_by, created_at FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUserFromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User findById(int id) {
        String sql = "SELECT id, email, password_hash, salt, full_name, phone, role, avatar_url, is_active, auth_provider, google_subject_id, created_by, created_at FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUserFromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User findByGoogleSubjectId(String googleSubjectId) {
        if (googleSubjectId == null || googleSubjectId.trim().isEmpty()) return null;
        String sql = "SELECT id, email, password_hash, salt, full_name, phone, role, avatar_url, is_active, auth_provider, google_subject_id, created_by, created_at FROM users WHERE google_subject_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, googleSubjectId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUserFromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean linkGoogleAccount(int userId, String googleSubjectId, String avatarUrl) {
        String sql = "UPDATE users SET google_subject_id = ?, auth_provider = CASE WHEN auth_provider = 'LOCAL' THEN 'GOOGLE' ELSE auth_provider END"
                + (avatarUrl != null && !avatarUrl.trim().isEmpty() ? ", avatar_url = COALESCE(avatar_url, ?)" : "")
                + " WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, googleSubjectId.trim());
            int paramIndex = 2;
            if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                ps.setString(paramIndex++, avatarUrl.trim());
            }
            ps.setInt(paramIndex, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserRepository] Error linking Google account: " + e.getMessage());
        }
        return false;
    }

    public User createUserWithGoogle(String email, String fullName, String googleSubjectId, String avatarUrl, String role, Map<String, Object> extra) {
        String salt = PasswordHasher.generateSalt();
        String randomPass = UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
        String passwordHash = PasswordHasher.hashPassword(randomPass, salt);

        String sql = "INSERT INTO users (email, password_hash, salt, full_name, phone, role, avatar_url, is_active, auth_provider, google_subject_id) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 'GOOGLE', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email.trim().toLowerCase());
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.setString(4, fullName != null && !fullName.trim().isEmpty() ? fullName.trim() : "Google User");
            ps.setString(5, "");
            ps.setString(6, role != null ? role.toUpperCase() : "PATIENT");
            ps.setString(7, avatarUrl != null && !avatarUrl.trim().isEmpty() ? avatarUrl.trim() : "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150");
            ps.setString(8, googleSubjectId != null ? googleSubjectId.trim() : null);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        createSubProfile(conn, id, role != null ? role.toUpperCase() : "PATIENT", fullName, extra);
                        return findById(id);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User createUser(String email, String passwordHash, String salt, String fullName, String phone, String role) {
        return createUser(email, passwordHash, salt, fullName, phone, role, new HashMap<>());
    }

    public User createAdminUser(String email, String passwordHash, String salt, String fullName, String phone, Integer createdByAdminId) {
        String sql = "INSERT INTO users (email, password_hash, salt, full_name, phone, role, avatar_url, is_active, auth_provider, created_by) VALUES (?, ?, ?, ?, ?, 'ADMIN', ?, 1, 'LOCAL', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email.trim().toLowerCase());
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.setString(4, fullName);
            ps.setString(5, phone != null ? phone : "");
            ps.setString(6, "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150");
            if (createdByAdminId != null) {
                ps.setInt(7, createdByAdminId);
            } else {
                ps.setNull(7, Types.INTEGER);
            }

            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        return findById(id);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User createUser(String email, String passwordHash, String salt, String fullName, String phone, String role, Map<String, Object> extra) {
        boolean activeByDefault = !"DOCTOR".equalsIgnoreCase(role) && !"PHARMACY".equalsIgnoreCase(role);
        return createUser(email, passwordHash, salt, fullName, phone, role, activeByDefault, extra);
    }

    public User createUser(String email, String passwordHash, String salt, String fullName, String phone, String role, boolean isActive, Map<String, Object> extra) {
        String sql = "INSERT INTO users (email, password_hash, salt, full_name, phone, role, avatar_url, is_active, auth_provider) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'LOCAL')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email.trim().toLowerCase());
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.setString(4, fullName);
            ps.setString(5, phone != null ? phone : "");
            ps.setString(6, role != null ? role.toUpperCase() : "PATIENT");
            ps.setString(7, "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150");
            ps.setInt(8, isActive ? 1 : 0);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        createSubProfile(conn, id, role != null ? role.toUpperCase() : "PATIENT", fullName, extra);
                        return findById(id);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User createPatientUser(String email, String passwordHash, String salt, String fullName, String phone, Integer createdByAdminId, Map<String, Object> extra) {
        String sql = "INSERT INTO users (email, password_hash, salt, full_name, phone, role, avatar_url, is_active, auth_provider, created_by) VALUES (?, ?, ?, ?, ?, 'PATIENT', ?, 1, 'LOCAL', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email.trim().toLowerCase());
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.setString(4, fullName);
            ps.setString(5, phone != null ? phone : "");
            ps.setString(6, "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150");
            if (createdByAdminId != null) {
                ps.setInt(7, createdByAdminId);
            } else {
                ps.setNull(7, Types.INTEGER);
            }

            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        createSubProfile(conn, id, "PATIENT", fullName, extra);
                        return findById(id);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private User mapUserFromResultSet(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setSalt(rs.getString("salt"));
        u.setFullName(rs.getString("full_name"));
        u.setPhone(rs.getString("phone"));
        u.setRole(rs.getString("role"));
        u.setAvatarUrl(rs.getString("avatar_url"));
        u.setActive(rs.getInt("is_active") == 1);
        try {
            u.setAuthProvider(rs.getString("auth_provider"));
        } catch (SQLException ignored) {}
        try {
            u.setGoogleSubjectId(rs.getString("google_subject_id"));
        } catch (SQLException ignored) {}
        try {
            int cb = rs.getInt("created_by");
            if (!rs.wasNull()) {
                u.setCreatedBy(cb);
            }
        } catch (SQLException ignored) {}
        u.setCreatedAt(rs.getString("created_at"));
        return u;
    }

    private void createSubProfile(Connection conn, int userId, String role, String fullName, Map<String, Object> extra) {
        try {
            if ("PATIENT".equals(role)) {
                String bloodGroup = extra != null && extra.get("bloodGroup") != null ? (String) extra.get("bloodGroup") : "O+";
                String address = extra != null && extra.get("address") != null ? (String) extra.get("address") : "Bengaluru, India";
                String dob = extra != null && extra.get("dob") != null ? (String) extra.get("dob") : "1995-01-01";
                String gender = extra != null && extra.get("gender") != null ? (String) extra.get("gender") : "Other";

                String pSql = "INSERT INTO patients (user_id, dob, gender, blood_group, address) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(pSql)) {
                    ps.setInt(1, userId);
                    ps.setString(2, dob);
                    ps.setString(3, gender);
                    ps.setString(4, bloodGroup);
                    ps.setString(5, address);
                    ps.executeUpdate();
                }
            } else if ("DOCTOR".equals(role)) {
                String spec = extra != null && extra.get("specialization") != null ? (String) extra.get("specialization") : "General Physician";
                String lic = extra != null && extra.get("licenseNumber") != null ? (String) extra.get("licenseNumber") : ("KMC-" + ((int)(Math.random() * 90000 + 10000)));
                String hosp = extra != null && extra.get("hospitalAffiliation") != null ? (String) extra.get("hospitalAffiliation") : "MediCareX Health Network";

                String dSql = "INSERT INTO doctors (user_id, specialization, license_number, hospital_affiliation) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(dSql)) {
                    ps.setInt(1, userId);
                    ps.setString(2, spec);
                    ps.setString(3, lic);
                    ps.setString(4, hosp);
                    ps.executeUpdate();
                }
            } else if ("PHARMACY".equals(role)) {
                String pharmName = extra != null && extra.get("pharmacyName") != null ? (String) extra.get("pharmacyName") : (fullName + " Pharmacy");
                String owner = extra != null && extra.get("ownerName") != null ? (String) extra.get("ownerName") : fullName;
                String lic = extra != null && extra.get("licenseNumber") != null ? (String) extra.get("licenseNumber") : ("PHARM-" + ((int)(Math.random() * 90000 + 10000)));
                String addr = extra != null && extra.get("address") != null ? (String) extra.get("address") : "MG Road";
                String city = extra != null && extra.get("city") != null ? (String) extra.get("city") : "Bengaluru";
                String state = extra != null && extra.get("state") != null ? (String) extra.get("state") : "Karnataka";
                String postal = extra != null && extra.get("postalCode") != null ? (String) extra.get("postalCode") : "560001";
                String phone = extra != null && extra.get("phone") != null ? (String) extra.get("phone") : "+91 80000 00000";

                String phSql = "INSERT INTO pharmacies (user_id, pharmacy_name, owner_name, license_number, address, city, state, postal_code, latitude, longitude, phone) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 12.9716, 77.5946, ?)";
                try (PreparedStatement ps = conn.prepareStatement(phSql)) {
                    ps.setInt(1, userId);
                    ps.setString(2, pharmName);
                    ps.setString(3, owner);
                    ps.setString(4, lic);
                    ps.setString(5, addr);
                    ps.setString(6, city);
                    ps.setString(7, state);
                    ps.setString(8, postal);
                    ps.setString(9, phone);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean createPasswordReset(int userId, String tokenHash, long expiresAt) {
        String sql = "INSERT INTO password_resets (user_id, token_hash, expires_at, used) VALUES (?, ?, ?, 0)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, tokenHash);
            ps.setLong(3, expiresAt);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public Map<String, Object> findValidPasswordReset(String tokenHash) {
        String sql = "SELECT id, user_id, expires_at, used FROM password_resets WHERE token_hash = ? AND used = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long expiresAt = rs.getLong("expires_at");
                    if (expiresAt > System.currentTimeMillis()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", rs.getInt("id"));
                        map.put("userId", rs.getInt("user_id"));
                        map.put("expiresAt", expiresAt);
                        return map;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean updatePasswordAndInvalidateReset(int userId, int resetId, String newPasswordHash, String newSalt) {
        String uSql = "UPDATE users SET password_hash = ?, salt = ? WHERE id = ?";
        String rSql = "UPDATE password_resets SET used = 1 WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ups = conn.prepareStatement(uSql);
                 PreparedStatement rps = conn.prepareStatement(rSql)) {
                ups.setString(1, newPasswordHash);
                ups.setString(2, newSalt);
                ups.setInt(3, userId);
                ups.executeUpdate();

                rps.setInt(1, resetId);
                rps.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean createEmailVerification(int userId, String tokenHash, long expiresAt) {
        String sql = "INSERT INTO email_verifications (user_id, token_hash, expires_at, verified) VALUES (?, ?, ?, 0)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, tokenHash);
            ps.setLong(3, expiresAt);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean verifyEmailToken(String tokenHash) {
        String sSql = "SELECT id, user_id, expires_at FROM email_verifications WHERE token_hash = ? AND verified = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sSql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String uSql = "UPDATE email_verifications SET verified = 1 WHERE id = ?";
                    try (PreparedStatement ups = conn.prepareStatement(uSql)) {
                        ups.setInt(1, id);
                        return ups.executeUpdate() > 0;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT id, email, password_hash, salt, full_name, phone, role, avatar_url, is_active, auth_provider, google_subject_id, created_by, created_at FROM users ORDER BY id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapUserFromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean update(User u) {
        String sql = "UPDATE users SET full_name = ?, phone = ?, avatar_url = ?, is_active = ?, password_hash = ?, salt = ?, auth_provider = ?, google_subject_id = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u.getFullName());
            ps.setString(2, u.getPhone());
            ps.setString(3, u.getAvatarUrl());
            ps.setInt(4, u.isActive() ? 1 : 0);
            ps.setString(5, u.getPasswordHash());
            ps.setString(6, u.getSalt());
            ps.setString(7, u.getAuthProvider() != null ? u.getAuthProvider() : "LOCAL");
            ps.setString(8, u.getGoogleSubjectId());
            ps.setInt(9, u.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserRepository] update error: " + e.getMessage());
        }
        return false;
    }

    public boolean updateStatus(int userId, boolean isActive) {
        String sql = "UPDATE users SET is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, isActive ? 1 : 0);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserRepository] updateStatus error: " + e.getMessage());
        }
        return false;
    }

    public int count() {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
