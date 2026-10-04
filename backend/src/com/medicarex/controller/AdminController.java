package com.medicarex.controller;

import com.medicarex.auth.PasswordHasher;
import com.medicarex.model.AccountApplication;
import com.medicarex.model.Hospital;
import com.medicarex.model.Medicine;
import com.medicarex.model.Pharmacy;
import com.medicarex.model.User;
import com.medicarex.repository.*;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.*;

public class AdminController implements HttpHandler {
    private final UserRepository userRepo = new UserRepository();
    private final MedicineRepository medicineRepo = new MedicineRepository();
    private final PharmacyRepository pharmacyRepo = new PharmacyRepository();
    private final PrescriptionRepository rxRepo = new PrescriptionRepository();
    private final ApplicationRepository appRepo = new ApplicationRepository();
    private final HospitalRepository hospitalRepo = new HospitalRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (HttpUtil.handleOptions(exchange)) return;

            Map<String, Object> claims = HttpUtil.authenticate(exchange);
            if (claims == null) {
                HttpUtil.sendError(exchange, 401, "Authentication required for admin access.");
                return;
            }

            Number userIdNum = (Number) claims.get("userId");
            if (userIdNum == null) {
                HttpUtil.sendError(exchange, 401, "Invalid token payload.");
                return;
            }

            User currentAdmin = userRepo.findById(userIdNum.intValue());
            if (currentAdmin == null) {
                HttpUtil.sendError(exchange, 401, "Administrator account not found.");
                return;
            }

            if (!"ADMIN".equalsIgnoreCase(currentAdmin.getRole())) {
                HttpUtil.sendError(exchange, 403, "Forbidden: Admin privileges required.");
                return;
            }

