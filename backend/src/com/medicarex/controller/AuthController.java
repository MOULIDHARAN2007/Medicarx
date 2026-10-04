package com.medicarex.controller;

import com.medicarex.auth.JwtUtil;
import com.medicarex.auth.PasswordHasher;
import com.medicarex.config.AppConfig;
import com.medicarex.model.User;
import com.medicarex.repository.UserRepository;
import com.medicarex.util.HttpUtil;
import com.medicarex.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthController implements HttpHandler {
    private final UserRepository userRepo = new UserRepository();
    private final com.medicarex.repository.ApplicationRepository appRepo = new com.medicarex.repository.ApplicationRepository();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleOptions(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.endsWith("/login") && "POST".equalsIgnoreCase(method)) {
            handleLogin(exchange);
        } else if (path.endsWith("/register") && "POST".equalsIgnoreCase(method)) {
            handleRegister(exchange);
        } else if (path.endsWith("/me") && "GET".equalsIgnoreCase(method)) {
            handleMe(exchange);
        } else if (path.endsWith("/google/config") && "GET".equalsIgnoreCase(method)) {
            handleGoogleConfig(exchange);
        } else if ((path.endsWith("/google/url") || path.endsWith("/google")) && "GET".equalsIgnoreCase(method)) {
            handleGoogleAuthUrl(exchange);
        } else if (path.endsWith("/google/callback") && "GET".equalsIgnoreCase(method)) {
            handleGoogleCallback(exchange);
        } else if (path.endsWith("/google") && "POST".equalsIgnoreCase(method)) {
            handleGoogleAuth(exchange);
        } else if (path.endsWith("/forgot-password") && "POST".equalsIgnoreCase(method)) {
            handleForgotPassword(exchange);
        } else if (path.endsWith("/reset-password") && "POST".equalsIgnoreCase(method)) {
            handleResetPassword(exchange);
        } else if (path.endsWith("/verify-email") && "POST".equalsIgnoreCase(method)) {
            handleVerifyEmail(exchange);
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found");
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String email = (String) req.get("email");
        String password = (String) req.get("password");

        if (email == null || password == null || email.trim().isEmpty() || password.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Email and password are required.");
            return;
        }

        User user = userRepo.findByEmail(email.trim().toLowerCase());
        if (user == null) {
            HttpUtil.sendError(exchange, 401, "Invalid email or password.");
            return;
        }

        if (!PasswordHasher.verifyPassword(password, user.getSalt(), user.getPasswordHash())) {
            HttpUtil.sendError(exchange, 401, "Invalid email or password.");
            return;
        }

        if (!user.isActive()) {
            com.medicarex.model.AccountApplication app = appRepo.findLatestByUserId(user.getId());
            if (app != null) {
                if ("PENDING".equalsIgnoreCase(app.getStatus())) {
                    HttpUtil.sendError(exchange, 403, "Your " + user.getRole().toLowerCase() + " account application is currently pending administrator approval. You will be able to log in once approved.");
                    return;
                } else if ("REJECTED".equalsIgnoreCase(app.getStatus())) {
                    String reason = app.getRejectionReason() != null && !app.getRejectionReason().trim().isEmpty() ? " Reason: " + app.getRejectionReason() : "";
                    HttpUtil.sendError(exchange, 403, "Your " + user.getRole().toLowerCase() + " account application was rejected by the administrator." + reason);
                    return;
                }
            }
            HttpUtil.sendError(exchange, 403, "Your account has been deactivated or is awaiting approval. Please contact support.");
            return;
        }

        String token = JwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole(), user.getFullName());
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("token", token);
        responseData.put("user", user.toSafeMap());

        HttpUtil.sendSuccess(exchange, "Login successful", responseData);
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String fullName = (String) req.get("fullName");
        String email = (String) req.get("email");
        String phone = (String) req.get("phone");
        String password = (String) req.get("password");
        String role = (String) req.get("role");

        if (email == null || password == null || fullName == null || email.trim().isEmpty() || password.trim().isEmpty() || fullName.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Full name, email, and password are required.");
            return;
        }

        String finalRole = role != null && !role.trim().isEmpty() ? role.toUpperCase() : "PATIENT";

        // Admin Registration is strictly forbidden via public registration endpoint
        if ("ADMIN".equals(finalRole)) {
            HttpUtil.sendError(exchange, 403, "Public administrator registration is not permitted. Administrator accounts must be created by an existing active administrator.");
            return;
        }

        // Only allow PATIENT, DOCTOR, PHARMACY for public registration
        if (!"PATIENT".equals(finalRole) && !"DOCTOR".equals(finalRole) && !"PHARMACY".equals(finalRole)) {
            HttpUtil.sendError(exchange, 400, "Invalid registration role specified.");
            return;
        }

        if (userRepo.findByEmail(email.trim().toLowerCase()) != null) {
            HttpUtil.sendError(exchange, 400, "An account with this email address already exists.");
            return;
        }

        String salt = PasswordHasher.generateSalt();
        String passwordHash = PasswordHasher.hashPassword(password, salt);

        User newUser = userRepo.createUser(email, passwordHash, salt, fullName, phone, finalRole, req);
        if (newUser == null) {
            HttpUtil.sendError(exchange, 500, "Failed to create user account.");
            return;
        }

        // If DOCTOR or PHARMACY, create an application record and require admin approval
        if ("DOCTOR".equals(finalRole) || "PHARMACY".equals(finalRole)) {
            appRepo.createApplication(newUser.getId(), newUser.getFullName(), newUser.getEmail(), newUser.getPhone(), finalRole, req);
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("pendingApproval", true);
            responseData.put("role", finalRole);
            responseData.put("user", newUser.toSafeMap());
            HttpUtil.sendSuccess(exchange, "Registration submitted successfully. Your " + finalRole.toLowerCase() + " application is pending administrator approval before you can log in.", responseData);
            return;
        }

        String token = JwtUtil.generateToken(newUser.getId(), newUser.getEmail(), newUser.getRole(), newUser.getFullName());
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("token", token);
        responseData.put("user", newUser.toSafeMap());

        HttpUtil.sendSuccess(exchange, "Registration successful", responseData);
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        Map<String, Object> claims = HttpUtil.authenticate(exchange);
        if (claims == null) {
            HttpUtil.sendError(exchange, 401, "Unauthorized or token expired.");
            return;
        }

        Number userIdNum = (Number) claims.get("userId");
        if (userIdNum == null) {
            HttpUtil.sendError(exchange, 401, "Invalid token payload.");
            return;
        }

        User user = userRepo.findById(userIdNum.intValue());
        if (user == null) {
            HttpUtil.sendError(exchange, 404, "User not found.");
            return;
        }

        HttpUtil.sendSuccess(exchange, "Current user loaded", user.toSafeMap());
    }

    private void handleGoogleConfig(HttpExchange exchange) throws IOException {
        Map<String, Object> res = new HashMap<>();
        res.put("configured", AppConfig.isGoogleOAuthConfigured());
        res.put("clientId", AppConfig.GOOGLE_CLIENT_ID);
        res.put("redirectUri", AppConfig.getEffectiveRedirectUri(exchange));
        HttpUtil.sendSuccess(exchange, "Google OAuth configuration loaded", res);
    }

    private void handleGoogleAuthUrl(HttpExchange exchange) throws IOException {
        Map<String, Object> res = new HashMap<>();
        boolean configured = AppConfig.isGoogleOAuthConfigured();
        res.put("configured", configured);
        String redirectUri = AppConfig.getEffectiveRedirectUri(exchange);
        if (configured) {
            String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
            String clientId = URLEncoder.encode(AppConfig.GOOGLE_CLIENT_ID, StandardCharsets.UTF_8);
            String state = UUID.randomUUID().toString().replace("-", "");
            String authUrl = "https://accounts.google.com/o/oauth2/v2/auth?"
                    + "client_id=" + clientId
                    + "&redirect_uri=" + encodedRedirect
                    + "&response_type=code"
                    + "&scope=openid%20email%20profile"
                    + "&access_type=offline"
                    + "&prompt=select_account"
                    + "&state=" + state;
            res.put("url", authUrl);
            res.put("clientId", AppConfig.GOOGLE_CLIENT_ID);
            res.put("redirectUri", redirectUri);
            res.put("state", state);
        } else {
            res.put("url", null);
            res.put("error", "Google OAuth 2.0 is not configured on this server.");
        }
        HttpUtil.sendSuccess(exchange, "Google OAuth authorization URL", res);
    }

    private void handleGoogleCallback(HttpExchange exchange) throws IOException {
        Map<String, String> queryParams = HttpUtil.getQueryParams(exchange);
        String code = queryParams.get("code");
        String error = queryParams.get("error");

        if (error != null || code == null || code.trim().isEmpty()) {
            String redirectUrl = "/login.html?error=" + URLEncoder.encode(error != null ? error : "authorization_cancelled", StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Location", redirectUrl);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }

        if (!AppConfig.isGoogleOAuthConfigured()) {
            String redirectUrl = "/login.html?error=oauth_not_configured";
            exchange.getResponseHeaders().set("Location", redirectUrl);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }

        String effectiveRedirectUri = AppConfig.getEffectiveRedirectUri(exchange);
        Map<String, Object> googlePayload = exchangeGoogleAuthCode(code, effectiveRedirectUri);
        if (googlePayload == null) {
            String redirectUrl = "/login.html?error=google_auth_failed";
            exchange.getResponseHeaders().set("Location", redirectUrl);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }

        String sub = (String) googlePayload.get("sub");
        String email = (String) googlePayload.get("email");
        String name = (String) googlePayload.get("name");
        String picture = (String) googlePayload.get("picture");

        if (email == null || email.trim().isEmpty()) {
            String redirectUrl = "/login.html?error=no_email_in_token";
            exchange.getResponseHeaders().set("Location", redirectUrl);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }

        User user = null;
        // 1. First check by Google unique subject ID
        if (sub != null && !sub.trim().isEmpty()) {
            user = userRepo.findByGoogleSubjectId(sub.trim());
        }

        // 2. If not found by sub, check by verified email (Safe Account Linking)
        if (user == null) {
            User existingEmailUser = userRepo.findByEmail(email.trim().toLowerCase());
            if (existingEmailUser != null) {
                // Link Google identity to existing account safely without changing password/role
                if (sub != null && !sub.trim().isEmpty()) {
                    userRepo.linkGoogleAccount(existingEmailUser.getId(), sub.trim(), picture);
                }
                user = userRepo.findById(existingEmailUser.getId());
            }
        }

        // 3. If account still does not exist, provision safe new PATIENT account
        if (user == null) {
            user = userRepo.createUserWithGoogle(email, name, sub, picture, "PATIENT", null);
            if (user == null) {
                String redirectUrl = "/login.html?error=user_provisioning_failed";
                exchange.getResponseHeaders().set("Location", redirectUrl);
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }
        }

        if (!user.isActive()) {
            String redirectUrl = "/login.html?error=account_deactivated";
            exchange.getResponseHeaders().set("Location", redirectUrl);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }

        String token = JwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole(), user.getFullName());
        String userJson = JsonUtil.toJson(user.toSafeMap());
        String targetDashboard = "/dashboard.html";
        String role = user.getRole() != null ? user.getRole().toUpperCase() : "PATIENT";
        if ("DOCTOR".equals(role)) targetDashboard = "/doctor-dashboard.html";
        else if ("PHARMACY".equals(role)) targetDashboard = "/pharmacy-dashboard.html";
        else if ("ADMIN".equals(role)) targetDashboard = "/admin-dashboard.html";

        // Serve client storage bridge
        String bridgeHtml = "<!DOCTYPE html><html><head><meta charset='utf-8'><title>Authenticating...</title>"
                + "<script>"
                + "localStorage.setItem('medicarex_token', " + JsonUtil.toJson(token) + ");"
                + "localStorage.setItem('medicarex_user', " + JsonUtil.toJson(userJson) + ");"
                + "window.location.href = '" + targetDashboard + "';"
                + "</script></head><body style='font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;background:#f8fafc;'>"
                + "<div style='text-align:center;padding:2rem;background:#fff;border-radius:12px;box-shadow:0 4px 6px -1px rgba(0,0,0,0.1);'>"
                + "<h3 style='color:#0284c7;margin:0 0 0.5rem;'>Authentication Successful</h3>"
                + "<p style='color:#64748b;margin:0;'>Redirecting to your dashboard...</p>"
                + "</div></body></html>";
        byte[] bytes = bridgeHtml.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void handleGoogleAuth(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String idToken = (String) req.get("credential");
        if (idToken == null || idToken.trim().isEmpty()) {
            idToken = (String) req.get("idToken");
        }
        String code = (String) req.get("code");

        if ((idToken == null || idToken.trim().isEmpty()) && (code == null || code.trim().isEmpty())) {
            HttpUtil.sendError(exchange, 400, "Google authorization code or ID token is required.");
            return;
        }

        if (!AppConfig.isGoogleOAuthConfigured()) {
            HttpUtil.sendError(exchange, 503, "Google OAuth 2.0 is not configured on this server. Please configure GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET.");
            return;
        }

        Map<String, Object> googlePayload = null;
        if (code != null && !code.trim().isEmpty()) {
            String redirectUri = (String) req.get("redirectUri");
            if (redirectUri == null || redirectUri.trim().isEmpty()) {
                redirectUri = AppConfig.getEffectiveRedirectUri(exchange);
            }
            googlePayload = exchangeGoogleAuthCode(code.trim(), redirectUri);
        } else {
            googlePayload = verifyGoogleToken(idToken.trim());
        }

        if (googlePayload == null) {
            HttpUtil.sendError(exchange, 401, "Invalid, expired, or unverified Google OAuth token/code.");
            return;
        }

        String sub = (String) googlePayload.get("sub");
        String email = (String) googlePayload.get("email");
        String name = (String) googlePayload.get("name");
        String picture = (String) googlePayload.get("picture");

        if (email == null || email.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Verified Google email not found in token payload.");
            return;
        }

        User user = null;
        // 1. Lookup by Google sub
        if (sub != null && !sub.trim().isEmpty()) {
            user = userRepo.findByGoogleSubjectId(sub.trim());
        }

        // 2. Lookup by email & safe link
        if (user == null) {
            User existingEmailUser = userRepo.findByEmail(email.trim().toLowerCase());
            if (existingEmailUser != null) {
                if (sub != null && !sub.trim().isEmpty()) {
                    userRepo.linkGoogleAccount(existingEmailUser.getId(), sub.trim(), picture);
                }
                user = userRepo.findById(existingEmailUser.getId());
            }
        }

        // 3. Provision new PATIENT if not found
        if (user == null) {
            user = userRepo.createUserWithGoogle(email, name, sub, picture, "PATIENT", null);
            if (user == null) {
                HttpUtil.sendError(exchange, 500, "Failed to provision user from Google identity.");
                return;
            }
        }

        if (!user.isActive()) {
            HttpUtil.sendError(exchange, 403, "Your account has been deactivated. Please contact support.");
            return;
        }

        String token = JwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole(), user.getFullName());
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("token", token);
        responseData.put("user", user.toSafeMap());

        HttpUtil.sendSuccess(exchange, "Google authentication successful", responseData);
    }

    private void handleForgotPassword(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String email = (String) req.get("email");
        if (email == null || email.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Email address is required.");
            return;
        }

        User user = userRepo.findByEmail(email.trim().toLowerCase());
        String rawToken = UUID.randomUUID().toString().replace("-", "") + Long.toHexString(System.currentTimeMillis());
        String tokenHash = hashToken(rawToken);

        if (user != null) {
            long expiresAt = System.currentTimeMillis() + (60 * 60 * 1000L); // 1 hour
            userRepo.createPasswordReset(user.getId(), tokenHash, expiresAt);
        }

        // Return generic message to prevent account enumeration
        Map<String, Object> res = new HashMap<>();
        res.put("message", "If an account exists for " + email + ", password reset instructions have been generated.");
        res.put("token", rawToken); // Included for dev/testing verification

        HttpUtil.sendSuccess(exchange, "Password reset initiated", res);
    }

    private void handleResetPassword(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String token = (String) req.get("token");
        String newPassword = (String) req.get("newPassword");

        if (token == null || newPassword == null || token.trim().isEmpty() || newPassword.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Reset token and new password are required.");
            return;
        }

        String tokenHash = hashToken(token.trim());
        Map<String, Object> resetRecord = userRepo.findValidPasswordReset(tokenHash);

        if (resetRecord == null) {
            HttpUtil.sendError(exchange, 400, "Invalid, expired, or already used password reset token.");
            return;
        }

        int userId = (int) resetRecord.get("userId");
        int resetId = (int) resetRecord.get("id");

        String newSalt = PasswordHasher.generateSalt();
        String newHash = PasswordHasher.hashPassword(newPassword, newSalt);

        boolean updated = userRepo.updatePasswordAndInvalidateReset(userId, resetId, newHash, newSalt);
        if (!updated) {
            HttpUtil.sendError(exchange, 500, "Failed to update password.");
            return;
        }

        HttpUtil.sendSuccess(exchange, "Password has been successfully updated. You may now sign in.", null);
    }

    private void handleVerifyEmail(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, Object> req = JsonUtil.parseMap(body);

        String token = (String) req.get("token");
        if (token == null || token.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Verification token is required.");
            return;
        }

        String tokenHash = hashToken(token.trim());
        boolean verified = userRepo.verifyEmailToken(tokenHash);
        if (!verified) {
            HttpUtil.sendError(exchange, 400, "Invalid or expired email verification token.");
            return;
        }

        HttpUtil.sendSuccess(exchange, "Email address verified successfully.", null);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            return token;
        }
    }

    private Map<String, Object> verifyGoogleToken(String idToken) {
        try {
            String verifyUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            URL url = URI.create(verifyUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    return JsonUtil.parseMap(sb.toString());
                }
            }
        } catch (Exception e) {
            System.err.println("[AuthController] Google token verification error: " + e.getMessage());
        }
        return null;
    }

    private Map<String, Object> exchangeGoogleAuthCode(String code, String redirectUri) {
        try {
            String tokenUrl = "https://oauth2.googleapis.com/token";
            URL url = URI.create(tokenUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            String targetRedirect = (redirectUri != null && !redirectUri.trim().isEmpty()) ? redirectUri.trim() : AppConfig.GOOGLE_REDIRECT_URI;
            String postData = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(AppConfig.GOOGLE_CLIENT_ID, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(AppConfig.GOOGLE_CLIENT_SECRET, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(targetRedirect, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    Map<String, Object> tokenRes = JsonUtil.parseMap(sb.toString());
                    if (tokenRes != null) {
                        String idToken = (String) tokenRes.get("id_token");
                        if (idToken != null && !idToken.trim().isEmpty()) {
                            Map<String, Object> profile = verifyGoogleToken(idToken);
                            if (profile != null) return profile;
                        }
                        String accessToken = (String) tokenRes.get("access_token");
                        if (accessToken != null && !accessToken.trim().isEmpty()) {
                            return fetchGoogleUserInfo(accessToken);
                        }
                    }
                }
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    System.err.println("[AuthController] Google token exchange failed (HTTP " + conn.getResponseCode() + "): " + sb.toString());
                }
            }
        } catch (Exception e) {
            System.err.println("[AuthController] Error exchanging Google auth code: " + e.getMessage());
        }
        return null;
    }

    private Map<String, Object> fetchGoogleUserInfo(String accessToken) {
        try {
            String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
            URL url = URI.create(userInfoUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    return JsonUtil.parseMap(sb.toString());
                }
            }
        } catch (Exception e) {
            System.err.println("[AuthController] Error fetching Google userinfo: " + e.getMessage());
        }
        return null;
    }
}
