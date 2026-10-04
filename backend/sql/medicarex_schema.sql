-- ===================================================
-- MediCareX Database Schema (MySQL Compatible)
-- ===================================================

CREATE DATABASE IF NOT EXISTS medicarex CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE medicarex;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    role VARCHAR(30) NOT NULL DEFAULT 'PATIENT', -- PATIENT, DOCTOR, PHARMACY, ADMIN
    avatar_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    auth_provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL', -- LOCAL, GOOGLE
    google_subject_id VARCHAR(255) UNIQUE,
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_email (email),
    INDEX idx_user_role (role),
    INDEX idx_user_google_sub (google_subject_id),
    INDEX idx_user_auth_provider (auth_provider),
    INDEX idx_user_created_by (created_by)
);

-- 2. Patients Table
CREATE TABLE IF NOT EXISTS patients (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL UNIQUE,
    dob DATE,
    gender VARCHAR(20),
    blood_group VARCHAR(10),
    address VARCHAR(255),
    emergency_contact VARCHAR(50),
    insurance_provider VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. Doctors Table
CREATE TABLE IF NOT EXISTS doctors (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL UNIQUE,
    specialization VARCHAR(100) NOT NULL,
    license_number VARCHAR(100) NOT NULL UNIQUE,
    hospital_affiliation VARCHAR(150),
    experience_years INT DEFAULT 5,
    consultation_fee DECIMAL(10,2) DEFAULT 500.00,
    rating DECIMAL(3,1) DEFAULT 4.8,
    available_hours VARCHAR(100) DEFAULT '09:00 AM - 05:00 PM',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 4. Pharmacies Table
CREATE TABLE IF NOT EXISTS pharmacies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    pharmacy_name VARCHAR(200) NOT NULL,
    license_number VARCHAR(100) NOT NULL,
    owner_name VARCHAR(150),
    address VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20),
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(255),
    opening_hours VARCHAR(100) DEFAULT '8:00 AM - 10:00 PM',
    is_24_hours BOOLEAN DEFAULT FALSE,
    is_verified BOOLEAN DEFAULT TRUE,
    rating DECIMAL(3,1) DEFAULT 4.7,
    status VARCHAR(30) DEFAULT 'Active', -- Active, Inactive, Pending
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_pharmacy_city (city),
    INDEX idx_pharmacy_status (status)
);

-- 5. Medicines Table
CREATE TABLE IF NOT EXISTS medicines (
    id INT AUTO_INCREMENT PRIMARY KEY,
    barcode VARCHAR(100) UNIQUE,
    name VARCHAR(200) NOT NULL,
    generic_name VARCHAR(200) NOT NULL,
    brand VARCHAR(150) NOT NULL,
    category VARCHAR(100) NOT NULL, -- Antibiotics, Pain Relief, Cardiology, Diabetes, Respiratory, Vitamins
    dosage VARCHAR(100) NOT NULL,
    form VARCHAR(50) NOT NULL, -- Tablet, Capsule, Syrup, Injection, Ointment, Drops
    prescription_required BOOLEAN DEFAULT FALSE,
    description TEXT,
    uses TEXT,
    side_effects TEXT,
    important_info TEXT,
    storage_info VARCHAR(255) DEFAULT 'Store in a cool, dry place away from direct sunlight',
    image_url VARCHAR(500),
    base_price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_medicine_barcode (barcode),
    INDEX idx_medicine_name (name),
    INDEX idx_medicine_generic (generic_name),
    INDEX idx_medicine_category (category)
);

-- 6. Inventory Table
CREATE TABLE IF NOT EXISTS inventory (
    id INT AUTO_INCREMENT PRIMARY KEY,
    pharmacy_id INT NOT NULL,
    medicine_id INT NOT NULL,
    batch_number VARCHAR(100) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    unit_price DECIMAL(10, 2) NOT NULL,
    expiry_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'In Stock', -- 'In Stock', 'Low Stock', 'Out of Stock'
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id) ON DELETE CASCADE,
    FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE,
    UNIQUE KEY uk_pharmacy_medicine (pharmacy_id, medicine_id),
    INDEX idx_inv_status (status),
    INDEX idx_inv_expiry (expiry_date)
);