            if (!currentAdmin.isActive()) {
                HttpUtil.sendError(exchange, 403, "Forbidden: Administrator account has been deactivated.");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/api/admin/stats") && "GET".equalsIgnoreCase(method)) {
                handleStats(exchange);
            } else if (path.equals("/api/admin/users") && "GET".equalsIgnoreCase(method)) {
                handleUsers(exchange);
            } else if ((path.equals("/api/admin/users") || path.equals("/api/admin/users/create")) && "POST".equalsIgnoreCase(method)) {
                handleCreateAccount(exchange, currentAdmin);
            } else if (path.equals("/api/admin/users/status") && "POST".equalsIgnoreCase(method)) {
                handleToggleUserStatus(exchange, currentAdmin);
            } 
            // Doctor & Pharmacy Approval routes
            else if ((path.equals("/api/admin/approvals/doctors") || path.equals("/api/admin/approvals/doctor")) && "GET".equalsIgnoreCase(method)) {
                handleListApplications(exchange, "DOCTOR");
            } else if ((path.equals("/api/admin/approvals/pharmacies") || path.equals("/api/admin/approvals/pharmacy")) && "GET".equalsIgnoreCase(method)) {
                handleListApplications(exchange, "PHARMACY");
            } else if (path.matches("^/api/admin/approvals/(?:doctor/)?(\\d+)/approve$") && "POST".equalsIgnoreCase(method)) {
                String idStr = path.replaceAll("^/api/admin/approvals/(?:doctor/)?(\\d+)/approve$", "$1");
                handleApproveApplication(exchange, Integer.parseInt(idStr), currentAdmin);
            } else if (path.matches("^/api/admin/approvals/(?:doctor/)?(\\d+)/reject$") && "POST".equalsIgnoreCase(method)) {
                String idStr = path.replaceAll("^/api/admin/approvals/(?:doctor/)?(\\d+)/reject$", "$1");
                handleRejectApplication(exchange, Integer.parseInt(idStr), currentAdmin);
            } else if (path.matches("^/api/admin/approvals/pharmacy/(\\d+)/approve$") && "POST".equalsIgnoreCase(method)) {
                String idStr = path.replaceAll("^/api/admin/approvals/pharmacy/(\\d+)/approve$", "$1");
                handleApproveApplication(exchange, Integer.parseInt(idStr), currentAdmin);
            } else if (path.matches("^/api/admin/approvals/pharmacy/(\\d+)/reject$") && "POST".equalsIgnoreCase(method)) {
                String idStr = path.replaceAll("^/api/admin/approvals/pharmacy/(\\d+)/reject$", "$1");
                handleRejectApplication(exchange, Integer.parseInt(idStr), currentAdmin);
            }
            // Medicine CRUD
            else if (path.equals("/api/admin/medicines/create") && "POST".equalsIgnoreCase(method)) {
                handleCreateMedicine(exchange);
            } else if (path.equals("/api/admin/medicines/update") && "POST".equalsIgnoreCase(method)) {
                handleUpdateMedicine(exchange);
            } else if (path.startsWith("/api/admin/medicines/") && "DELETE".equalsIgnoreCase(method)) {
                String idStr = path.substring("/api/admin/medicines/".length());
                handleDeleteMedicine(exchange, Integer.parseInt(idStr));
            } 
            // Pharmacy CRUD
            else if (path.equals("/api/admin/pharmacies/create") && "POST".equalsIgnoreCase(method)) {
                handleCreatePharmacy(exchange);
            } else if (path.equals("/api/admin/pharmacies/update") && "POST".equalsIgnoreCase(method)) {
                handleUpdatePharmacy(exchange);
            } else if (path.startsWith("/api/admin/pharmacies/") && "DELETE".equalsIgnoreCase(method)) {
                String idStr = path.substring("/api/admin/pharmacies/".length());
                handleDeletePharmacy(exchange, Integer.parseInt(idStr));
            }
            // Hospital Management CRUD
            else if (path.equals("/api/admin/hospitals/stats") && "GET".equalsIgnoreCase(method)) {
                handleHospitalStats(exchange);
            } else if (path.equals("/api/admin/hospitals") && "GET".equalsIgnoreCase(method)) {
                handleListHospitals(exchange);
            } else if ((path.equals("/api/admin/hospitals") || path.equals("/api/admin/hospitals/create")) && "POST".equalsIgnoreCase(method)) {
                handleCreateHospital(exchange, currentAdmin);
            } else if (path.equals("/api/admin/hospitals/update") && "POST".equalsIgnoreCase(method)) {
                handleUpdateHospital(exchange);
            } else if (path.equals("/api/admin/hospitals/status") && "POST".equalsIgnoreCase(method)) {
                handleToggleHospitalStatus(exchange);
            } else if (path.matches("^/api/admin/hospitals/\\d+/status$") && ("PATCH".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method))) {
                String idStr = path.replaceAll("^/api/admin/hospitals/(\\d+)/status$", "$1");
                handleToggleHospitalStatus(exchange, Integer.parseInt(idStr));
            } else if (path.matches("^/api/admin/hospitals/\\d+/approve$") && "POST".equalsIgnoreCase(method)) {
                String idStr = path.replaceAll("^/api/admin/hospitals/(\\d+)/approve$", "$1");
                handleApproveHospital(exchange, Integer.parseInt(idStr));
            } else if (path.matches("^/api/admin/hospitals/\\d+/reject$") && "POST".equalsIgnoreCase(method)) {
                String idStr = path.replaceAll("^/api/admin/hospitals/(\\d+)/reject$", "$1");
                handleRejectHospital(exchange, Integer.parseInt(idStr));
            } else if (path.matches("^/api/admin/hospitals/\\d+$")) {
                int hospId = Integer.parseInt(path.substring("/api/admin/hospitals/".length()));
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetHospitalById(exchange, hospId);
                } else if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
                    handleUpdateHospital(exchange, hospId);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    handleDeleteHospital(exchange, hospId);
                } else {
                    HttpUtil.sendError(exchange, 405, "Method Not Allowed");
                }
            } else {
                HttpUtil.sendError(exchange, 404, "Endpoint not found");
            }
        } catch (Throwable t) {
            System.err.println("[AdminController] Unhandled error: " + t.getMessage());
            t.printStackTrace();
            HttpUtil.sendError(exchange, 500, "Internal server error: " + t.getMessage());
        }
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepo.count());
        stats.put("totalMedicines", medicineRepo.count());
        stats.put("totalPharmacies", pharmacyRepo.count());
        stats.put("totalPrescriptions", rxRepo.count());
        stats.put("pendingDoctorApprovals", appRepo.countPending("DOCTOR"));
        stats.put("pendingPharmacyApprovals", appRepo.countPending("PHARMACY"));

        List<Map<String, Object>> userGrowth = new ArrayList<>();
        userGrowth.add(Map.of("month", "Apr", "count", 45));
        userGrowth.add(Map.of("month", "May", "count", 78));
        userGrowth.add(Map.of("month", "Jun", "count", 112));
        userGrowth.add(Map.of("month", "Jul", "count", 160));
        userGrowth.add(Map.of("month", "Aug", "count", 210));
        userGrowth.add(Map.of("month", "Sep", "count", 280));
        stats.put("userGrowth", userGrowth);

        Map<String, Object> availabilityBreakdown = new HashMap<>();
        availabilityBreakdown.put("inStock", 78);
        availabilityBreakdown.put("lowStock", 14);
        availabilityBreakdown.put("outOfStock", 8);
        stats.put("availabilityBreakdown", availabilityBreakdown);

        HttpUtil.sendSuccess(exchange, "Admin analytics stats", stats);
    }

    private void handleUsers(HttpExchange exchange) throws IOException {
        List<User> users = userRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            result.add(u.toSafeMap());
        }
        HttpUtil.sendSuccess(exchange, "Users list", result);
    }

    private void handleCreateAccount(HttpExchange exchange, User currentAdmin) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String role = (String) req.get("role");
        if (role == null || role.trim().isEmpty()) {
            role = "ADMIN";
        }
        String normalizedRole = role.trim().toUpperCase();

        // STRICT REQUIREMENT: Reject DOCTOR and PHARMACY role creation from Admin Create Account page
        if ("DOCTOR".equals(normalizedRole) || "PHARMACY".equals(normalizedRole)) {
            HttpUtil.sendError(exchange, 400, "Doctor and Pharmacy accounts must be created through the approval workflow.");
            return;
        }

        if (!"PATIENT".equals(normalizedRole) && !"ADMIN".equals(normalizedRole)) {
            HttpUtil.sendError(exchange, 400, "Invalid role. Admin can only create Patient or Administrator accounts.");
            return;
        }

        String fullName = (String) req.get("fullName");
        String email = (String) req.get("email");
        String phone = (String) req.get("phone");
        String password = (String) req.get("password");
        String confirmPassword = (String) req.get("confirmPassword");

        if (fullName == null || fullName.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Full name is required.");
            return;
        }

        if (email == null || email.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Email address is required.");
            return;
        }

        String trimmedEmail = email.trim().toLowerCase();
        if (!trimmedEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            HttpUtil.sendError(exchange, 400, "Please enter a valid email address.");
            return;
        }

        if (password == null || password.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Password is required.");
            return;
        }

        if (password.length() < 6) {
            HttpUtil.sendError(exchange, 400, "Password must be at least 6 characters long.");
            return;
        }

        if (confirmPassword != null && !confirmPassword.trim().isEmpty() && !password.equals(confirmPassword)) {
            HttpUtil.sendError(exchange, 400, "Passwords do not match.");
            return;
        }

        if (userRepo.findByEmail(trimmedEmail) != null) {
            HttpUtil.sendError(exchange, 400, "An account with this email address already exists.");
            return;
        }

        String salt = PasswordHasher.generateSalt();
        String passwordHash = PasswordHasher.hashPassword(password, salt);

        User newUser;
        if ("ADMIN".equals(normalizedRole)) {
            newUser = userRepo.createAdminUser(trimmedEmail, passwordHash, salt, fullName.trim(), phone != null ? phone.trim() : "", currentAdmin.getId());
        } else {
            newUser = userRepo.createPatientUser(trimmedEmail, passwordHash, salt, fullName.trim(), phone != null ? phone.trim() : "", currentAdmin.getId(), req);
        }

        if (newUser == null) {
            HttpUtil.sendError(exchange, 500, "Failed to create " + normalizedRole.toLowerCase() + " account in database.");
            return;
        }

        Map<String, Object> respData = new HashMap<>();
        respData.put("user", newUser.toSafeMap());
        HttpUtil.sendSuccess(exchange, ("ADMIN".equals(normalizedRole) ? "Administrator" : "Patient") + " account created successfully", respData);
    }

    private void handleToggleUserStatus(HttpExchange exchange, User currentAdmin) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number userIdNum = (Number) req.get("userId");
        Boolean isActive = (Boolean) req.get("isActive");
        if (isActive == null && req.get("status") != null) {
            String s = String.valueOf(req.get("status"));
            isActive = "ACTIVE".equalsIgnoreCase(s) || "1".equals(s) || "true".equalsIgnoreCase(s);
        }

        if (userIdNum == null || isActive == null) {
            HttpUtil.sendError(exchange, 400, "userId and isActive/status are required");
            return;
        }

        User u = userRepo.findById(userIdNum.intValue());
        if (u == null) {
            HttpUtil.sendError(exchange, 404, "User not found");
            return;
        }

        // Safety: Do not allow deactivating one's own account or the only active admin
        if ("ADMIN".equalsIgnoreCase(u.getRole()) && !isActive) {
            if (u.getId() == currentAdmin.getId()) {
                HttpUtil.sendError(exchange, 400, "Cannot deactivate your own administrator account.");
                return;
            }
            int adminCount = 0;
            for (User user : userRepo.findAll()) {
                if ("ADMIN".equalsIgnoreCase(user.getRole()) && user.isActive()) adminCount++;
            }
            if (adminCount <= 1) {
                HttpUtil.sendError(exchange, 400, "Cannot deactivate the sole active system administrator account.");
                return;
            }
        }

        u.setActive(isActive);
        userRepo.updateStatus(u.getId(), isActive);
        HttpUtil.sendSuccess(exchange, "User status updated to " + (isActive ? "Active" : "Inactive"), u.toSafeMap());
    }

    private void handleListApplications(HttpExchange exchange, String role) throws IOException {
        Map<String, String> qParams = HttpUtil.getQueryParams(exchange);
        String status = qParams.get("status");
        String query = qParams.get("query");

        List<AccountApplication> apps = appRepo.findByRole(role, status, query);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AccountApplication app : apps) {
            result.add(app.toMap());
        }
        HttpUtil.sendSuccess(exchange, role.toLowerCase() + " applications list", result);
    }

    private void handleApproveApplication(HttpExchange exchange, int applicationId, User currentAdmin) throws IOException {
        AccountApplication app = appRepo.findById(applicationId);
        if (app == null) {
            HttpUtil.sendError(exchange, 404, "Application record not found.");
            return;
        }

        if (!"PENDING".equalsIgnoreCase(app.getStatus())) {
            HttpUtil.sendError(exchange, 400, "Application has already been processed (Current status: " + app.getStatus() + ").");
            return;
        }

        boolean ok = appRepo.approveApplication(applicationId, currentAdmin.getId());
        if (ok) {
            AccountApplication updated = appRepo.findById(applicationId);
            HttpUtil.sendSuccess(exchange, app.getRole() + " application approved successfully. Account is now active.", updated != null ? updated.toMap() : null);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to approve application. Please try again.");
        }
    }

    private void handleRejectApplication(HttpExchange exchange, int applicationId, User currentAdmin) throws IOException {
        AccountApplication app = appRepo.findById(applicationId);
        if (app == null) {
            HttpUtil.sendError(exchange, 404, "Application record not found.");
            return;
        }

        if (!"PENDING".equalsIgnoreCase(app.getStatus())) {
            HttpUtil.sendError(exchange, 400, "Application has already been processed (Current status: " + app.getStatus() + ").");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        String reason = req != null ? (String) req.get("reason") : null;

        boolean ok = appRepo.rejectApplication(applicationId, reason, currentAdmin.getId());
        if (ok) {
            AccountApplication updated = appRepo.findById(applicationId);
            HttpUtil.sendSuccess(exchange, app.getRole() + " application has been rejected.", updated != null ? updated.toMap() : null);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to reject application.");
        }
    }

    private void handleCreateMedicine(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Medicine m = new Medicine();
        m.setName((String) req.get("name"));
        m.setGenericName((String) req.get("genericName"));
        m.setBrand((String) req.get("brand"));
        m.setCategory((String) req.get("category"));
        m.setDosage((String) req.get("dosage"));
        m.setForm((String) req.get("form"));
        Object rxReq = req.get("prescriptionRequired");
        m.setPrescriptionRequired(Boolean.TRUE.equals(rxReq));
        m.setDescription((String) req.get("description"));
        m.setUses((String) req.get("uses"));
        m.setSideEffects((String) req.get("sideEffects"));
        m.setImportantInfo((String) req.get("importantInfo"));
        m.setStorageInfo((String) req.get("storageInfo"));
        m.setImageUrl((String) req.get("imageUrl"));
        Number priceNum = (Number) req.get("basePrice");
        m.setBasePrice(priceNum != null ? priceNum.doubleValue() : 50.0);
        m.setActive(true);

        Medicine created = medicineRepo.create(m);
        if (created != null) {
            HttpUtil.sendSuccess(exchange, "Medicine created successfully", created.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to create medicine");
        }
    }

    private void handleUpdateMedicine(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number idNum = (Number) req.get("id");
        if (idNum == null) {
            HttpUtil.sendError(exchange, 400, "id is required");
            return;
        }

        Medicine m = medicineRepo.findById(idNum.intValue());
        if (m == null) {
            HttpUtil.sendError(exchange, 404, "Medicine not found");
            return;
        }

        if (req.containsKey("name")) m.setName((String) req.get("name"));
        if (req.containsKey("genericName")) m.setGenericName((String) req.get("genericName"));
        if (req.containsKey("brand")) m.setBrand((String) req.get("brand"));
        if (req.containsKey("category")) m.setCategory((String) req.get("category"));
        if (req.containsKey("dosage")) m.setDosage((String) req.get("dosage"));
        if (req.containsKey("form")) m.setForm((String) req.get("form"));
        if (req.containsKey("prescriptionRequired")) m.setPrescriptionRequired(Boolean.TRUE.equals(req.get("prescriptionRequired")));
        if (req.containsKey("description")) m.setDescription((String) req.get("description"));
        if (req.containsKey("uses")) m.setUses((String) req.get("uses"));
        if (req.containsKey("sideEffects")) m.setSideEffects((String) req.get("sideEffects"));
        if (req.containsKey("importantInfo")) m.setImportantInfo((String) req.get("importantInfo"));
        if (req.containsKey("storageInfo")) m.setStorageInfo((String) req.get("storageInfo"));
        if (req.containsKey("imageUrl")) m.setImageUrl((String) req.get("imageUrl"));
        if (req.containsKey("basePrice")) m.setBasePrice(((Number) req.get("basePrice")).doubleValue());
        if (req.containsKey("isActive")) m.setActive(Boolean.TRUE.equals(req.get("isActive")));

        boolean ok = medicineRepo.update(m);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Medicine updated successfully", m.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to update medicine");
        }
    }

    private void handleDeleteMedicine(HttpExchange exchange, int id) throws IOException {
        boolean ok = medicineRepo.delete(id);
        HttpUtil.sendSuccess(exchange, ok ? "Medicine deleted successfully" : "Failed to delete medicine", null);
    }

    private void handleCreatePharmacy(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Pharmacy p = new Pharmacy();
        p.setPharmacyName((String) req.get("pharmacyName"));
        p.setLicenseNumber((String) req.get("licenseNumber"));
        p.setOwnerName((String) req.get("ownerName"));
        p.setAddress((String) req.get("address"));
        p.setCity((String) req.get("city"));
        p.setState((String) req.get("state"));
        p.setPostalCode((String) req.get("postalCode"));
        Number latNum = (Number) req.get("latitude");
        Number lngNum = (Number) req.get("longitude");
        p.setLatitude(latNum != null ? latNum.doubleValue() : 12.9716);
        p.setLongitude(lngNum != null ? lngNum.doubleValue() : 77.5946);
        p.setPhone((String) req.get("phone"));
        p.setEmail((String) req.get("email"));
        p.setOpeningHours((String) req.get("openingHours"));
        p.set24Hours(Boolean.TRUE.equals(req.get("is24Hours")));
        p.setStatus((String) req.getOrDefault("status", "Active"));

        Pharmacy created = pharmacyRepo.create(p);
        if (created != null) {
            HttpUtil.sendSuccess(exchange, "Pharmacy created successfully", created.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to create pharmacy");
        }
    }

    private void handleUpdatePharmacy(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number idNum = (Number) req.get("id");
        if (idNum == null) {
            HttpUtil.sendError(exchange, 400, "id is required");
            return;
        }

        Pharmacy p = pharmacyRepo.findById(idNum.intValue());
        if (p == null) {
            HttpUtil.sendError(exchange, 404, "Pharmacy not found");
            return;
        }

        if (req.containsKey("pharmacyName")) p.setPharmacyName((String) req.get("pharmacyName"));
        if (req.containsKey("ownerName")) p.setOwnerName((String) req.get("ownerName"));
        if (req.containsKey("licenseNumber")) p.setLicenseNumber((String) req.get("licenseNumber"));
        if (req.containsKey("address")) p.setAddress((String) req.get("address"));
        if (req.containsKey("city")) p.setCity((String) req.get("city"));
        if (req.containsKey("state")) p.setState((String) req.get("state"));
        if (req.containsKey("phone")) p.setPhone((String) req.get("phone"));
        if (req.containsKey("email")) p.setEmail((String) req.get("email"));
        if (req.containsKey("status")) p.setStatus((String) req.get("status"));
        if (req.containsKey("isVerified")) p.setVerified(Boolean.TRUE.equals(req.get("isVerified")));

        boolean ok = pharmacyRepo.update(p);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Pharmacy updated successfully", p.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to update pharmacy");
        }
    }

    private void handleDeletePharmacy(HttpExchange exchange, int id) throws IOException {
        boolean ok = pharmacyRepo.delete(id);
        HttpUtil.sendSuccess(exchange, ok ? "Pharmacy deleted successfully" : "Failed to delete pharmacy", null);
    }

    // ==========================================
    // Hospital Management Handlers
    // ==========================================

    private void handleHospitalStats(HttpExchange exchange) throws IOException {
        Map<String, Object> stats = hospitalRepo.getHospitalStats();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", stats);
        data.putAll(stats);
        HttpUtil.sendSuccess(exchange, "Hospital statistics retrieved", data);
    }

    private void handleListHospitals(HttpExchange exchange) throws IOException {
        Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);

        String search = queryParams.get("search");
        String status = queryParams.get("status");
        Boolean emergencyOnly = "true".equalsIgnoreCase(queryParams.get("emergency")) || "true".equalsIgnoreCase(queryParams.get("emergencyOnly"));
        Boolean acceptingOnly = "true".equalsIgnoreCase(queryParams.get("accepting")) || "true".equalsIgnoreCase(queryParams.get("acceptingOnly"));

        List<Hospital> list = hospitalRepo.getAllHospitals(search, status, emergencyOnly, acceptingOnly);
        List<Map<String, Object>> response = new ArrayList<>();
        for (Hospital h : list) {
            response.add(h.toMap());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hospitals", response);
        data.put("list", response);
        HttpUtil.sendSuccess(exchange, "Hospitals list retrieved", data);
    }

    private void handleGetHospitalById(HttpExchange exchange, int id) throws IOException {
        Hospital h = hospitalRepo.getHospitalById(id);
        if (h == null) {
            HttpUtil.sendError(exchange, 404, "Hospital not found");
            return;
        }
        HttpUtil.sendSuccess(exchange, "Hospital retrieved", h.toMap());
    }

    private void handleCreateHospital(HttpExchange exchange, User currentAdmin) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        if (req == null) {
            HttpUtil.sendError(exchange, 400, "Invalid JSON payload");
            return;
        }

        String name = (String) req.get("name");
        if (name == null || name.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Hospital name is required");
            return;
        }

        String address = (String) req.get("address");
        if (address == null || address.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Address is required");
            return;
        }

        String city = (String) req.get("city");
        if (city == null || city.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "City is required");
            return;
        }

        String state = (String) req.get("state");
        if (state == null || state.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "State is required");
            return;
        }

        String pincode = (String) req.get("pincode");
        if (pincode == null || pincode.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Pincode is required");
            return;
        }

        double lat = 0.0;
        double lng = 0.0;
        try {
            if (req.containsKey("latitude")) lat = ((Number) req.get("latitude")).doubleValue();
            if (req.containsKey("longitude")) lng = ((Number) req.get("longitude")).doubleValue();
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 400, "Latitude and longitude must be valid numbers");
            return;
        }

        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            HttpUtil.sendError(exchange, 400, "Latitude must be between -90 and 90, Longitude must be between -180 and 180");
            return;
        }

        // Duplicate prevention check
        Hospital dup = hospitalRepo.findPotentialDuplicate(name, city, address, lat, lng);
        if (dup != null) {
            HttpUtil.sendError(exchange, 409, "Hospital already exists.");
            return;
        }

        String phone = (String) req.getOrDefault("phone", "");
        String emergencyContact = (String) req.getOrDefault("emergencyContact", phone != null ? phone : "");

        Hospital h = new Hospital();
        h.setName(name.trim());
        h.setRegistrationNumber((String) req.getOrDefault("registrationNumber", "HOSP-" + System.currentTimeMillis() % 100000));
        h.setAddress(address.trim());
        h.setCity(city.trim());
        h.setState(state.trim());
        h.setPincode(pincode.trim());
        h.setLatitude(lat);
        h.setLongitude(lng);
        h.setPhone(phone != null ? phone.trim() : "");
        h.setEmergencyContact(emergencyContact != null ? emergencyContact.trim() : "");
        h.setEmergencyAvailable(req.containsKey("emergencyAvailable") ? Boolean.TRUE.equals(req.get("emergencyAvailable")) : true);
        h.setIcuAvailable(req.containsKey("icuAvailable") ? Boolean.TRUE.equals(req.get("icuAvailable")) : true);
        
        if (req.containsKey("availableBeds")) {
            try { h.setAvailableBeds(((Number) req.get("availableBeds")).intValue()); } catch (Exception ignored) {}
        }
        if (req.containsKey("totalBeds")) {
            try { h.setTotalBeds(((Number) req.get("totalBeds")).intValue()); } catch (Exception ignored) {}
        }
        h.setAcceptingAmbulances(req.containsKey("acceptingAmbulances") ? Boolean.TRUE.equals(req.get("acceptingAmbulances")) : true);

        // Admin-created hospital default status is ACTIVE (unless explicitly set to false)
        boolean active = req.containsKey("isActive") ? Boolean.TRUE.equals(req.get("isActive")) : (req.containsKey("active") ? Boolean.TRUE.equals(req.get("active")) : true);
        h.setActive(active);
        h.setApprovalStatus(active ? "ACTIVE" : "INACTIVE");
        h.setCreatedByRole("ADMIN");
        if (currentAdmin != null) {
            h.setCreatedByUserId(currentAdmin.getId());
        }

        Hospital created = hospitalRepo.createHospital(h);
        if (created != null) {
            HttpUtil.sendSuccess(exchange, "Hospital added successfully.", created.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to create hospital record in database");
        }
    }

    private void handleUpdateHospital(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number idNum = (Number) req.get("id");
        if (idNum == null) {
            HttpUtil.sendError(exchange, 400, "id is required");
            return;
        }
        handleUpdateHospital(exchange, idNum.intValue(), req);
    }

    private void handleUpdateHospital(HttpExchange exchange, int id) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        handleUpdateHospital(exchange, id, req);
    }

    private void handleUpdateHospital(HttpExchange exchange, int id, Map<String, Object> req) throws IOException {
        Hospital h = hospitalRepo.getHospitalById(id);
        if (h == null) {
            HttpUtil.sendError(exchange, 404, "Hospital not found");
            return;
        }

        if (req.containsKey("name")) {
            String name = (String) req.get("name");
            if (name == null || name.trim().isEmpty()) {
                HttpUtil.sendError(exchange, 400, "Hospital name cannot be empty");
                return;
            }
            h.setName(name.trim());
        }
        if (req.containsKey("registrationNumber")) h.setRegistrationNumber((String) req.get("registrationNumber"));
        if (req.containsKey("address")) {
            String address = (String) req.get("address");
            if (address == null || address.trim().isEmpty()) {
                HttpUtil.sendError(exchange, 400, "Address cannot be empty");
                return;
            }
            h.setAddress(address.trim());
        }
        if (req.containsKey("city")) h.setCity((String) req.get("city"));
        if (req.containsKey("state")) h.setState((String) req.get("state"));
        if (req.containsKey("pincode")) h.setPincode((String) req.get("pincode"));
        if (req.containsKey("latitude")) {
            try {
                double lat = ((Number) req.get("latitude")).doubleValue();
                if (lat < -90 || lat > 90) {
                    HttpUtil.sendError(exchange, 400, "Invalid latitude coordinate");
                    return;
                }
                h.setLatitude(lat);
            } catch (Exception ignored) {}
        }
        if (req.containsKey("longitude")) {
            try {
                double lng = ((Number) req.get("longitude")).doubleValue();
                if (lng < -180 || lng > 180) {
                    HttpUtil.sendError(exchange, 400, "Invalid longitude coordinate");
                    return;
                }
                h.setLongitude(lng);
            } catch (Exception ignored) {}
        }
        if (req.containsKey("phone")) h.setPhone((String) req.get("phone"));
        if (req.containsKey("emergencyContact")) h.setEmergencyContact((String) req.get("emergencyContact"));
        if (req.containsKey("emergencyAvailable")) h.setEmergencyAvailable(Boolean.TRUE.equals(req.get("emergencyAvailable")));
        if (req.containsKey("icuAvailable")) h.setIcuAvailable(Boolean.TRUE.equals(req.get("icuAvailable")));
        if (req.containsKey("availableBeds")) {
            try { h.setAvailableBeds(((Number) req.get("availableBeds")).intValue()); } catch (Exception ignored) {}
        }
        if (req.containsKey("totalBeds")) {
            try { h.setTotalBeds(((Number) req.get("totalBeds")).intValue()); } catch (Exception ignored) {}
        }
        if (req.containsKey("acceptingAmbulances")) h.setAcceptingAmbulances(Boolean.TRUE.equals(req.get("acceptingAmbulances")));
        if (req.containsKey("isActive")) h.setActive(Boolean.TRUE.equals(req.get("isActive")));
        if (req.containsKey("active")) h.setActive(Boolean.TRUE.equals(req.get("active")));

        boolean ok = hospitalRepo.updateHospital(h);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Hospital updated successfully", h.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to update hospital in database");
        }
    }

    private void handleToggleHospitalStatus(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        Number idNum = (Number) req.get("id");
        if (idNum == null) {
            HttpUtil.sendError(exchange, 400, "id is required");
            return;
        }

        boolean active = req.containsKey("isActive") ? Boolean.TRUE.equals(req.get("isActive")) : (req.containsKey("active") ? Boolean.TRUE.equals(req.get("active")) : true);
        handleToggleHospitalStatus(exchange, idNum.intValue(), active);
    }

    private void handleToggleHospitalStatus(HttpExchange exchange, int id) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        boolean active = req.containsKey("isActive") ? Boolean.TRUE.equals(req.get("isActive")) : (req.containsKey("active") ? Boolean.TRUE.equals(req.get("active")) : true);
        handleToggleHospitalStatus(exchange, id, active);
    }

    private void handleToggleHospitalStatus(HttpExchange exchange, int id, boolean active) throws IOException {
        Hospital h = hospitalRepo.getHospitalById(id);
        if (h == null) {
            HttpUtil.sendError(exchange, 404, "Hospital not found");
            return;
        }

        boolean ok = hospitalRepo.updateHospitalStatus(id, active);
        if (ok) {
            h.setActive(active);
            HttpUtil.sendSuccess(exchange, "Hospital status updated to " + (active ? "Active" : "Inactive"), h.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to update hospital status");
        }
    }

    private void handleApproveHospital(HttpExchange exchange, int id) throws IOException {
        Hospital h = hospitalRepo.getHospitalById(id);
        if (h == null) {
            HttpUtil.sendError(exchange, 404, "Hospital not found");
            return;
        }

        boolean ok = hospitalRepo.approveHospital(id);
        if (ok) {
            Hospital updated = hospitalRepo.getHospitalById(id);
            HttpUtil.sendSuccess(exchange, "Hospital approved and activated successfully", updated != null ? updated.toMap() : h.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to approve hospital");
        }
    }

    private void handleRejectHospital(HttpExchange exchange, int id) throws IOException {
        Hospital h = hospitalRepo.getHospitalById(id);
        if (h == null) {
            HttpUtil.sendError(exchange, 404, "Hospital not found");
            return;
        }

        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);
        String reason = (String) req.getOrDefault("reason", "Rejected by administrator review.");

        boolean ok = hospitalRepo.rejectHospital(id, reason);
        if (ok) {
            Hospital updated = hospitalRepo.getHospitalById(id);
            HttpUtil.sendSuccess(exchange, "Hospital rejected", updated != null ? updated.toMap() : h.toMap());
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to reject hospital");
        }
    }

    private void handleDeleteHospital(HttpExchange exchange, int id) throws IOException {
        Hospital h = hospitalRepo.getHospitalById(id);
        if (h == null) {
            HttpUtil.sendError(exchange, 404, "Hospital not found");
            return;
        }

        // Active Emergency Protection: Do not allow deletion if currently assigned to active emergency
        if (hospitalRepo.hasActiveEmergencyReference(id)) {
            HttpUtil.sendError(exchange, 409, "Hospital cannot be removed because it is currently assigned to an active emergency. Deactivate it for future dispatch instead.");
            return;
        }

        // If historical emergency references exist, soft-remove to preserve trip history
        if (hospitalRepo.hasHistoricalEmergencyReference(id)) {
            boolean ok = hospitalRepo.softRemoveHospital(id);
            if (ok) {
                HttpUtil.sendSuccess(exchange, "Hospital removed successfully.", null);
            } else {
                HttpUtil.sendError(exchange, 500, "Failed to remove hospital record.");
            }
            return;
        }

        // If no references exist, permanently delete the record
        boolean ok = hospitalRepo.deleteHospital(id);
        if (ok) {
            HttpUtil.sendSuccess(exchange, "Hospital removed successfully.", null);
        } else {
            HttpUtil.sendError(exchange, 500, "Failed to remove hospital record.");
        }
    }
}
