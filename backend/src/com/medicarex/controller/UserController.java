package com.medicarex.controller;

import com.medicarex.auth.PasswordHasher;
import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.Doctor;
import com.medicarex.model.Patient;
import com.medicarex.model.Pharmacy;
import com.medicarex.model.User;
import com.medicarex.repository.DoctorRepository;
import com.medicarex.repository.PatientRepository;
import com.medicarex.repository.PharmacyRepository;
import com.medicarex.repository.UserRepository;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class UserController implements HttpHandler {
    private final UserRepository userRepo = new UserRepository();
    private final PatientRepository patientRepo = new PatientRepository();
    private final DoctorRepository doctorRepo = new DoctorRepository();
    private final PharmacyRepository pharmacyRepo = new PharmacyRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (HttpUtil.handleOptions(exchange)) return;

            Map<String, Object> claims = HttpUtil.authenticate(exchange);
            if (claims == null) {
                HttpUtil.sendError(exchange, 401, "Authentication required.");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/api/user/profile") && "GET".equalsIgnoreCase(method)) {
                handleGetProfile(exchange, claims);
            } else if (path.equals("/api/user/profile") && "POST".equalsIgnoreCase(method)) {
                handleUpdateProfile(exchange, claims);
            } else if (path.equals("/api/user/password") && "POST".equalsIgnoreCase(method)) {
                handleChangePassword(exchange, claims);
            } else if (path.equals("/api/user/settings") && "GET".equalsIgnoreCase(method)) {
                handleGetSettings(exchange, claims);
            } else if (path.equals("/api/user/settings") && "POST".equalsIgnoreCase(method)) {
                handleUpdateSettings(exchange, claims);
            } else {
                HttpUtil.sendError(exchange, 404, "Endpoint not found");
            }
        } catch (Throwable t) {
            System.err.println("[UserController] Unhandled error: " + t.getMessage());
            t.printStackTrace();
            HttpUtil.sendError(exchange, 500, "Internal server error: " + t.getMessage());
        }
    }

    private void handleGetProfile(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        Number userIdNum = (Number) claims.get("userId");
        int userId = userIdNum.intValue();

        User user = userRepo.findById(userId);
        if (user == null) {
            HttpUtil.sendError(exchange, 404, "User not found");
            return;
        }

        Map<String, Object> res = user.toSafeMap();

        if ("PATIENT".equalsIgnoreCase(user.getRole())) {
            Patient p = patientRepo.findByUserId(userId);
            if (p != null) {
                res.put("dob", p.getDob());
                res.put("gender", p.getGender());
                res.put("bloodGroup", p.getBloodGroup());
                res.put("address", p.getAddress());
                res.put("emergencyContact", p.getEmergencyContact());
                res.put("insuranceProvider", p.getInsuranceProvider());
            }
        } else if ("DOCTOR".equalsIgnoreCase(user.getRole())) {
            Doctor d = doctorRepo.findByUserId(userId);
            if (d != null) {
                res.put("specialization", d.getSpecialization());
                res.put("licenseNumber", d.getLicenseNumber());
                res.put("hospitalAffiliation", d.getHospitalAffiliation());
                res.put("experienceYears", d.getExperienceYears());
                res.put("consultationFee", d.getConsultationFee());
                res.put("availableHours", d.getAvailableHours());
                res.put("rating", d.getRating());
            }
        } else if ("PHARMACY".equalsIgnoreCase(user.getRole())) {
            Pharmacy ph = pharmacyRepo.findByUserId(userId);
            if (ph != null) {
                res.put("pharmacyName", ph.getPharmacyName());
                res.put("licenseNumber", ph.getLicenseNumber());
                res.put("ownerName", ph.getOwnerName());
                res.put("address", ph.getAddress());
                res.put("city", ph.getCity());
                res.put("state", ph.getState());
                res.put("openingHours", ph.getOpeningHours());
                res.put("is24Hours", ph.is24Hours());
            }
        }

        HttpUtil.sendSuccess(exchange, "Profile loaded", res);
    }

    private void handleUpdateProfile(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        Number userIdNum = (Number) claims.get("userId");
        int userId = userIdNum.intValue();

        User user = userRepo.findById(userId);
        if (user == null) {
            HttpUtil.sendError(exchange, 404, "User not found");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String fullName = (String) req.get("fullName");
        String phone = (String) req.get("phone");
        String avatarUrl = (String) req.get("avatarUrl");

        if (fullName != null && !fullName.trim().isEmpty()) user.setFullName(fullName);
        if (phone != null) user.setPhone(phone);
        if (avatarUrl != null) user.setAvatarUrl(avatarUrl);

        userRepo.update(user);

        // Role-specific profile updates
        if ("PATIENT".equalsIgnoreCase(user.getRole())) {
            Patient p = patientRepo.findByUserId(userId);
            if (p != null) {
                if (req.containsKey("dob")) p.setDob((String) req.get("dob"));
                if (req.containsKey("gender")) p.setGender((String) req.get("gender"));
                if (req.containsKey("bloodGroup")) p.setBloodGroup((String) req.get("bloodGroup"));
                if (req.containsKey("address")) p.setAddress((String) req.get("address"));
                if (req.containsKey("emergencyContact")) p.setEmergencyContact((String) req.get("emergencyContact"));
                if (req.containsKey("insuranceProvider")) p.setInsuranceProvider((String) req.get("insuranceProvider"));
                patientRepo.update(p);
            }
        } else if ("DOCTOR".equalsIgnoreCase(user.getRole())) {
            Doctor d = doctorRepo.findByUserId(userId);
            if (d != null) {
                if (req.containsKey("specialization")) d.setSpecialization((String) req.get("specialization"));
                if (req.containsKey("licenseNumber")) d.setLicenseNumber((String) req.get("licenseNumber"));
                if (req.containsKey("hospitalAffiliation")) d.setHospitalAffiliation((String) req.get("hospitalAffiliation"));
                if (req.containsKey("consultationFee")) d.setConsultationFee(((Number) req.get("consultationFee")).doubleValue());
                if (req.containsKey("availableHours")) d.setAvailableHours((String) req.get("availableHours"));
                doctorRepo.update(d);
            }
        } else if ("PHARMACY".equalsIgnoreCase(user.getRole())) {
            Pharmacy ph = pharmacyRepo.findByUserId(userId);
            if (ph != null) {
                if (req.containsKey("pharmacyName")) ph.setPharmacyName((String) req.get("pharmacyName"));
                if (req.containsKey("ownerName")) ph.setOwnerName((String) req.get("ownerName"));
                if (req.containsKey("address")) ph.setAddress((String) req.get("address"));
                if (req.containsKey("city")) ph.setCity((String) req.get("city"));
                if (req.containsKey("openingHours")) ph.setOpeningHours((String) req.get("openingHours"));
                if (req.containsKey("is24Hours")) ph.set24Hours(Boolean.TRUE.equals(req.get("is24Hours")));
                pharmacyRepo.update(ph);
            }
        }

        HttpUtil.sendSuccess(exchange, "Profile updated successfully", user.toSafeMap());
    }

    private void handleChangePassword(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        Number userIdNum = (Number) claims.get("userId");
        int userId = userIdNum.intValue();

        User user = userRepo.findById(userId);
        if (user == null) {
            HttpUtil.sendError(exchange, 404, "User not found");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String currentPassword = (String) req.get("currentPassword");
        String newPassword = (String) req.get("newPassword");

        if (currentPassword == null || newPassword == null || currentPassword.isEmpty() || newPassword.isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Current and new password are required.");
            return;
        }

        if (!PasswordHasher.verifyPassword(currentPassword, user.getSalt(), user.getPasswordHash())) {
            HttpUtil.sendError(exchange, 400, "Incorrect current password.");
            return;
        }

        if (newPassword.length() < 6) {
            HttpUtil.sendError(exchange, 400, "New password must be at least 6 characters.");
            return;
        }

        String newSalt = PasswordHasher.generateSalt();
        String newHash = PasswordHasher.hashPassword(newPassword, newSalt);

        user.setPasswordHash(newHash);
        user.setSalt(newSalt);
        boolean ok = userRepo.update(user);

        if (ok) {
            HttpUtil.sendSuccess(exchange, "Password changed successfully", null);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to update password");
        }
    }

    private void handleGetSettings(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        Number userIdNum = (Number) claims.get("userId");
        int userId = userIdNum.intValue();

        Map<String, Object> settings = new HashMap<>();
        settings.put("userId", userId);
        settings.put("emailNotifications", true);
        settings.put("smsNotifications", true);
        settings.put("stockAlerts", true);
        settings.put("twoFactorAuth", false);

        String sql = "SELECT email_notifications, sms_notifications, stock_alerts, two_factor_auth FROM user_settings WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    settings.put("emailNotifications", rs.getInt("email_notifications") == 1);
                    settings.put("smsNotifications", rs.getInt("sms_notifications") == 1);
                    settings.put("stockAlerts", rs.getInt("stock_alerts") == 1);
                    settings.put("twoFactorAuth", rs.getInt("two_factor_auth") == 1);
                }
            }
        } catch (SQLException ignored) {}

        HttpUtil.sendSuccess(exchange, "User settings loaded", settings);
    }

    private void handleUpdateSettings(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        Number userIdNum = (Number) claims.get("userId");
        int userId = userIdNum.intValue();

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        int emailNotifs = Boolean.TRUE.equals(req.get("emailNotifications")) ? 1 : 0;
        int smsNotifs = Boolean.TRUE.equals(req.get("smsNotifications")) ? 1 : 0;
        int stockAlerts = Boolean.TRUE.equals(req.get("stockAlerts")) ? 1 : 0;
        int twoFactor = Boolean.TRUE.equals(req.get("twoFactorAuth")) ? 1 : 0;

        String upsert = "INSERT OR REPLACE INTO user_settings (user_id, email_notifications, sms_notifications, stock_alerts, two_factor_auth) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsert)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, emailNotifs);
            stmt.setInt(3, smsNotifs);
            stmt.setInt(4, stockAlerts);
            stmt.setInt(5, twoFactor);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[UserController] settings update error: " + e.getMessage());
        }

        HttpUtil.sendSuccess(exchange, "Settings updated successfully", req);
    }
}
