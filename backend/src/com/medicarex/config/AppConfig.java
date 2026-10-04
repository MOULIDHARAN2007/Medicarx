package com.medicarex.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AppConfig {
    private static final Map<String, String> ENV_FILE_VARS = loadDotEnv();

    public static final int PORT = getEnvInt("PORT", 8080);
    public static final String JWT_SECRET = getEnv("JWT_SECRET", "medicarex_super_secret_jwt_hmac256_key_2026_healthcare_saas");
    public static final long JWT_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    // Google OAuth 2.0 Configuration
    public static final String GOOGLE_CLIENT_ID = getEnv("GOOGLE_CLIENT_ID", "");
    public static final String GOOGLE_CLIENT_SECRET = getEnv("GOOGLE_CLIENT_SECRET", "");
    public static final String GOOGLE_REDIRECT_URI = getEnv("GOOGLE_REDIRECT_URI", "http://localhost:8080/api/auth/google/callback");

    public static boolean isGoogleOAuthConfigured() {
        return GOOGLE_CLIENT_ID != null && !GOOGLE_CLIENT_ID.trim().isEmpty() 
            && !GOOGLE_CLIENT_ID.contains("YOUR_GOOGLE")
            && GOOGLE_CLIENT_SECRET != null && !GOOGLE_CLIENT_SECRET.trim().isEmpty()
            && !GOOGLE_CLIENT_SECRET.contains("YOUR_GOOGLE");
    }

    // SMTP Configuration
    public static final String SMTP_HOST = getEnv("SMTP_HOST", "");
    public static final String SMTP_PORT = getEnv("SMTP_PORT", "587");
    public static final String SMTP_USER = getEnv("SMTP_USER", "");
    public static final String SMTP_PASS = getEnv("SMTP_PASS", "");

    public static boolean isSmtpConfigured() {
        return SMTP_HOST != null && !SMTP_HOST.trim().isEmpty() && SMTP_USER != null && !SMTP_USER.trim().isEmpty();
    }
    
    // MySQL configuration
    public static final String MYSQL_HOST = getEnv("DB_HOST", System.getProperty("db.host", "localhost"));
    public static final String MYSQL_PORT = getEnv("DB_PORT", System.getProperty("db.port", "3306"));
    public static final String MYSQL_DB = getEnv("DB_NAME", System.getProperty("db.name", "medicarex"));
    public static final String MYSQL_USER = getEnv("DB_USER", System.getProperty("db.user", "root"));
    public static final String MYSQL_PASSWORD = getEnv("DB_PASSWORD", System.getProperty("db.password", ""));
    public static final String MYSQL_URL = resolveMysqlUrl();
    
    // Fallback SQLite configuration for zero-setup execution
    public static final String SQLITE_DB_PATH = resolveSqlitePath();
    public static final String SQLITE_URL = "jdbc:sqlite:" + SQLITE_DB_PATH;

    private static String resolveMysqlUrl() {
        String directUrl = getEnv("DB_URL", null);
        if (directUrl != null && !directUrl.trim().isEmpty()) {
            return directUrl.trim();
        }
        return "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/" + MYSQL_DB + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String resolveSqlitePath() {
        File f1 = new File("backend" + File.separator + "medicarex.db");
        if (f1.exists()) return f1.getAbsolutePath();
        File f2 = new File("medicarex.db");
        if (f2.exists()) return f2.getAbsolutePath();
        File f3 = new File(".." + File.separator + "backend" + File.separator + "medicarex.db");
        if (f3.exists()) return f3.getAbsolutePath();
        return f1.getAbsolutePath();
    }
    
    // Frontend static assets path
    public static final String FRONTEND_DIR = resolveFrontendDir();

    private static String resolveFrontendDir() {
        File f = new File("frontend");
        if (f.exists() && f.isDirectory()) return "frontend";
        File parentF = new File("../frontend");
        if (parentF.exists() && parentF.isDirectory()) return "../frontend";
        return "frontend";
    }

    public static String getEffectiveRedirectUri(com.sun.net.httpserver.HttpExchange exchange) {
        String configuredUri = GOOGLE_REDIRECT_URI;
        if (exchange != null) {
            String fwdHost = exchange.getRequestHeaders().getFirst("X-Forwarded-Host");
            String host = exchange.getRequestHeaders().getFirst("Host");
            String effectiveHost = (fwdHost != null && !fwdHost.trim().isEmpty()) ? fwdHost.trim() : (host != null ? host.trim() : "");
            
            String fwdProto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
            String proto = (fwdProto != null && !fwdProto.trim().isEmpty()) ? fwdProto.trim() : "http";
            if (effectiveHost.contains("trycloudflare.com") || effectiveHost.contains(".ngrok") || effectiveHost.contains(".loca.lt")) {
                proto = "https";
            }

            // If request comes from an external tunnel (e.g. Cloudflare) but configuredUri is localhost, auto-adapt
            if (!effectiveHost.isEmpty() && !effectiveHost.startsWith("localhost") && !effectiveHost.startsWith("127.0.0.1") 
                    && (configuredUri == null || configuredUri.trim().isEmpty() || configuredUri.contains("localhost") || configuredUri.contains("127.0.0.1"))) {
                return proto + "://" + effectiveHost + "/api/auth/google/callback";
            }
        }
        if (configuredUri != null && !configuredUri.trim().isEmpty()) {
            return configuredUri.trim();
        }
        return "http://localhost:" + PORT + "/api/auth/google/callback";
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        // 1. Load base .env if present
        String[] basePaths = { ".env", "../.env", "backend/.env", "../backend/.env" };
        for (String path : basePaths) {
            File envFile = new File(path);
            if (envFile.exists() && envFile.isFile()) {
                parseEnvFile(envFile, map);
                break;
            }
        }
        // 2. Load .env.local if present (overrides .env)
        String[] localPaths = { ".env.local", "../.env.local", "backend/.env.local", "../backend/.env.local" };
        for (String path : localPaths) {
            File envFile = new File(path);
            if (envFile.exists() && envFile.isFile()) {
                parseEnvFile(envFile, map);
                break;
            }
        }
        return map;
    }

    private static void parseEnvFile(File envFile, Map<String, String> targetMap) {
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    }
                    targetMap.put(key, val);
                }
            }
        } catch (IOException ignored) {}
    }

    public static String getEnv(String key, String defaultVal) {
        String val = System.getenv(key);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        val = System.getProperty(key);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        if (ENV_FILE_VARS.containsKey(key)) {
            val = ENV_FILE_VARS.get(key);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return defaultVal;
    }

    public static int getEnvInt(String key, int defaultVal) {
        String val = getEnv(key, null);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }
}

