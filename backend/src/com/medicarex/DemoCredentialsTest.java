package com.medicarex;

import java.io.File;
import java.nio.file.Files;

public class DemoCredentialsTest {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   MEDICAREX — CREDENTIAL SECURITY AUDIT          ");
        System.out.println("==================================================");

        int passed = 0;
        int total = 5;

        try {
            File frontendDir = new File("frontend");
            if (!frontendDir.exists()) frontendDir = new File("../frontend");

            // 1. Audit login.html for demo credentials or hardcoded passwords
            System.out.println("\n[TEST 1] Audit login.html for Zero Demo Credentials");
            File loginFile = new File(frontendDir, "login.html");
            String loginContent = Files.readString(loginFile.toPath());

            boolean hasPriya = loginContent.contains("priya@example.com");
            boolean hasPatient123 = loginContent.contains("patient123");
            boolean hasDoctor123 = loginContent.contains("doctor123");
            boolean hasPharmacy123 = loginContent.contains("pharmacy123");
            boolean hasAdmin123 = loginContent.contains("admin123");
            boolean hasDemoBox = loginContent.toLowerCase().contains("demo credentials") || loginContent.toLowerCase().contains("pre-seeded");

            if (!hasPriya && !hasPatient123 && !hasDoctor123 && !hasPharmacy123 && !hasAdmin123 && !hasDemoBox) {
                System.out.println("  -> PASSED: login.html is clean! Zero demo emails, zero demo passwords, zero demo boxes.");
                passed++;
            } else {
                System.err.println("  -> FAILED: login.html contains demo credentials!");
            }

            // 2. Audit all other HTML files in frontend
            System.out.println("\n[TEST 2] Audit All Frontend HTML Files");
            boolean allHtmlClean = true;
            File[] htmlFiles = frontendDir.listFiles((dir, name) -> name.endsWith(".html"));
            if (htmlFiles != null) {
                for (File f : htmlFiles) {
                    String content = Files.readString(f.toPath());
                    if (content.contains("patient123") || content.contains("doctor123") || content.contains("pharmacy123") || content.contains("admin123") || content.toLowerCase().contains("demo credentials")) {
                        allHtmlClean = false;
                        System.err.println("  -> Found credential in " + f.getName());
                    }
                }
            }
            if (allHtmlClean) {
                System.out.println("  -> PASSED: All frontend HTML files are 100% clean of hardcoded passwords and demo credential boxes.");
                passed++;
            } else {
                System.err.println("  -> FAILED: Some HTML files contain demo credentials.");
            }

            // 3. Audit README.md
            System.out.println("\n[TEST 3] Audit README.md for Zero Plaintext Passwords");
            File readmeFile = new File("README.md");
            if (!readmeFile.exists()) readmeFile = new File("../README.md");
            String readmeContent = Files.readString(readmeFile.toPath());

            boolean readmeHasPass = readmeContent.contains("patient123") || readmeContent.contains("doctor123") || readmeContent.contains("pharmacy123") || readmeContent.contains("admin123");
            if (!readmeHasPass) {
                System.out.println("  -> PASSED: README.md does not display plaintext passwords!");
                passed++;
            } else {
                System.err.println("  -> FAILED: README.md contains demo passwords.");
            }

            // 4. Verify Legitimate Login Form Elements Exist
            System.out.println("\n[TEST 4] Verify Legitimate Login Form Elements");
            boolean hasEmailInput = loginContent.contains("id=\"login-email\"");
            boolean hasPassInput = loginContent.contains("id=\"login-password\"");
            boolean hasSubmitBtn = loginContent.contains("id=\"btn-login\"");
            boolean hasForgotLink = loginContent.contains("forgot-password.html");
            boolean hasGoogleBtn = loginContent.contains("id=\"btn-google-login\"");
            boolean hasRegisterLink = loginContent.contains("register.html");

            if (hasEmailInput && hasPassInput && hasSubmitBtn && hasForgotLink && hasGoogleBtn && hasRegisterLink) {
                System.out.println("  -> PASSED: All authentic login elements (Email, Password, Sign In, Forgot Password, Google Login, Register) verified!");
                passed++;
            } else {
                System.err.println("  -> FAILED: Missing required login form elements.");
            }

            // 5. Audit SQL Seed Comments
            System.out.println("\n[TEST 5] Audit Database SQL Seed File");
            File seedSql = new File("backend/sql/medicarex_seed_data.sql");
            if (!seedSql.exists()) seedSql = new File("sql/medicarex_seed_data.sql");
            String seedContent = Files.readString(seedSql.toPath());

            boolean seedHasPlainPass = seedContent.contains("priya@example.com -> patient123") || seedContent.contains("arjun@example.com -> doctor123");
            if (!seedHasPlainPass) {
                System.out.println("  -> PASSED: Seed SQL file contains no plaintext password comments.");
                passed++;
            } else {
                System.err.println("  -> FAILED: Seed SQL file contains plaintext password comments.");
            }

            System.out.println("\n==================================================");
            System.out.println("  SECURITY AUDIT RESULTS: " + passed + " / " + total + " TESTS PASSED");
            System.out.println("==================================================");

            if (passed == total) {
                System.exit(0);
            } else {
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error during security audit test: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
