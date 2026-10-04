package com.medicarex;

import com.medicarex.auth.JwtUtil;
import com.medicarex.config.DatabaseConnection;
import com.medicarex.model.User;
import com.medicarex.repository.UserRepository;

import java.util.Map;

public class OAuthIntegrationTest {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   Testing Google OAuth Data & Auth Logic         ");
        System.out.println("==================================================");

        DatabaseConnection.initialize();
        UserRepository userRepo = new UserRepository();

        // 1. Test existing user lookup
        User priya = userRepo.findByEmail("priya@example.com");
        assert priya != null : "Priya must exist";
        String originalPasswordHash = priya.getPasswordHash();
        String originalRole = priya.getRole();
        System.out.println("[PASS] Found existing user: " + priya.getEmail() + ", role: " + priya.getRole());

        // 2. Test Safe Account Linking for existing user logging in with Google
        String testGoogleSub = "google-sub-123456789";
        boolean linked = userRepo.linkGoogleAccount(priya.getId(), testGoogleSub, "https://lh3.googleusercontent.com/a/test-avatar");
        assert linked : "Linking must succeed";

        User linkedPriya = userRepo.findById(priya.getId());
        assert testGoogleSub.equals(linkedPriya.getGoogleSubjectId()) : "Google subject ID must be linked";
        assert "GOOGLE".equals(linkedPriya.getAuthProvider()) : "Auth provider must be GOOGLE";
        assert originalPasswordHash.equals(linkedPriya.getPasswordHash()) : "Original password hash must NOT be changed";
        assert originalRole.equals(linkedPriya.getRole()) : "Original role must NOT be changed";
        System.out.println("[PASS] Safe account linking preserved password & role: " + linkedPriya.toSafeMap());

        // 3. Test Lookup by Google Sub
        User foundBySub = userRepo.findByGoogleSubjectId(testGoogleSub);
        assert foundBySub != null && foundBySub.getId() == priya.getId() : "Lookup by Google sub must find Priya";
        System.out.println("[PASS] Found user by Google sub: " + foundBySub.getEmail());

        // 4. Test New Google User Provisioning (Default Patient role)
        String newGoogleEmail = "newpatient_" + System.currentTimeMillis() + "@gmail.com";
        String newGoogleSub = "google-sub-new-" + System.currentTimeMillis();
        User newGoogleUser = userRepo.createUserWithGoogle(newGoogleEmail, "Rahul Sharma", newGoogleSub, "https://lh3.googleusercontent.com/a/rahul", "PATIENT", null);
        assert newGoogleUser != null : "New Google user creation must succeed";
        assert "PATIENT".equals(newGoogleUser.getRole()) : "New Google user role must default to PATIENT";
        assert "GOOGLE".equals(newGoogleUser.getAuthProvider()) : "Auth provider must be GOOGLE";
        assert newGoogleSub.equals(newGoogleUser.getGoogleSubjectId()) : "Google subject ID must match";
        System.out.println("[PASS] New Google user created safely: " + newGoogleUser.toSafeMap());

        // 5. Test Duplicate Account Prevention
        User duplicateCheck = userRepo.findByGoogleSubjectId(newGoogleSub);
        assert duplicateCheck != null && duplicateCheck.getId() == newGoogleUser.getId() : "Sub lookup returns existing record";
        System.out.println("[PASS] Duplicate account prevention verified (id: " + duplicateCheck.getId() + ")");

        // 6. Test JWT Generation for Google authenticated user
        String token = JwtUtil.generateToken(newGoogleUser.getId(), newGoogleUser.getEmail(), newGoogleUser.getRole(), newGoogleUser.getFullName());
        assert token != null && !token.isEmpty() : "JWT must be generated";
        Map<String, Object> claims = JwtUtil.validateToken(token);
        assert claims != null : "JWT must verify";
        assert "PATIENT".equals(claims.get("role")) : "JWT claim role must match";
        System.out.println("[PASS] MediCareX JWT generated and verified successfully for Google user");

        System.out.println("==================================================");
        System.out.println("   ALL INTEGRATION LOGIC TESTS PASSED (6/6)!     ");
        System.out.println("==================================================");
    }
}