-- 7. Prescriptions Table
CREATE TABLE IF NOT EXISTS prescriptions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    prescription_number VARCHAR(50) NOT NULL UNIQUE,
    doctor_id INT NOT NULL,
    patient_id INT NOT NULL,
    diagnosis VARCHAR(255) NOT NULL,
    notes TEXT,
    doctor_signature VARCHAR(255) DEFAULT 'Dr. Arjun Kumar, MD (Reg # MED-88492)',
    status VARCHAR(30) DEFAULT 'Active', -- Active, Completed, Cancelled
    issue_date DATE NOT NULL,
    valid_until DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
    INDEX idx_rx_patient (patient_id),
    INDEX idx_rx_doctor (doctor_id)
);

-- 8. Prescription Items Table
CREATE TABLE IF NOT EXISTS prescription_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    prescription_id INT NOT NULL,
    medicine_id INT,
    medicine_name VARCHAR(200) NOT NULL,
    dosage VARCHAR(100) NOT NULL,
    frequency VARCHAR(100) NOT NULL, -- e.g., "1 Tablet Twice a Day (After Food)"
    duration VARCHAR(50) NOT NULL,  -- e.g., "5 Days"
    instructions TEXT,
    quantity INT NOT NULL DEFAULT 10,
    FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE,
    FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE SET NULL
);

-- 9. Health Records Table
CREATE TABLE IF NOT EXISTS health_records (
    id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    record_type VARCHAR(50) NOT NULL, -- Consultations, Prescriptions, Lab Reports, Documents
    title VARCHAR(255) NOT NULL,
    provider_name VARCHAR(150) NOT NULL,
    description TEXT,
    document_url VARCHAR(500),
    file_size VARCHAR(50) DEFAULT '1.4 MB',
    record_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
    INDEX idx_record_type (record_type)
);

-- 10. Notifications Table
CREATE TABLE IF NOT EXISTS notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'System', -- All, Medicine, Pharmacy, Prescription, System
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    reference_id INT,
    reference_type VARCHAR(50),
    is_read BOOLEAN DEFAULT FALSE,
    time_group VARCHAR(30) DEFAULT 'Today', -- Today, Yesterday, This Week
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notif_user (user_id),
    INDEX idx_notif_type (type)
);

-- 11. Saved Medicines Table
CREATE TABLE IF NOT EXISTS saved_medicines (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    medicine_id INT NOT NULL,
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_saved_med (user_id, medicine_id)
);

-- 12. Appointments Table
CREATE TABLE IF NOT EXISTS appointments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    doctor_id INT NOT NULL,
    patient_id INT NOT NULL,
    appointment_date DATE NOT NULL,
    appointment_time VARCHAR(20) NOT NULL,
    type VARCHAR(50) DEFAULT 'Follow-up Consultation',
    status VARCHAR(30) DEFAULT 'Confirmed', -- Confirmed, Pending, Completed, Cancelled
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE
);

-- 13. Prescription Uploads Table
CREATE TABLE IF NOT EXISTS prescription_uploads (
    id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size INT NOT NULL DEFAULT 0,
    extraction_status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED',
    raw_text LONGTEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE
);

-- 14. Prescription Extracted Medicines Table
CREATE TABLE IF NOT EXISTS prescription_extracted_medicines (
    id INT AUTO_INCREMENT PRIMARY KEY,
    upload_id INT NOT NULL,
    medicine_id INT NULL,
    extracted_name VARCHAR(255) NOT NULL,
    extracted_dosage VARCHAR(100),
    confidence DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    is_confirmed TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
-- 15. Account Applications (Doctor & Pharmacy Approval Workflow)
CREATE TABLE IF NOT EXISTS account_applications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    applicant_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(30),
    role VARCHAR(30) NOT NULL, -- DOCTOR, PHARMACY
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
    rejection_reason TEXT,
    details_json TEXT,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    reviewed_by INT NULL,
    INDEX idx_app_user (user_id),
    INDEX idx_app_role (role),
    INDEX idx_app_status (status),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL
);

