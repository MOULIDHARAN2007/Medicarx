package com.medicarex.auth;

import com.medicarex.config.AppConfig;
import com.medicarex.util.JsonUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {
    private static final String HMAC_ALGO = "HmacSHA256";

    public static String generateToken(int userId, String email, String role, String fullName) {
        long now = System.currentTimeMillis();
        long exp = now + AppConfig.JWT_EXPIRATION_MS;

        Map<String, Object> header = new HashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("email", email);
        payload.put("role", role);
        payload.put("fullName", fullName);
        payload.put("iat", now / 1000L);
        payload.put("exp", exp / 1000L);

        String headerEncoded = base64UrlEncode(JsonUtil.toJson(header));
        String payloadEncoded = base64UrlEncode(JsonUtil.toJson(payload));
        String dataToSign = headerEncoded + "." + payloadEncoded;
        String signature = sign(dataToSign, AppConfig.JWT_SECRET);

        return dataToSign + "." + signature;
    }

    public static Map<String, Object> validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        if (token.startsWith("Bearer ") || token.startsWith("bearer ")) {
            token = token.substring(7).trim();
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String headerEncoded = parts[0];
        String payloadEncoded = parts[1];
        String signature = parts[2];

        String expectedSignature = sign(headerEncoded + "." + payloadEncoded, AppConfig.JWT_SECRET);
        if (!expectedSignature.equals(signature)) {
            return null;
        }

        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(payloadEncoded), StandardCharsets.UTF_8);
            Map<String, Object> claims = JsonUtil.parseMap(payloadJson);
            if (claims == null) return null;

            Number expNum = (Number) claims.get("exp");
            if (expNum != null) {
                long expSeconds = expNum.longValue();
                if (System.currentTimeMillis() / 1000L > expSeconds) {
                    return null; // Expired
                }
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private static String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(secretKey);
            byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("JWT signing error", e);
        }
    }

    private static String base64UrlEncode(String input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }
}
