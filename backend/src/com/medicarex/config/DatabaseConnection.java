package com.medicarex.config;

import com.medicarex.auth.PasswordHasher;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {
    private static String activeDbType = "MYSQL"; // MYSQL or SQLITE
    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized) return;

        // Try MySQL first
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(AppConfig.MYSQL_URL, AppConfig.MYSQL_USER, AppConfig.MYSQL_PASSWORD)) {
                if (conn != null) {
                    activeDbType = "MYSQL";
                    System.out.println("[Database] Connected successfully to MySQL database (" + AppConfig.MYSQL_URL + ").");
                    runSchemaMigrations(conn);
                    initialized = true;
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println("[Database] MySQL not reachable (" + e.getMessage() + "). Initializing embedded SQLite database for zero-friction setup...");
        }

        // Fallback to SQLite
        try {
            Class.forName("org.sqlite.JDBC");
            File dbFile = new File(AppConfig.SQLITE_DB_PATH);
            if (dbFile.getParentFile() != null) {
                dbFile.getParentFile().mkdirs();
            }

            try (Connection conn = DriverManager.getConnection(AppConfig.SQLITE_URL)) {
                activeDbType = "SQLITE";
                try (Statement pragma = conn.createStatement()) {
                    pragma.execute("PRAGMA journal_mode=WAL;");
                    pragma.execute("PRAGMA busy_timeout=10000;");
                    pragma.execute("PRAGMA synchronous=NORMAL;");
                }
                System.out.println("[Database] Connected successfully to embedded database (" + AppConfig.SQLITE_URL + ") with WAL concurrency.");
                initSqliteSchemaAndSeed(conn);
                runSchemaMigrations(conn);
            }
            initialized = true;
        } catch (Exception e) {
            System.err.println("[Database] Failed to initialize SQLite database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            initialize();
        }
        if ("MYSQL".equals(activeDbType)) {
            return DriverManager.getConnection(AppConfig.MYSQL_URL, AppConfig.MYSQL_USER, AppConfig.MYSQL_PASSWORD);
        } else {
            Connection conn = DriverManager.getConnection(AppConfig.SQLITE_URL);
            try (Statement pragma = conn.createStatement()) {
                pragma.execute("PRAGMA busy_timeout=10000;");
            } catch (Exception ignored) {}
            return conn;
        }
    }

    public static String getActiveDbType() {
        return activeDbType;
    }

    private static void runSchemaMigrations(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Check & add auth_provider to users table if missing
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN auth_provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL';");
            } catch (Exception ignored) {}

            // Check & add google_subject_id to users table if missing
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN google_subject_id VARCHAR(255);");
            } catch (Exception ignored) {}

            // Check & add index on google_subject_id
            try {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_google_sub ON users(google_subject_id);");
            } catch (Exception ignored) {}

            // Check & add created_by to users table if missing
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN created_by INT;");
            } catch (Exception ignored) {}

            // Check & add barcode to medicines table if missing
            try {
                if ("MYSQL".equals(activeDbType)) {
                    stmt.execute("ALTER TABLE medicines ADD COLUMN barcode VARCHAR(100) UNIQUE NULL;");
                } else {
                    stmt.execute("ALTER TABLE medicines ADD COLUMN barcode TEXT;");
                    stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_medicines_barcode ON medicines(barcode);");
                }
            } catch (Exception ignored) {}

            // Populate standard barcodes for seeded medicines if barcode is null
            try {
                stmt.execute("UPDATE medicines SET barcode = '8901030001014' WHERE id = 1 AND (barcode IS NULL OR barcode = '');");
                stmt.execute("UPDATE medicines SET barcode = '8901030001021' WHERE id = 2 AND (barcode IS NULL OR barcode = '');");
                stmt.execute("UPDATE medicines SET barcode = '8901030001038' WHERE id = 3 AND (barcode IS NULL OR barcode = '');");
                stmt.execute("UPDATE medicines SET barcode = '8901030001045' WHERE id = 4 AND (barcode IS NULL OR barcode = '');");
                stmt.execute("UPDATE medicines SET barcode = '8901030001052' WHERE id = 5 AND (barcode IS NULL OR barcode = '');");
                stmt.execute("UPDATE medicines SET barcode = '8901030001069' WHERE id = 6 AND (barcode IS NULL OR barcode = '');");
                stmt.execute("UPDATE medicines SET barcode = '8901030001076' WHERE id = 7 AND (barcode IS NULL OR barcode = '');");
                stmt.execute("UPDATE medicines SET barcode = '8901030001083' WHERE id = 8 AND (barcode IS NULL OR barcode = '');");
                stmt.execute("UPDATE medicines SET barcode = '8901030001090' WHERE id = 9 AND (barcode IS NULL OR barcode = '');");
                stmt.execute("UPDATE medicines SET barcode = '8901030001106' WHERE id = 10 AND (barcode IS NULL OR barcode = '');");
            } catch (Exception ignored) {}

            // Hospitals Table Migration (MySQL / SQLite)
            try {
                if ("MYSQL".equals(activeDbType)) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS hospitals (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "name VARCHAR(150) NOT NULL, " +
                            "registration_number VARCHAR(100), " +
                            "address VARCHAR(255) NOT NULL, " +
                            "city VARCHAR(100) NOT NULL, " +
                            "state VARCHAR(100) NOT NULL, " +
                            "pincode VARCHAR(20) NOT NULL, " +
                            "latitude DECIMAL(10,8) NOT NULL, " +
                            "longitude DECIMAL(11,8) NOT NULL, " +
                            "phone VARCHAR(50) NOT NULL, " +
                            "emergency_contact VARCHAR(50), " +
                            "emergency_available TINYINT(1) DEFAULT 1, " +
                            "icu_available TINYINT(1) DEFAULT 1, " +
                            "available_beds INT DEFAULT 12, " +
                            "total_beds INT DEFAULT 60, " +
                            "accepting_ambulances TINYINT(1) DEFAULT 1, " +
                            "is_active TINYINT(1) DEFAULT 1, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                            "INDEX idx_hosp_active (is_active), " +
                            "INDEX idx_hosp_emg (emergency_available), " +
                            "INDEX idx_hosp_amb (accepting_ambulances));");
                } else {
                    stmt.execute("CREATE TABLE IF NOT EXISTS hospitals (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "name TEXT NOT NULL, " +
                            "registration_number TEXT, " +
                            "address TEXT NOT NULL, " +
                            "city TEXT NOT NULL, " +
                            "state TEXT NOT NULL, " +
                            "pincode TEXT NOT NULL, " +
                            "latitude REAL NOT NULL, " +
                            "longitude REAL NOT NULL, " +
                            "phone TEXT NOT NULL, " +
                            "emergency_contact TEXT, " +
                            "emergency_available INTEGER DEFAULT 1, " +
                            "icu_available INTEGER DEFAULT 1, " +
                            "available_beds INTEGER DEFAULT 12, " +
                            "total_beds INTEGER DEFAULT 60, " +
                            "accepting_ambulances INTEGER DEFAULT 1, " +
                            "is_active INTEGER DEFAULT 1, " +
                            "approval_status TEXT DEFAULT 'ACTIVE', " +
                            "created_by_user_id INTEGER NULL, " +
                            "created_by_role TEXT DEFAULT 'ADMIN', " +
                            "rejection_reason TEXT NULL, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP);");
                }
            } catch (Exception ignored) {}

            // Migrate hospitals to add approval_status, creator, rejection reason
            try { stmt.execute("ALTER TABLE hospitals ADD COLUMN approval_status VARCHAR(30) DEFAULT 'ACTIVE';"); } catch (Exception ignored) {}
            try {
                if ("MYSQL".equals(activeDbType)) {
                    stmt.execute("ALTER TABLE hospitals ADD COLUMN created_by_user_id INT NULL;");
                } else {
                    stmt.execute("ALTER TABLE hospitals ADD COLUMN created_by_user_id INTEGER NULL;");
                }
            } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE hospitals ADD COLUMN created_by_role VARCHAR(30) DEFAULT 'ADMIN';"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE hospitals ADD COLUMN rejection_reason TEXT NULL;"); } catch (Exception ignored) {}

            // Migrate emergency_requests to add destination hospital columns
            try {
                if ("MYSQL".equals(activeDbType)) {
                    stmt.execute("ALTER TABLE emergency_requests ADD COLUMN destination_hospital_id INT NULL;");
                } else {
                    stmt.execute("ALTER TABLE emergency_requests ADD COLUMN destination_hospital_id INTEGER NULL;");
                }
            } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE emergency_requests ADD COLUMN destination_hospital_name VARCHAR(150) NULL;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE emergency_requests ADD COLUMN destination_hospital_address VARCHAR(255) NULL;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE emergency_requests ADD COLUMN destination_hospital_phone VARCHAR(50) NULL;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE emergency_requests ADD COLUMN destination_latitude DECIMAL(10,8) NULL;"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE emergency_requests ADD COLUMN destination_longitude DECIMAL(11,8) NULL;"); } catch (Exception ignored) {}

            // Seed initial realistic hospitals
            try {
                if ("MYSQL".equals(activeDbType)) {
                    stmt.execute("INSERT IGNORE INTO hospitals (id, name, registration_number, address, city, state, pincode, latitude, longitude, phone, emergency_contact, emergency_available, icu_available, available_beds, total_beds, accepting_ambulances, is_active) VALUES " +
                            "(1, 'Manipal Hospital Trauma & Multi-Specialty', 'HOSP-KA-BLR-001', '98 HAL Old Airport Rd, Kodihalli', 'Bengaluru', 'Karnataka', '560008', 12.9587, 77.6486, '+91 80 2502 4444', '+91 80 2502 3333', 1, 1, 24, 120, 1, 1)," +
                            "(2, 'Apollo Hospitals Emergency & Trauma Center', 'HOSP-KA-BLR-002', '154/11 Bannerghatta Rd, Opp IIMB', 'Bengaluru', 'Karnataka', '560076', 12.8945, 77.5985, '+91 80 2630 4050', '+91 80 1066', 1, 1, 18, 150, 1, 1)," +
                            "(3, 'Fortis Hospital Multi-Specialty & ER', 'HOSP-KA-BLR-003', '14 Cunningham Rd, Vasanth Nagar', 'Bengaluru', 'Karnataka', '560052', 12.9868, 77.5973, '+91 80 6621 4444', '+91 80 6621 4400', 1, 1, 15, 80, 1, 1)," +
                            "(4, 'St. Johns Medical College Hospital', 'HOSP-KA-BLR-004', 'Sarjapur Main Rd, Koramangala 2nd Block', 'Bengaluru', 'Karnataka', '560034', 12.9345, 77.6245, '+91 80 2206 5000', '+91 80 2206 5111', 1, 1, 35, 200, 1, 1)," +
                            "(5, 'Cloudnine Hospital & Emergency Care', 'HOSP-KA-BLR-005', '1st Main Rd, Whitefield', 'Bengaluru', 'Karnataka', '560066', 12.9864, 77.7345, '+91 80 4966 7000', '+91 80 4966 7111', 1, 0, 8, 40, 1, 1)," +
                            "(6, 'Apollo Main Hospital Emergency Center', 'HOSP-TN-CHN-001', '21 Greams Lane, Thousand Lights', 'Chennai', 'Tamil Nadu', '600006', 13.0604, 80.2508, '+91 44 2829 0200', '+91 44 1066', 1, 1, 28, 180, 1, 1)," +
                            "(7, 'Kauvery Hospital 24/7 Trauma Care', 'HOSP-TN-CHN-002', '199 Luz Church Rd, Mylapore', 'Chennai', 'Tamil Nadu', '600004', 13.0375, 80.2612, '+91 44 4000 6000', '+91 44 4000 6100', 1, 1, 16, 100, 1, 1)," +
                            "(8, 'Gleneagles Global Health City', 'HOSP-TN-CHN-003', '439 Cheran Nagar, Perumbakkam', 'Chennai', 'Tamil Nadu', '600100', 12.8988, 80.1983, '+91 44 4477 7000', '+91 44 4477 7111', 1, 1, 20, 140, 1, 1);");
                } else {
                    stmt.execute("INSERT OR IGNORE INTO hospitals (id, name, registration_number, address, city, state, pincode, latitude, longitude, phone, emergency_contact, emergency_available, icu_available, available_beds, total_beds, accepting_ambulances, is_active) VALUES " +
                            "(1, 'Manipal Hospital Trauma & Multi-Specialty', 'HOSP-KA-BLR-001', '98 HAL Old Airport Rd, Kodihalli', 'Bengaluru', 'Karnataka', '560008', 12.9587, 77.6486, '+91 80 2502 4444', '+91 80 2502 3333', 1, 1, 24, 120, 1, 1)," +
                            "(2, 'Apollo Hospitals Emergency & Trauma Center', 'HOSP-KA-BLR-002', '154/11 Bannerghatta Rd, Opp IIMB', 'Bengaluru', 'Karnataka', '560076', 12.8945, 77.5985, '+91 80 2630 4050', '+91 80 1066', 1, 1, 18, 150, 1, 1)," +
                            "(3, 'Fortis Hospital Multi-Specialty & ER', 'HOSP-KA-BLR-003', '14 Cunningham Rd, Vasanth Nagar', 'Bengaluru', 'Karnataka', '560052', 12.9868, 77.5973, '+91 80 6621 4444', '+91 80 6621 4400', 1, 1, 15, 80, 1, 1)," +
                            "(4, 'St. Johns Medical College Hospital', 'HOSP-KA-BLR-004', 'Sarjapur Main Rd, Koramangala 2nd Block', 'Bengaluru', 'Karnataka', '560034', 12.9345, 77.6245, '+91 80 2206 5000', '+91 80 2206 5111', 1, 1, 35, 200, 1, 1)," +
                            "(5, 'Cloudnine Hospital & Emergency Care', 'HOSP-KA-BLR-005', '1st Main Rd, Whitefield', 'Bengaluru', 'Karnataka', '560066', 12.9864, 77.7345, '+91 80 4966 7000', '+91 80 4966 7111', 1, 0, 8, 40, 1, 1)," +
                            "(6, 'Apollo Main Hospital Emergency Center', 'HOSP-TN-CHN-001', '21 Greams Lane, Thousand Lights', 'Chennai', 'Tamil Nadu', '600006', 13.0604, 80.2508, '+91 44 2829 0200', '+91 44 1066', 1, 1, 28, 180, 1, 1)," +
                            "(7, 'Kauvery Hospital 24/7 Trauma Care', 'HOSP-TN-CHN-002', '199 Luz Church Rd, Mylapore', 'Chennai', 'Tamil Nadu', '600004', 13.0375, 80.2612, '+91 44 4000 6000', '+91 44 4000 6100', 1, 1, 16, 100, 1, 1)," +
                            "(8, 'Gleneagles Global Health City', 'HOSP-TN-CHN-003', '439 Cheran Nagar, Perumbakkam', 'Chennai', 'Tamil Nadu', '600100', 12.8988, 80.1983, '+91 44 4477 7000', '+91 44 4477 7111', 1, 1, 20, 140, 1, 1);");
                }
            } catch (Exception ignored) {}

            try {
                if ("MYSQL".equals(activeDbType)) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS account_applications (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "user_id INT NOT NULL, " +
                            "applicant_name VARCHAR(150) NOT NULL, " +
                            "email VARCHAR(255) NOT NULL, " +
                            "phone VARCHAR(30), " +
                            "role VARCHAR(30) NOT NULL, " +
                            "status VARCHAR(30) NOT NULL DEFAULT 'PENDING', " +
                            "rejection_reason TEXT, " +
                            "details_json TEXT, " +
                            "submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "reviewed_at TIMESTAMP NULL, " +
                            "reviewed_by INT NULL, " +
                            "INDEX idx_app_user (user_id), " +
                            "INDEX idx_app_role (role), " +
                            "INDEX idx_app_status (status));");

                    stmt.execute("CREATE TABLE IF NOT EXISTS bills (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "pharmacy_id INT NOT NULL, " +
                            "bill_number VARCHAR(50) NOT NULL UNIQUE, " +
                            "patient_id INT NULL, " +
                            "customer_name VARCHAR(150) NOT NULL DEFAULT 'Walk-in Customer', " +
                            "customer_phone VARCHAR(30), " +
                            "prescription_id INT NULL, " +
                            "subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00, " +
                            "discount DECIMAL(10,2) NOT NULL DEFAULT 0.00, " +
                            "tax DECIMAL(10,2) NOT NULL DEFAULT 0.00, " +
                            "total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00, " +
                            "payment_method VARCHAR(50) DEFAULT 'CASH', " +
                            "payment_status VARCHAR(30) DEFAULT 'PAID', " +
                            "notes TEXT, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "created_by INT NULL, " +
                            "INDEX idx_bill_pharmacy (pharmacy_id), " +
                            "INDEX idx_bill_number (bill_number), " +
                            "FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id) ON DELETE CASCADE);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS bill_items (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "bill_id INT NOT NULL, " +
                            "medicine_id INT NOT NULL, " +
                            "medicine_name VARCHAR(200) NOT NULL, " +
                            "batch_number VARCHAR(100), " +
                            "quantity INT NOT NULL, " +
                            "unit_price DECIMAL(10,2) NOT NULL, " +
                            "subtotal DECIMAL(10,2) NOT NULL, " +
                            "FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE, " +
                            "FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS inventory_transactions (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "pharmacy_id INT NOT NULL, " +
                            "medicine_id INT NOT NULL, " +
                            "transaction_type VARCHAR(50) NOT NULL, " +
                            "quantity_change INT NOT NULL, " +
                            "previous_stock INT NOT NULL, " +
                            "new_stock INT NOT NULL, " +
                            "reference_id VARCHAR(100), " +
                            "notes TEXT, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "created_by INT NULL, " +
                            "INDEX idx_inv_tx_pharmacy (pharmacy_id), " +
                            "INDEX idx_inv_tx_medicine (medicine_id), " +
                            "FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id) ON DELETE CASCADE, " +
                            "FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS treatment_goals (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "patient_id INT NOT NULL, " +
                            "doctor_id INT NOT NULL, " +
                            "title VARCHAR(200) NOT NULL, " +
                            "metric_name VARCHAR(100) NOT NULL, " +
                            "unit VARCHAR(50) DEFAULT '', " +
                            "baseline_value VARCHAR(50) NOT NULL, " +
                            "target_value VARCHAR(50) NOT NULL, " +
                            "status VARCHAR(30) DEFAULT 'IN_PROGRESS', " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                            "INDEX idx_goal_patient (patient_id), " +
                            "INDEX idx_goal_doctor (doctor_id), " +
                            "FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE, " +
                            "FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS treatment_progress_entries (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "goal_id INT NOT NULL, " +
                            "value VARCHAR(50) NOT NULL, " +
                            "recorded_date DATE NOT NULL, " +
                            "notes TEXT, " +
                            "created_by VARCHAR(100) DEFAULT 'Doctor', " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "INDEX idx_prog_goal (goal_id), " +
                            "FOREIGN KEY (goal_id) REFERENCES treatment_goals(id) ON DELETE CASCADE);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS ai_decision_audit_logs (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "doctor_id INT NOT NULL, " +
                            "patient_id INT NOT NULL, " +
                            "feature VARCHAR(50) NOT NULL, " +
                            "summary_notes TEXT, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "INDEX idx_ai_audit_doc (doctor_id), " +
                            "INDEX idx_ai_audit_pat (patient_id), " +
                            "FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE, " +
                            "FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE);");

                    // Emergency Ambulance & Patient Enhancements Tables (MySQL)
                    stmt.execute("CREATE TABLE IF NOT EXISTS ambulances (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "vehicle_number VARCHAR(50) NOT NULL UNIQUE, " +
                            "vehicle_type VARCHAR(50) NOT NULL DEFAULT 'BASIC', " +
                            "driver_name VARCHAR(100), " +
                            "driver_phone VARCHAR(50), " +
                            "driver_user_id INT NULL, " +
                            "status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE', " +
                            "current_latitude DECIMAL(10,8) NOT NULL DEFAULT 13.0827, " +
                            "current_longitude DECIMAL(11,8) NOT NULL DEFAULT 80.2707, " +
                            "hospital_affiliation VARCHAR(150), " +
                            "equipment_summary TEXT, " +
                            "is_active TINYINT(1) DEFAULT 1, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                            "INDEX idx_amb_status (status), " +
                            "INDEX idx_amb_driver (driver_user_id));");

                    stmt.execute("CREATE TABLE IF NOT EXISTS emergency_requests (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "patient_id INT NOT NULL, " +
                            "emergency_type VARCHAR(100) NOT NULL DEFAULT 'GENERAL', " +
                            "severity VARCHAR(50) NOT NULL DEFAULT 'CRITICAL', " +
                            "pickup_latitude DECIMAL(10,8) NOT NULL, " +
                            "pickup_longitude DECIMAL(11,8) NOT NULL, " +
                            "pickup_address VARCHAR(255) NOT NULL, " +
                            "landmark VARCHAR(150), " +
                            "contact_phone VARCHAR(50), " +
                            "patient_notes TEXT, " +
                            "assigned_ambulance_id INT NULL, " +
                            "driver_user_id INT NULL, " +
                            "status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED', " +
                            "estimated_arrival_minutes INT DEFAULT 8, " +
                            "cancellation_reason TEXT, " +
                            "requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "accepted_at TIMESTAMP NULL, " +
                            "en_route_at TIMESTAMP NULL, " +
                            "arrived_pickup_at TIMESTAMP NULL, " +
                            "picked_up_at TIMESTAMP NULL, " +
                            "arrived_hospital_at TIMESTAMP NULL, " +
                            "completed_at TIMESTAMP NULL, " +
                            "cancelled_at TIMESTAMP NULL, " +
                            "INDEX idx_emg_pat (patient_id), " +
                            "INDEX idx_emg_amb (assigned_ambulance_id), " +
                            "INDEX idx_emg_status (status));");

                    stmt.execute("CREATE TABLE IF NOT EXISTS patient_data_sharing (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "patient_id INT NOT NULL, " +
                            "doctor_id INT NOT NULL, " +
                            "access_scope VARCHAR(50) NOT NULL DEFAULT 'FULL_HISTORY', " +
                            "status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', " +
                            "notes TEXT, " +
                            "expires_at VARCHAR(50) NULL, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "revoked_at TIMESTAMP NULL, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                            "INDEX idx_share_pat (patient_id), " +
                            "INDEX idx_share_doc (doctor_id));");

                    stmt.execute("CREATE TABLE IF NOT EXISTS patient_data_access_logs (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "patient_id INT NOT NULL, " +
                            "accessed_by_user_id INT NOT NULL, " +
                            "record_type VARCHAR(100) NOT NULL, " +
                            "record_id INT NULL, " +
                            "action VARCHAR(50) DEFAULT 'VIEW', " +
                            "ip_address VARCHAR(50), " +
                            "accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "INDEX idx_share_log_pat (patient_id));");

                    stmt.execute("CREATE TABLE IF NOT EXISTS patient_emergency_card (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "patient_id INT NOT NULL UNIQUE, " +
                            "blood_group VARCHAR(10) DEFAULT 'O+', " +
                            "emergency_contact_name VARCHAR(100), " +
                            "emergency_contact_phone VARCHAR(50), " +
                            "emergency_contact_relation VARCHAR(50), " +
                            "known_allergies TEXT, " +
                            "chronic_conditions TEXT, " +
                            "current_medications_summary TEXT, " +
                            "is_organ_donor TINYINT(1) DEFAULT 0, " +
                            "insurance_provider VARCHAR(150), " +
                            "insurance_policy_number VARCHAR(100), " +
                            "public_token VARCHAR(100) NOT NULL UNIQUE, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (patient_id) REFERENCES users(id) ON DELETE CASCADE);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS visit_preparations (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "patient_id INT NOT NULL, " +
                            "appointment_id INT NULL, " +
                            "doctor_id INT NULL, " +
                            "primary_symptoms TEXT, " +
                            "symptom_duration VARCHAR(100), " +
                            "questions_for_doctor TEXT, " +
                            "current_vitals_notes TEXT, " +
                            "medication_changes TEXT, " +
                            "additional_notes TEXT, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (patient_id) REFERENCES users(id) ON DELETE CASCADE);");
                } else {
                    stmt.execute("CREATE TABLE IF NOT EXISTS account_applications (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER NOT NULL, " +
                            "applicant_name TEXT NOT NULL, " +
                            "email TEXT NOT NULL, " +
                            "phone TEXT, " +
                            "role TEXT NOT NULL, " +
                            "status TEXT NOT NULL DEFAULT 'PENDING', " +
                            "rejection_reason TEXT, " +
                            "details_json TEXT, " +
                            "submitted_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "reviewed_at DATETIME NULL, " +
                            "reviewed_by INTEGER NULL);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS bills (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "pharmacy_id INTEGER NOT NULL, " +
                            "bill_number TEXT NOT NULL UNIQUE, " +
                            "patient_id INTEGER NULL, " +
                            "customer_name TEXT NOT NULL DEFAULT 'Walk-in Customer', " +
                            "customer_phone TEXT, " +
                            "prescription_id INTEGER NULL, " +
                            "subtotal REAL NOT NULL DEFAULT 0.0, " +
                            "discount REAL NOT NULL DEFAULT 0.0, " +
                            "tax REAL NOT NULL DEFAULT 0.0, " +
                            "total_amount REAL NOT NULL DEFAULT 0.0, " +
                            "payment_method TEXT DEFAULT 'CASH', " +
                            "payment_status TEXT DEFAULT 'PAID', " +
                            "notes TEXT, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "created_by INTEGER NULL, " +
                            "FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id) ON DELETE CASCADE);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS bill_items (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "bill_id INTEGER NOT NULL, " +
                            "medicine_id INTEGER NOT NULL, " +
                            "medicine_name TEXT NOT NULL, " +
                            "batch_number TEXT, " +
                            "quantity INTEGER NOT NULL, " +
                            "unit_price REAL NOT NULL, " +
                            "subtotal REAL NOT NULL, " +
                            "FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS inventory_transactions (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "pharmacy_id INTEGER NOT NULL, " +
                            "medicine_id INTEGER NOT NULL, " +
                            "transaction_type TEXT NOT NULL, " +
                            "quantity_change INTEGER NOT NULL, " +
                            "previous_stock INTEGER NOT NULL, " +
                            "new_stock INTEGER NOT NULL, " +
                            "reference_id TEXT, " +
                            "notes TEXT, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "created_by INTEGER NULL, " +
                            "FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id) ON DELETE CASCADE);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS treatment_goals (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "patient_id INTEGER NOT NULL, " +
                            "doctor_id INTEGER NOT NULL, " +
                            "title TEXT NOT NULL, " +
                            "metric_name TEXT NOT NULL, " +
                            "unit TEXT DEFAULT '', " +
                            "baseline_value TEXT NOT NULL, " +
                            "target_value TEXT NOT NULL, " +
                            "status TEXT DEFAULT 'IN_PROGRESS', " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE, " +
                            "FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS treatment_progress_entries (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "goal_id INTEGER NOT NULL, " +
                            "value TEXT NOT NULL, " +
                            "recorded_date TEXT NOT NULL, " +
                            "notes TEXT, " +
                            "created_by TEXT DEFAULT 'Doctor', " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (goal_id) REFERENCES treatment_goals(id) ON DELETE CASCADE);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS ai_decision_audit_logs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "doctor_id INTEGER NOT NULL, " +
                            "patient_id INTEGER NOT NULL, " +
                            "feature TEXT NOT NULL, " +
                            "summary_notes TEXT, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE, " +
                            "FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE);");

                    // Emergency Ambulance & Patient Enhancements Tables (SQLite)
                    stmt.execute("CREATE TABLE IF NOT EXISTS ambulances (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "vehicle_number TEXT NOT NULL UNIQUE, " +
                            "vehicle_type TEXT NOT NULL DEFAULT 'BASIC', " +
                            "driver_name TEXT, " +
                            "driver_phone TEXT, " +
                            "driver_user_id INTEGER NULL, " +
                            "status TEXT NOT NULL DEFAULT 'AVAILABLE', " +
                            "current_latitude REAL NOT NULL DEFAULT 13.0827, " +
                            "current_longitude REAL NOT NULL DEFAULT 80.2707, " +
                            "hospital_affiliation TEXT, " +
                            "equipment_summary TEXT, " +
                            "is_active INTEGER DEFAULT 1, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS emergency_requests (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "patient_id INTEGER NOT NULL, " +
                            "emergency_type TEXT NOT NULL DEFAULT 'GENERAL', " +
                            "severity TEXT NOT NULL DEFAULT 'CRITICAL', " +
                            "pickup_latitude REAL NOT NULL, " +
                            "pickup_longitude REAL NOT NULL, " +
                            "pickup_address TEXT NOT NULL, " +
                            "landmark TEXT, " +
                            "contact_phone TEXT, " +
                            "patient_notes TEXT, " +
                            "assigned_ambulance_id INTEGER NULL, " +
                            "driver_user_id INTEGER NULL, " +
                            "status TEXT NOT NULL DEFAULT 'REQUESTED', " +
                            "estimated_arrival_minutes INTEGER DEFAULT 8, " +
                            "cancellation_reason TEXT, " +
                            "requested_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "accepted_at DATETIME NULL, " +
                            "en_route_at DATETIME NULL, " +
                            "arrived_pickup_at DATETIME NULL, " +
                            "picked_up_at DATETIME NULL, " +
                            "arrived_hospital_at DATETIME NULL, " +
                            "completed_at DATETIME NULL, " +
                            "cancelled_at DATETIME NULL);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS patient_data_sharing (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "patient_id INTEGER NOT NULL, " +
                            "doctor_id INTEGER NOT NULL, " +
                            "access_scope TEXT NOT NULL DEFAULT 'FULL_HISTORY', " +
                            "status TEXT NOT NULL DEFAULT 'ACTIVE', " +
                            "notes TEXT, " +
                            "expires_at TEXT NULL, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "revoked_at DATETIME NULL, " +
                            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS patient_data_access_logs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "patient_id INTEGER NOT NULL, " +
                            "accessed_by_user_id INTEGER NOT NULL, " +
                            "record_type TEXT NOT NULL, " +
                            "record_id INTEGER NULL, " +
                            "action TEXT DEFAULT 'VIEW', " +
                            "ip_address TEXT, " +
                            "accessed_at DATETIME DEFAULT CURRENT_TIMESTAMP);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS patient_emergency_card (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "patient_id INTEGER NOT NULL UNIQUE, " +
                            "blood_group TEXT DEFAULT 'O+', " +
                            "emergency_contact_name TEXT, " +
                            "emergency_contact_phone TEXT, " +
                            "emergency_contact_relation TEXT, " +
                            "known_allergies TEXT, " +
                            "chronic_conditions TEXT, " +
                            "current_medications_summary TEXT, " +
                            "is_organ_donor INTEGER DEFAULT 0, " +
                            "insurance_provider TEXT, " +
                            "insurance_policy_number TEXT, " +
                            "public_token TEXT NOT NULL UNIQUE, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP);");

                    stmt.execute("CREATE TABLE IF NOT EXISTS visit_preparations (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "patient_id INTEGER NOT NULL, " +
                            "appointment_id INTEGER NULL, " +
                            "doctor_id INTEGER NULL, " +
                            "primary_symptoms TEXT, " +
                            "symptom_duration TEXT, " +
                            "questions_for_doctor TEXT, " +
                            "current_vitals_notes TEXT, " +
                            "medication_changes TEXT, " +
                            "additional_notes TEXT, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP);");
                }
            } catch (Exception ignored) {}

            try {
                stmt.execute("UPDATE users SET is_active = 1 WHERE email IN ('priya@example.com', 'arjun@example.com', 'healthplus@example.com', 'medlife@example.com', 'apollocare@example.com', 'admin@example.com');");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            System.err.println("[Database] Schema migration notice: " + e.getMessage());
        }
    }

    private static void initSqliteSchemaAndSeed(Connection conn) {
        System.out.println("[Database] Creating tables and seeding initial medical data in embedded DB...");
        try (Statement stmt = conn.createStatement()) {
            // SQLite DDL
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "email TEXT NOT NULL UNIQUE, " +
                    "password_hash TEXT NOT NULL, " +
                    "salt TEXT NOT NULL, " +
                    "full_name TEXT NOT NULL, " +
                    "phone TEXT, " +
                    "role TEXT NOT NULL DEFAULT 'PATIENT', " +
                    "avatar_url TEXT, " +
                    "is_active INTEGER NOT NULL DEFAULT 1, " +
                    "auth_provider TEXT NOT NULL DEFAULT 'LOCAL', " +
                    "google_subject_id TEXT UNIQUE, " +
                    "created_by INTEGER, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP);");

            stmt.execute("CREATE TABLE IF NOT EXISTS patients (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL UNIQUE, " +
                    "dob TEXT, gender TEXT, blood_group TEXT, address TEXT, emergency_contact TEXT, insurance_provider TEXT, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE IF NOT EXISTS doctors (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL UNIQUE, " +
                    "specialization TEXT NOT NULL, license_number TEXT NOT NULL UNIQUE, hospital_affiliation TEXT, " +
                    "experience_years INTEGER DEFAULT 5, consultation_fee REAL DEFAULT 500.0, rating REAL DEFAULT 4.8, available_hours TEXT DEFAULT '09:00 AM - 05:00 PM', " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE IF NOT EXISTS pharmacies (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER, pharmacy_name TEXT NOT NULL, license_number TEXT NOT NULL, owner_name TEXT, " +
                    "address TEXT NOT NULL, city TEXT NOT NULL, state TEXT NOT NULL, postal_code TEXT, " +
                    "latitude REAL NOT NULL, longitude REAL NOT NULL, phone TEXT NOT NULL, email TEXT, " +
                    "opening_hours TEXT DEFAULT '8:00 AM - 10:00 PM', is_24_hours INTEGER DEFAULT 0, is_verified INTEGER DEFAULT 1, " +
                    "rating REAL DEFAULT 4.7, status TEXT DEFAULT 'Active', " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL);");

            stmt.execute("CREATE TABLE IF NOT EXISTS medicines (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "barcode TEXT UNIQUE, " +
                    "name TEXT NOT NULL, generic_name TEXT NOT NULL, brand TEXT NOT NULL, category TEXT NOT NULL, " +
                    "dosage TEXT NOT NULL, form TEXT NOT NULL, prescription_required INTEGER DEFAULT 0, " +
                    "description TEXT, uses TEXT, side_effects TEXT, important_info TEXT, storage_info TEXT, " +
                    "image_url TEXT, base_price REAL NOT NULL DEFAULT 0.0, is_active INTEGER DEFAULT 1, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP);");

            stmt.execute("CREATE TABLE IF NOT EXISTS inventory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "pharmacy_id INTEGER NOT NULL, medicine_id INTEGER NOT NULL, batch_number TEXT NOT NULL, " +
                    "stock_quantity INTEGER NOT NULL DEFAULT 0, unit_price REAL NOT NULL, expiry_date TEXT NOT NULL, " +
                    "status TEXT NOT NULL DEFAULT 'In Stock', last_updated DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE, " +
                    "UNIQUE (pharmacy_id, medicine_id));");

            stmt.execute("CREATE TABLE IF NOT EXISTS prescriptions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "prescription_number TEXT NOT NULL UNIQUE, doctor_id INTEGER NOT NULL, patient_id INTEGER NOT NULL, " +
                    "diagnosis TEXT NOT NULL, notes TEXT, doctor_signature TEXT, status TEXT DEFAULT 'Active', " +
                    "issue_date TEXT NOT NULL, valid_until TEXT NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE IF NOT EXISTS prescription_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "prescription_id INTEGER NOT NULL, medicine_id INTEGER, medicine_name TEXT NOT NULL, " +
                    "dosage TEXT NOT NULL, frequency TEXT NOT NULL, duration TEXT NOT NULL, instructions TEXT, quantity INTEGER NOT NULL DEFAULT 10, " +
                    "FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE SET NULL);");

            stmt.execute("CREATE TABLE IF NOT EXISTS health_records (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "patient_id INTEGER NOT NULL, record_type TEXT NOT NULL, title TEXT NOT NULL, " +
                    "provider_name TEXT NOT NULL, description TEXT, document_url TEXT, file_size TEXT DEFAULT '1.4 MB', " +
                    "record_date TEXT NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE IF NOT EXISTS notifications (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL, type TEXT NOT NULL DEFAULT 'System', title TEXT NOT NULL, " +
                    "message TEXT NOT NULL, reference_id INTEGER, reference_type TEXT, is_read INTEGER DEFAULT 0, " +
                    "time_group TEXT DEFAULT 'Today', created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE IF NOT EXISTS saved_medicines (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL, medicine_id INTEGER NOT NULL, saved_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE, " +
                    "UNIQUE (user_id, medicine_id));");

            stmt.execute("CREATE TABLE IF NOT EXISTS appointments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "doctor_id INTEGER NOT NULL, patient_id INTEGER NOT NULL, appointment_date TEXT NOT NULL, " +
                    "appointment_time TEXT NOT NULL, type TEXT DEFAULT 'Follow-up Consultation', " +
                    "status TEXT DEFAULT 'Confirmed', notes TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE IF NOT EXISTS orders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "order_number TEXT NOT NULL UNIQUE, pharmacy_id INTEGER NOT NULL, patient_id INTEGER, " +
                    "customer_name TEXT NOT NULL, customer_phone TEXT, total_amount REAL NOT NULL, " +
                    "status TEXT NOT NULL DEFAULT 'Pending', payment_method TEXT DEFAULT 'UPI / Card', " +
                    "order_date TEXT NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE IF NOT EXISTS order_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "order_id INTEGER NOT NULL, medicine_id INTEGER, medicine_name TEXT NOT NULL, " +
                    "quantity INTEGER NOT NULL, unit_price REAL NOT NULL, " +
                    "FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE IF NOT EXISTS user_settings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL UNIQUE, " +
                    "email_notifications INTEGER DEFAULT 1, sms_notifications INTEGER DEFAULT 1, " +
                    "stock_alerts INTEGER DEFAULT 1, two_factor_auth INTEGER DEFAULT 0, " +
                    "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE IF NOT EXISTS prescription_uploads (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "patient_id INTEGER NOT NULL, " +
                    "original_filename TEXT NOT NULL, " +
                    "stored_filename TEXT NOT NULL, " +
                    "file_type TEXT NOT NULL, " +
                    "file_size INTEGER NOT NULL DEFAULT 0, " +
                    "extraction_status TEXT NOT NULL DEFAULT 'COMPLETED', " +
                    "raw_text TEXT, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE IF NOT EXISTS prescription_extracted_medicines (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "upload_id INTEGER NOT NULL, " +
                    "medicine_id INTEGER, " +
                    "extracted_name TEXT NOT NULL, " +
                    "extracted_dosage TEXT, " +
                    "confidence REAL NOT NULL DEFAULT 0.0, " +
                    "is_confirmed INTEGER NOT NULL DEFAULT 0, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (upload_id) REFERENCES prescription_uploads(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE SET NULL);");

            stmt.execute("CREATE TABLE IF NOT EXISTS password_resets (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL, " +
                    "token_hash TEXT NOT NULL UNIQUE, " +
                    "expires_at INTEGER NOT NULL, " +
                    "used INTEGER DEFAULT 0, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE IF NOT EXISTS email_verifications (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL, " +
                    "token_hash TEXT NOT NULL UNIQUE, " +
                    "expires_at INTEGER NOT NULL, " +
                    "verified INTEGER DEFAULT 0, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);");

            // Seed Users using runtime hasher with standard initial credentials
            String salt = PasswordHasher.generateSalt();
            String pHash = PasswordHasher.hashPassword("Password@123", salt);

            stmt.execute("INSERT OR IGNORE INTO users (id, email, password_hash, salt, full_name, phone, role, avatar_url, is_active) VALUES " +
                    "(1, 'priya@example.com', '" + pHash + "', '" + salt + "', 'Priya Sharma', '+91 98765 43210', 'PATIENT', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150', 1)," +
                    "(2, 'arjun@example.com', '" + pHash + "', '" + salt + "', 'Dr. Arjun Kumar', '+91 98450 11223', 'DOCTOR', 'https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=150', 1)," +
                    "(3, 'healthplus@example.com', '" + pHash + "', '" + salt + "', 'Rajesh Patel (HealthPlus)', '+91 80234 56789', 'PHARMACY', 'https://images.unsplash.com/photo-1586015555751-63bb77f4322a?w=150', 1)," +
                    "(4, 'admin@example.com', '" + pHash + "', '" + salt + "', 'System Administrator', '+91 90000 00001', 'ADMIN', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150', 1)," +
                    "(5, 'medlife@example.com', '" + pHash + "', '" + salt + "', 'Suresh Reddy (MedLife)', '+91 80987 65432', 'PHARMACY', 'https://images.unsplash.com/photo-1576091160550-2173dba999ef?w=150', 1)," +
                    "(6, 'apollocare@example.com', '" + pHash + "', '" + salt + "', 'Ananya Iyer (Apollo Care)', '+91 80112 23344', 'PHARMACY', 'https://images.unsplash.com/photo-1559839734-2b71ea197ec2?w=150', 1)," +
                    "(7, 'priyanair@example.com', '" + pHash + "', '" + salt + "', 'Dr. Priya Nair', '+91 98451 22334', 'DOCTOR', 'https://images.unsplash.com/photo-1559839734-2b71ea197ec2?w=150', 1)," +
                    "(8, 'rajeshvarma@example.com', '" + pHash + "', '" + salt + "', 'Dr. Rajesh Varma', '+91 98452 33445', 'DOCTOR', 'https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?w=150', 1)," +
                    "(9, 'snehakulkarni@example.com', '" + pHash + "', '" + salt + "', 'Dr. Sneha Kulkarni', '+91 98453 44556', 'DOCTOR', 'https://images.unsplash.com/photo-1594824813576-96a84d28430b?w=150', 1)," +
                    "(10, 'vikramhegde@example.com', '" + pHash + "', '" + salt + "', 'Dr. Vikram Hegde', '+91 98454 55667', 'DOCTOR', 'https://images.unsplash.com/photo-1537368910025-700350fe46c7?w=150', 1)," +
                    "(11, 'kavitharamesh@example.com', '" + pHash + "', '" + salt + "', 'Dr. Kavitha Ramesh', '+91 98455 66778', 'DOCTOR', 'https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=150', 1)," +
                    "(12, 'ambulance@example.com', '" + pHash + "', '" + salt + "', 'Ramesh Kumar (Paramedic)', '+91 98401 23456', 'AMBULANCE', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150', 1)," +
                    "(13, 'driver@example.com', '" + pHash + "', '" + salt + "', 'Mohammed Farooq (Driver)', '+91 98402 34567', 'AMBULANCE', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150', 1);");

            // Update passwords for existing seed users to ensure valid login
            try {
                stmt.execute("UPDATE users SET password_hash = '" + pHash + "', salt = '" + salt + "' WHERE email IN ('priya@example.com', 'arjun@example.com', 'healthplus@example.com', 'admin@example.com', 'medlife@example.com', 'apollocare@example.com', 'ambulance@example.com', 'driver@example.com');");
            } catch (Exception ignored) {}

            // Seed Patient & Doctor
            stmt.execute("INSERT OR IGNORE INTO patients (id, user_id, dob, gender, blood_group, address, emergency_contact, insurance_provider) VALUES " +
                    "(1, 1, '1996-05-14', 'Female', 'O+', '42, 4th Main, Indiranagar, Bengaluru, KA 560038', '+91 98765 00000 (Spouse)', 'Star Health Comprehensive');");

            stmt.execute("INSERT OR IGNORE INTO doctors (id, user_id, specialization, license_number, hospital_affiliation, experience_years, consultation_fee, rating, available_hours) VALUES " +
                    "(1, 2, 'Cardiologist & General Physician', 'KMC-88492-MD', 'Manipal Hospital, Bangalore', 12, 750.00, 4.9, '09:00 AM - 05:00 PM')," +
                    "(2, 7, 'Pediatrician & Child Specialist', 'KMC-99120-MD', 'Aster CMI Hospital, Bangalore', 9, 600.00, 4.8, '09:00 AM - 04:30 PM')," +
                    "(3, 8, 'Dermatologist & Cosmetologist', 'KMC-77341-MD', 'Apollo Hospitals, Bangalore', 14, 800.00, 4.9, '10:00 AM - 05:00 PM')," +
                    "(4, 9, 'Neurologist & Neuro Physician', 'KMC-65432-MD', 'Fortis Hospital, Bangalore', 11, 900.00, 4.9, '09:30 AM - 04:00 PM')," +
                    "(5, 10, 'Orthopedic & Joint Surgeon', 'KMC-54129-MS', 'Sakra World Hospital, Bangalore', 16, 850.00, 4.8, '10:00 AM - 06:00 PM')," +
                    "(6, 11, 'General Physician & Internal Medicine', 'KMC-43218-MD', 'Cloudnine Hospital, Bangalore', 8, 500.00, 4.7, '08:30 AM - 03:30 PM');");

            // Seed Pharmacies
            stmt.execute("INSERT OR IGNORE INTO pharmacies (id, user_id, pharmacy_name, license_number, owner_name, address, city, state, postal_code, latitude, longitude, phone, email, opening_hours, is_24_hours, is_verified, rating, status) VALUES " +
                    "(1, 3, 'HealthPlus Pharmacy', 'DL-BLR-2023-8891', 'Rajesh Patel', '102, 100 Feet Rd, Indiranagar', 'Bengaluru', 'Karnataka', '560038', 12.9784, 77.6408, '+91 80234 56789', 'support@healthplus.in', '8:00 AM - 11:00 PM', 0, 1, 4.8, 'Active')," +
                    "(2, 5, 'MedLife Store', 'DL-BLR-2022-4412', 'Suresh Reddy', '45, CMH Road, Indiranagar', 'Bengaluru', 'Karnataka', '560038', 12.9798, 77.6360, '+91 80987 65432', 'contact@medlifestore.in', '24 Hours Open', 1, 1, 4.7, 'Active')," +
                    "(3, 6, 'Apollo Care Pharmacy', 'DL-BLR-2024-1109', 'Ananya Iyer', '18, 80 Feet Road, Koramangala 4th Block', 'Bengaluru', 'Karnataka', '560034', 12.9345, 77.6245, '+91 80112 23344', 'care@apollocare.in', '7:00 AM - 11:30 PM', 0, 1, 4.9, 'Active')," +
                    "(4, NULL, 'Guardian Lifecare Pharmacy', 'DL-BLR-2021-9988', 'Vikram Malhotra', '77, MG Road, CBD Area', 'Bengaluru', 'Karnataka', '560001', 12.9756, 77.6067, '+91 80445 56677', 'info@guardianlife.in', '24 Hours Open', 1, 1, 4.6, 'Active')," +
                    "(5, NULL, 'GreenCross Meds & Wellness', 'DL-BLR-2023-6677', 'Deepak Verma', '12, 12th Main, HAL 2nd Stage', 'Bengaluru', 'Karnataka', '560008', 12.9660, 77.6499, '+91 80332 21100', 'greencross@wellness.in', '8:30 AM - 10:30 PM', 0, 1, 4.5, 'Active')," +
                    "(6, NULL, 'Care & Cure Pharmacy', 'DL-BLR-2023-5521', 'Kavitha Nair', '14, 27th Main Rd, HSR Layout Sector 1', 'Bengaluru', 'Karnataka', '560102', 12.9121, 77.6446, '+91 80456 78901', 'carecure@hsr.in', '8:00 AM - 11:00 PM', 0, 1, 4.8, 'Active')," +
                    "(7, NULL, 'Apollo Pharmacy Whitefield', 'DL-BLR-2024-9902', 'Manoj Kumar', '88, ITPL Main Road, Whitefield', 'Bengaluru', 'Karnataka', '560066', 12.9864, 77.7345, '+91 80678 90123', 'apollowf@care.in', '24 Hours Open', 1, 1, 4.9, 'Active')," +
                    "(8, NULL, 'MedPlus Malleshwaram', 'DL-BLR-2022-7711', 'Raghavendra Rao', '52, 8th Cross, Margosa Road', 'Bengaluru', 'Karnataka', '560003', 13.0035, 77.5701, '+91 80234 11223', 'medplus@malleshwaram.in', '8:00 AM - 10:30 PM', 0, 1, 4.7, 'Active')," +
                    "(9, NULL, 'Apollo Pharmacy T Nagar', 'DL-CHN-2023-3312', 'R. Swaminathan', '42, Venkatanarayana Road, T. Nagar', 'Chennai', 'Tamil Nadu', '600017', 13.0418, 80.2341, '+91 44 2434 5678', 'tnagar@apollocare.in', '24 Hours Open', 1, 1, 4.8, 'Active')," +
                    "(10, NULL, 'MedPlus Anna Nagar', 'DL-CHN-2024-8821', 'P. Karthik', '15, 2nd Avenue, Anna Nagar', 'Chennai', 'Tamil Nadu', '600040', 13.0850, 80.2100, '+91 44 2621 9900', 'annanagar@medplus.in', '8:00 AM - 11:00 PM', 0, 1, 4.7, 'Active');");

            // Seed Medicines
            stmt.execute("INSERT OR IGNORE INTO medicines (id, name, generic_name, brand, category, dosage, form, prescription_required, description, uses, side_effects, important_info, storage_info, image_url, base_price, is_active) VALUES " +
                    "(1, 'Amoxicillin 500mg', 'Amoxicillin Trihydrate', 'Moxclav / Amoxil', 'Antibiotics', '500mg', 'Capsule', 1, 'Amoxicillin is a broad-spectrum penicillin-class antibiotic used to treat bacterial infections including respiratory tract, ear, and skin infections.', 'Treatment of pneumonia, bronchitis, ear infections, urinary tract infections, and skin infections.', 'Nausea, vomiting, diarrhea, mild rash, headache.', 'Complete the full prescribed course. Take with meals.', 'Store below 25°C in a dry place.', 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300', 85.50, 1)," +
                    "(2, 'Paracetamol 650mg', 'Acetaminophen', 'Dolo 650 / Crocin', 'Pain Relief', '650mg', 'Tablet', 0, 'Paracetamol 650mg is a trusted analgesic and antipyretic medicine that relieves moderate pain, headaches, toothaches, and lowers fever.', 'Fever reduction, relief from mild to moderate pain, headache, toothache, muscle aches.', 'Generally well tolerated. Avoid alcohol overdose.', 'Do not exceed 4000mg in 24 hours. High doses can cause liver damage.', 'Store at room temperature 15°C - 30°C.', 'https://images.unsplash.com/photo-1587854692152-cbe660dbde88?w=300', 32.00, 1)," +
                    "(3, 'Metformin 500mg', 'Metformin Hydrochloride', 'Glycomet / Glucophage', 'Diabetes', '500mg', 'Tablet', 1, 'Metformin is the first-line medication for the treatment of type 2 diabetes.', 'Management of type 2 diabetes mellitus, improving glycemic control.', 'Stomach upset, diarrhea, flatulence, metallic taste.', 'Take with meals to minimize gastrointestinal discomfort.', 'Store at 20°C to 25°C.', 'https://images.unsplash.com/photo-1471864190281-a93a3070b6de?w=300', 48.00, 1)," +
                    "(4, 'Atorvastatin 20mg', 'Atorvastatin Calcium', 'Lipitor / Atorva', 'Cardiology', '20mg', 'Tablet', 1, 'Atorvastatin lowers bad cholesterol (LDL) and triglycerides, reducing cardiovascular risk.', 'Hypercholesterolemia, prevention of cardiovascular disease.', 'Muscle pain, joint pain, diarrhea, mild headache.', 'Avoid consuming large amounts of grapefruit juice.', 'Store below 30°C.', 'https://images.unsplash.com/photo-1550572017-ed20015ade08?w=300', 145.00, 1)," +
                    "(5, 'Azithromycin 500mg', 'Azithromycin', 'Azee 500 / Zithromax', 'Antibiotics', '500mg', 'Tablet', 1, 'Azithromycin is a macrolide antibiotic that halts bacterial growth in respiratory and skin infections.', 'Throat infections, tonsillitis, sinusitis, pneumonia.', 'Nausea, diarrhea, abdominal cramps.', 'Take 1 hour before or 2 hours after meals.', 'Store below 25°C.', 'https://images.unsplash.com/photo-1584017911766-d451b3d0e843?w=300', 115.00, 1)," +
                    "(6, 'Cetirizine 10mg', 'Cetirizine Hydrochloride', 'Zyrtec / Cetzine', 'Allergy', '10mg', 'Tablet', 0, 'Cetirizine is a non-drowsy antihistamine for allergy relief.', 'Seasonal allergic rhinitis, perennial allergic rhinitis, hives.', 'Mild drowsiness in some individuals, dry mouth.', 'Use caution when driving if drowsiness occurs.', 'Store at 20°C to 25°C.', 'https://images.unsplash.com/photo-1512069772995-ec65ed45afd6?w=300', 25.00, 1)," +
                    "(7, 'Pantoprazole 40mg', 'Pantoprazole Sodium', 'Pan 40 / Pantocid', 'Gastroenterology', '40mg', 'Tablet', 0, 'Pantoprazole is a proton pump inhibitor that decreases stomach acid.', 'Gastroesophageal reflux disease (GERD), peptic ulcer disease.', 'Headache, mild diarrhea, stomach pain.', 'Take once daily in the morning 30 mins before breakfast.', 'Store below 25°C.', 'https://images.unsplash.com/photo-1550572017-4fcdbb590e5b?w=300', 92.00, 1)," +
                    "(8, 'Ibuprofen 400mg', 'Ibuprofen', 'Brufen 400 / Advil', 'Pain Relief', '400mg', 'Tablet', 0, 'Ibuprofen is an NSAID used for relieving pain and inflammation.', 'Arthritis, dental pain, headache, menstrual cramps.', 'Stomach pain, heartburn, dizziness.', 'Always take after meals or with milk.', 'Store at 15°C to 25°C.', 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300', 40.00, 1)," +
                    "(9, 'Montelukast 10mg', 'Montelukast Sodium', 'Montair 10 / Singulair', 'Respiratory', '10mg', 'Tablet', 1, 'Montelukast prevents airway inflammation in asthma and allergies.', 'Asthma management, allergic rhinitis.', 'Headache, cough, abdominal pain.', 'Take regularly every day as prescribed.', 'Store at 20°C to 25°C.', 'https://images.unsplash.com/photo-1471864190281-a93a3070b6de?w=300', 160.00, 1)," +
                    "(10, 'Vitamin D3 60,000 IU', 'Cholecalciferol', 'Calcirol / D-Rise', 'Vitamins & Supplements', '60,000 IU', 'Capsule', 0, 'High-potency Vitamin D3 supplement for bone health and immunity.', 'Vitamin D deficiency, bone health support.', 'Well tolerated at recommended doses.', 'Take once weekly with a fat-containing meal.', 'Store below 25°C.', 'https://images.unsplash.com/photo-1587854692152-cbe660dbde88?w=300', 120.00, 1);");

            // Seed Inventory
            stmt.execute("INSERT OR IGNORE INTO inventory (pharmacy_id, medicine_id, batch_number, stock_quantity, unit_price, expiry_date, status) VALUES " +
                    "(1, 1, 'HP-AMX-901', 45, 85.50, '2027-08-31', 'In Stock')," +
                    "(1, 2, 'HP-PCM-882', 120, 32.00, '2028-01-15', 'In Stock')," +
                    "(1, 3, 'HP-MET-401', 18, 48.00, '2026-11-30', 'Low Stock')," +
                    "(1, 4, 'HP-ATV-112', 60, 145.00, '2027-06-20', 'In Stock')," +
                    "(1, 5, 'HP-AZI-773', 0, 115.00, '2026-10-15', 'Out of Stock')," +
                    "(1, 6, 'HP-CET-209', 80, 25.00, '2027-12-31', 'In Stock')," +
                    "(1, 7, 'HP-PAN-504', 35, 92.00, '2027-04-15', 'In Stock')," +
                    "(1, 8, 'HP-IBU-319', 55, 40.00, '2028-03-20', 'In Stock')," +
                    "(1, 9, 'HP-MON-662', 12, 160.00, '2026-12-10', 'Low Stock')," +
                    "(1, 10, 'HP-VD3-008', 95, 120.00, '2028-05-30', 'In Stock')," +
                    "(2, 1, 'ML-AMX-101', 30, 84.00, '2027-09-15', 'In Stock')," +
                    "(2, 2, 'ML-PCM-332', 200, 30.00, '2028-02-28', 'In Stock')," +
                    "(2, 3, 'ML-MET-991', 50, 46.50, '2027-05-20', 'In Stock')," +
                    "(2, 4, 'ML-ATV-442', 25, 142.00, '2027-07-10', 'In Stock')," +
                    "(2, 5, 'ML-AZI-883', 20, 112.00, '2027-03-15', 'In Stock')," +
                    "(2, 6, 'ML-CET-419', 5, 24.00, '2026-10-01', 'Low Stock')," +
                    "(2, 7, 'ML-PAN-884', 40, 90.00, '2027-11-20', 'In Stock')," +
                    "(3, 1, 'AP-AMX-204', 50, 88.00, '2027-10-10', 'In Stock')," +
                    "(3, 2, 'AP-PCM-119', 150, 33.00, '2028-04-15', 'In Stock')," +
                    "(3, 5, 'AP-AZI-449', 35, 118.00, '2027-08-20', 'In Stock')," +
                    "(3, 9, 'AP-MON-991', 40, 165.00, '2027-09-30', 'In Stock')," +
                    "(4, 1, 'GL-AMX-801', 25, 86.00, '2027-07-20', 'In Stock')," +
                    "(4, 2, 'GL-PCM-802', 80, 31.50, '2028-03-10', 'In Stock')," +
                    "(5, 1, 'GC-AMX-701', 40, 85.00, '2027-08-15', 'In Stock')," +
                    "(6, 1, 'CC-AMX-601', 35, 87.00, '2027-09-01', 'In Stock')," +
                    "(6, 2, 'CC-PCM-602', 110, 32.00, '2028-02-15', 'In Stock')," +
                    "(7, 1, 'AW-AMX-501', 60, 89.00, '2027-11-20', 'In Stock')," +
                    "(8, 1, 'MM-AMX-401', 45, 86.50, '2027-10-05', 'In Stock')," +
                    "(9, 1, 'AT-AMX-301', 50, 87.50, '2027-11-15', 'In Stock')," +
                    "(9, 2, 'AT-PCM-302', 130, 32.00, '2028-05-10', 'In Stock')," +
                    "(10, 1, 'MA-AMX-201', 40, 86.00, '2027-12-01', 'In Stock')," +
                    "(10, 2, 'MA-PCM-202', 100, 31.50, '2028-03-25', 'In Stock');");

            // Seed Health Records (General consults and lab tests)
            stmt.execute("INSERT OR IGNORE INTO health_records (id, patient_id, record_type, title, provider_name, description, document_url, file_size, record_date) VALUES " +
                    "(1, 1, 'Consultations', 'General Physician Health Checkup', 'Dr. Arjun Kumar (Manipal Hospital)', 'Routine annual wellness exam. Vitals normal, chest clear.', 'health_record_01.pdf', '1.2 MB', '2026-09-01')," +
                    "(3, 1, 'Lab Reports', 'Complete Blood Count (CBC) & Lipid Profile', 'Apollo Diagnostics Lab, Indiranagar', 'Hemoglobin: 13.8 g/dL, Total WBC: 7,400 /mcL, Total Cholesterol: 185 mg/dL.', 'cbc_lipid_report.pdf', '2.4 MB', '2026-08-20')," +
                    "(4, 1, 'Documents', 'Health Insurance Digital Card', 'Star Health Allied Insurance', 'Policy # SH-IND-9948201 valid through December 2026. Primary insured: Priya Sharma.', 'insurance_card.pdf', '620 KB', '2026-01-10');");

            // Seed Notifications
            stmt.execute("INSERT OR IGNORE INTO notifications (id, user_id, type, title, message, reference_id, reference_type, is_read, time_group) VALUES " +
                    "(1, 1, 'Medicine', 'Amoxicillin 500mg In Stock Near You', 'HealthPlus Pharmacy (0.8 km away) has 45 units of Amoxicillin 500mg available at ₹85.50.', 1, 'MEDICINE', 0, 'Today')," +
                    "(3, 1, 'Pharmacy', 'MedLife Store Order Confirmed', 'Your recurring refill at MedLife Store is ready for in-store pickup or delivery.', 2, 'PHARMACY', 1, 'Yesterday')," +
                    "(4, 1, 'System', 'Security Notification: Password Updated', 'Your MediCareX account credentials were successfully verified.', NULL, 'SECURITY', 1, 'This Week');");

            // Seed Appointments
            stmt.execute("INSERT OR IGNORE INTO appointments (id, doctor_id, patient_id, appointment_date, appointment_time, type, status, notes) VALUES " +
                    "(1, 1, 1, '2026-09-08', '10:30 AM', 'Follow-up Consultation', 'Confirmed', 'Review blood pressure recovery and antibiotic completion response.')," +
                    "(2, 1, 1, '2026-09-15', '03:00 PM', 'Cardio Wellness Review', 'Pending', 'Routine checkup.');");

            // Seed Saved Medicines
            stmt.execute("INSERT OR IGNORE INTO saved_medicines (user_id, medicine_id) VALUES (1, 1), (1, 2), (1, 10);");

            // Seed Orders
            stmt.execute("INSERT OR IGNORE INTO orders (id, order_number, pharmacy_id, patient_id, customer_name, customer_phone, total_amount, status, payment_method, order_date) VALUES " +
                    "(1, 'ORD-984210', 1, 1, 'Priya Sharma', '+91 98765 43210', 385.50, 'Ready', 'UPI Payment', '2026-09-05')," +
                    "(2, 'ORD-984211', 1, NULL, 'Rohan Mehta', '+91 98111 22233', 145.00, 'Pending', 'Cash on Delivery', '2026-09-06')," +
                    "(3, 'ORD-984212', 1, NULL, 'Kavita Sundaram', '+91 98444 55566', 220.00, 'Completed', 'Card Payment', '2026-09-04')," +
                    "(4, 'ORD-984213', 1, NULL, 'Amit Roy', '+91 98222 33344', 92.00, 'Confirmed', 'UPI Payment', '2026-09-06');");

            stmt.execute("INSERT OR IGNORE INTO order_items (id, order_id, medicine_id, medicine_name, quantity, unit_price) VALUES " +
                    "(1, 1, 1, 'Amoxicillin 500mg', 2, 85.50)," +
                    "(2, 1, 4, 'Atorvastatin 20mg', 1, 145.00)," +
                    "(3, 1, 2, 'Paracetamol 650mg', 2, 32.00)," +
                    "(4, 2, 4, 'Atorvastatin 20mg', 1, 145.00)," +
                    "(5, 3, 10, 'Vitamin D3 60,000 IU', 1, 120.00)," +
                    "(6, 3, 8, 'Ibuprofen 400mg', 2, 40.00)," +
                    "(7, 4, 7, 'Pantoprazole 40mg', 1, 92.00);");

            // Seed Ambulances
            stmt.execute("INSERT OR IGNORE INTO ambulances (id, vehicle_number, vehicle_type, driver_name, driver_phone, driver_user_id, status, current_latitude, current_longitude, hospital_affiliation, equipment_summary, is_active) VALUES " +
                    "(1, 'KA-01-AM-1008', 'ADVANCED_LIFE_SUPPORT', 'Ramesh Kumar', '+91 98401 23456', 12, 'AVAILABLE', 12.9784, 77.6408, 'Manipal Hospital Trauma Center, Indiranagar', 'Ventilator, Defibrillator, Portable Oxygen, ECG Monitor', 1)," +
                    "(2, 'KA-05-AM-2024', 'ICU', 'Mohammed Farooq', '+91 98402 34567', 13, 'AVAILABLE', 12.9345, 77.6245, 'Apollo Emergency Center, Koramangala', 'Full Mobile ICU, Cardiac Monitor, Infusion Pump, Suction Unit', 1)," +
                    "(3, 'KA-03-AM-3312', 'BASIC', 'Senthil Nathan', '+91 98403 45678', NULL, 'AVAILABLE', 12.9864, 77.7345, 'Cloudnine Emergency Care, Whitefield', 'First Aid Kit, Stretcher, Oxygen Cylinder', 1);");

            // Seed Patient Emergency Health Card
            stmt.execute("INSERT OR IGNORE INTO patient_emergency_card (id, patient_id, blood_group, emergency_contact_name, emergency_contact_phone, " +
                    "emergency_contact_relation, known_allergies, chronic_conditions, current_medications_summary, is_organ_donor, insurance_provider, insurance_policy_number, public_token) VALUES " +
                    "(1, 1, 'O+', 'Jane Doe', '+91 98765 00000', 'Spouse', 'Penicillin, Peanuts, Sulfa Drugs', 'Hypertension (Mild), Seasonal Asthma', 'Amlodipine 5mg, Cetirizine 10mg PRN', 1, 'Star Health Allied', 'SH-IND-9948201', 'EMG-priya-9876543210-secure-token-99');");

            try {
                stmt.execute("UPDATE ambulances SET driver_user_id = 12 WHERE id = 1 AND (driver_user_id IS NULL OR driver_user_id = 0);");
                stmt.execute("UPDATE ambulances SET driver_user_id = 13 WHERE id = 2 AND (driver_user_id IS NULL OR driver_user_id = 0);");
            } catch (Exception ignored) {}

            System.out.println("[Database] Schema and realistic healthcare seed data created successfully!");
        } catch (SQLException e) {
            System.err.println("[Database] Error seeding database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