-- 16. Pharmacy Bills Table
CREATE TABLE IF NOT EXISTS bills (
    id INT AUTO_INCREMENT PRIMARY KEY,
    pharmacy_id INT NOT NULL,
    bill_number VARCHAR(50) NOT NULL UNIQUE,
    patient_id INT NULL,
    customer_name VARCHAR(150) NOT NULL DEFAULT 'Walk-in Customer',
    customer_phone VARCHAR(30),
    prescription_id INT NULL,
    subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    discount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    tax DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(50) DEFAULT 'CASH', -- CASH, CARD, UPI
    payment_status VARCHAR(30) DEFAULT 'PAID', -- PAID, CANCELLED, REFUNDED
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by INT NULL,
    INDEX idx_bill_pharmacy (pharmacy_id),
    INDEX idx_bill_number (bill_number),
    FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id) ON DELETE CASCADE,
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE SET NULL,
    FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE SET NULL,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

-- 17. Pharmacy Bill Items Table
CREATE TABLE IF NOT EXISTS bill_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    bill_id INT NOT NULL,
    medicine_id INT NOT NULL,
    medicine_name VARCHAR(200) NOT NULL,
    batch_number VARCHAR(100),
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE,
    FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE
);

-- 18. Inventory Transactions Table (Audit Trail for Sales, Restocks, and Adjustments)
CREATE TABLE IF NOT EXISTS inventory_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    pharmacy_id INT NOT NULL,
    medicine_id INT NOT NULL,
    transaction_type VARCHAR(50) NOT NULL, -- SALE, RESTOCK, MANUAL_ADJUSTMENT, CLEAR_STOCK, IMPORT, REFUND
    quantity_change INT NOT NULL,
    previous_stock INT NOT NULL,
    new_stock INT NOT NULL,
    reference_id VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by INT NULL,
    INDEX idx_inv_tx_pharmacy (pharmacy_id),
    INDEX idx_inv_tx_medicine (medicine_id),
    FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id) ON DELETE CASCADE,
    FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE
);

-- 19. Treatment Goals Table (Doctor Treatment Progress Tracking)
CREATE TABLE IF NOT EXISTS treatment_goals (
    id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    doctor_id INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    unit VARCHAR(50) DEFAULT '',
    baseline_value VARCHAR(50) NOT NULL,
    target_value VARCHAR(50) NOT NULL,
    status VARCHAR(30) DEFAULT 'IN_PROGRESS', -- NOT_STARTED, IN_PROGRESS, IMPROVING, STABLE, COMPLETED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_goal_patient (patient_id),
    INDEX idx_goal_doctor (doctor_id),
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
    FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE
);

-- 20. Treatment Progress Entries Table
CREATE TABLE IF NOT EXISTS treatment_progress_entries (
    id INT AUTO_INCREMENT PRIMARY KEY,
    goal_id INT NOT NULL,
    value VARCHAR(50) NOT NULL,
    recorded_date DATE NOT NULL,
    notes TEXT,
    created_by VARCHAR(100) DEFAULT 'Doctor',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_prog_goal (goal_id),
    FOREIGN KEY (goal_id) REFERENCES treatment_goals(id) ON DELETE CASCADE
);

-- 21. AI Clinical Decision Support Audit Logs
CREATE TABLE IF NOT EXISTS ai_decision_audit_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    doctor_id INT NOT NULL,
    patient_id INT NOT NULL,
    feature VARCHAR(50) NOT NULL, -- CLINICAL_DECISION_SUPPORT, SAFETY_CHECK
    summary_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_audit_doc (doctor_id),
    INDEX idx_ai_audit_pat (patient_id),
    FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE
);



