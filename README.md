# MediCareX — Healthcare SaaS Platform

> **Smarter Healthcare. Better Access.**  
> Real-time medicine availability, verified pharmacy networks, digital prescription issuance, and clinical workflow management.

---

## 🏥 Platform Overview

MediCareX is a full-stack Healthcare SaaS platform engineered with **Pure Java + JDBC + MySQL** on the backend and a high-performance, accessible vanilla **HTML5 / CSS3 / JavaScript** frontend.

### Supported User Roles
1. **Patient**:
   - Medicine search with generic formula matching and live pharmacy inventory availability.
   - Prescription upload & OCR extraction (supports PDF, PNG, JPG).
   - Interactive 24/7 pharmacy map with geolocation and distance calculation.
   - Lifelong health records timeline (consultations, prescriptions, lab reports, insurance cards).
   - Real-time notifications and profile management.
   - Official digital prescription view & signed PDF generation.

2. **Doctor**:
   - Clinical dashboard with appointment queue and patient records.
   - Patient directory with clinical medical history.
   - Digital prescription generation with dynamic medicine rows, doctor signature, and diagnostic notes.
   - Clinical formulary search.

3. **Pharmacy**:
   - Inventory management: batch tracking, unit pricing, expiry alerts, and stock status.
   - CSV bulk stock import and export.
   - Prescription order fulfillment and customer pickup management.

4. **Administrator**:
   - Platform surveillance: user growth, prescription volume, and stock distribution.
   - User account management and role-based status toggling.
   - Secure Administrator Provisioning: Authenticated active administrators create new administrator accounts.
   - Medicine formulary and verified pharmacy directory management.

---

## 🏗️ Architecture & Technology Stack

- **Backend**: Pure Java 21 (Zero heavy frameworks, lightweight `com.sun.net.httpserver.HttpServer`).
- **Database Access**: Direct JDBC with parameterized queries for SQL injection prevention.
- **Production Database**: MySQL 8.x.
- **Authentication**: Stateless JWT tokens (HMAC-SHA256) and salted BCrypt/SHA-256 password hashing, plus Google OAuth 2.0.
- **Frontend**: Vanilla ES6 JavaScript (`api.js`, `shell.js`, `toast.js`), modular CSS (`variables.css`, `base.css`, `layout.css`, `components.css`), and semantic HTML5.
- **Document Processing**: Custom PDF generator (`PdfGenerator.java`), PDF text extractor (`PdfTextExtractor.java`), and WinRT OCR engine integration (`win_ocr_runner.ps1`).

---

## 📁 Project Structure

```
MediCareX/
├── .env.example                  # Environment configuration template
├── .gitignore                    # Git ignore rules for build artifacts & uploads
├── pom.xml                       # Root Maven parent POM
├── run.bat                       # Pure Java build & start script
├── README.md                     # Project documentation
│
├── frontend/                     # Web Application Frontend
│   ├── index.html                # Client-side router based on user role
│   ├── login.html                # Authentication sign-in page
│   ├── register.html             # User registration (Patient, Doctor, Pharmacy)
│   ├── dashboard.html            # Patient main dashboard
│   ├── prescription.html         # Digital prescription paper & signed PDF
│   ├── search.html               # Medicine search & prescription upload OCR
│   ├── map.html                  # Interactive pharmacy geolocation map
│   ├── records.html              # Health records & diagnostic timeline
│   ├── doctor-dashboard.html     # Doctor clinical dashboard & Rx issuance
│   ├── pharmacy-dashboard.html   # Pharmacy inventory & orders management
│   ├── admin-dashboard.html      # Administrator platform oversight
│   ├── admin-users.html          # User administration & admin creation
│   ├── css/                      # Modular CSS design system
│   └── js/                       # Core client modules (api.js, shell.js, toast.js)
│
├── backend/                      # Pure Java Backend
│   ├── pom.xml                   # Backend Maven configuration
│   ├── lib/                      # Required JDBC & utility JARs
│   ├── sql/                      # MySQL database schema & seed data
│   │   ├── medicarex_schema.sql  # DDL schema definition
│   │   └── medicarex_seed_data.sql # Initial seed dataset
│   ├── scripts/                  # Helper scripts (WinRT OCR runner)
│   └── src/com/medicarex/        # Java source packages
│       ├── Main.java             # Entry point & HTTP server
│       ├── auth/                 # JwtUtil & PasswordHasher
│       ├── config/               # AppConfig & DatabaseConnection
│       ├── controller/           # REST API Controllers
│       ├── model/                # Data Transfer Models
│       ├── repository/           # JDBC Repositories
│       ├── service/              # Business Logic Services
│       └── util/                 # HTTP, JSON & PDF Utilities
```

---

## 🚀 Quick Start Guide

### 1. Prerequisites
- **Java Development Kit (JDK 21+)** installed and configured in `PATH`.
- **MySQL Server** (Optional for local testing; platform automatically connects to MySQL when available or runs embedded in isolated mode).

### 2. Configure Environment
Copy `.env.example` to `.env` and set your MySQL credentials:
```bash
DB_HOST=localhost
DB_PORT=3306
DB_NAME=medicarex
DB_USER=root
DB_PASSWORD=your_password
```

### 3. Initialize MySQL Database (Production)
```sql
mysql -u root -p < backend/sql/medicarex_schema.sql
mysql -u root -p < backend/sql/medicarex_seed_data.sql
```

### 4. Build & Run Application
Double-click `run.bat` or execute in terminal:
```powershell
.\run.bat
```
The server will start on **http://localhost:8080**.

---

## 🧪 Testing

Execute automated integration test suites:
```powershell
# 1. Google OAuth & Account Linking Tests
java -cp "backend\bin;backend\lib\*" -ea com.medicarex.OAuthIntegrationTest

# 2. Doctor Prescription Issuance & Database Tests
java -cp "backend\bin;backend\lib\*" com.medicarex.PrescriptionIntegrationTest

# 3. Full HTTP REST End-to-End Tests (requires running server)
java -cp "backend\bin;backend\lib\*" com.medicarex.PrescriptionHttpE2ETest
```

---

## 🔒 Security Best Practices

- **Zero Public Admin Registration**: Administrator accounts can only be created by an existing authenticated active administrator through the admin portal.
- **Stateless Authentication**: JWT tokens with 7-day expiration and signature validation on every protected route.
- **Prescription Isolation**: Strict patient ID validation ensures patients can only access their own prescriptions and health documents.
- **SQL Injection Prevention**: 100% Parameterized queries via `PreparedStatement`.
