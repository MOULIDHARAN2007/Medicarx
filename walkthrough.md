# Two-Way Hospital Management System Walkthrough

## 1. Overview
The Hospital Management system has been extended to provide a unified, single source of truth between the **Admin Portal** and the **Ambulance Dashboard** in MySQL/SQLite:
1. **Admin Update $\to$ Ambulance Real-Time Sync**: When an Administrator edits hospital details (name, address, coordinates, emergency capabilities, ICU readiness, reported bed count, accepting status, active/inactive), changes immediately reflect in the Ambulance dashboard upon loading, opening destination selector, and via controlled 15-second polling without requiring an application restart.
2. **Ambulance "Suggest / Add Hospital" $\to$ Admin Review & Approval Workflow**: Ambulance drivers can suggest/add a hospital directly from the emergency workflow. Submissions undergo coordinate range validation and duplicate prevention, starting in `PENDING_REVIEW` (inactive) status. Administrators can filter by `PENDING_REVIEW`, review details, and either **Approve** (activates hospital for emergency destination selection) or **Reject** (preserves record in audit history while excluding from dispatch).

---

## 2. Core Architecture & Workflow

```
                        MYSQL DATABASE
                              │
                    CANONICAL HOSPITALS TABLE
                              │
             ┌────────────────┴────────────────┐
             ↓                                 ↓
       ADMIN PORTAL                    AMBULANCE PORTAL
             │                                 │
     Update / Approve / Reject          View / Suggest
             │                                 │
             └────────────────┬────────────────┘
                              ↓
                      SAME HOSPITAL RECORD
```

### A. Admin Update $\to$ Ambulance Auto-Sync Flow
```
Admin edits Hospital #1 in Admin Portal
        ↓
PUT /api/admin/hospitals/1
        ↓
MySQL hospitals table updated
        ↓
Ambulance Dashboard auto-refreshes / opens destination selector
        ↓
GET /api/ambulance/hospitals/nearby or /api/ambulance/hospitals/1
        ↓
Ambulance UI & Leaflet Map Markers update with new coordinates & bed capacity
```

### B. Ambulance Suggest Hospital $\to$ Admin Approval Flow
```
Ambulance Driver clicks "+ Add / Suggest Hospital"
        ↓
Validates coordinates & checks for duplicate entries
        ↓
POST /api/ambulance/hospitals (approvalStatus='PENDING_REVIEW', isActive=0)
        ↓
MySQL hospitals table (pending review, excluded from active destinations)
        ↓
Admin views "Pending Review" tab in Hospital Management
        ↓
Admin reviews details and clicks "Approve" (POST /api/admin/hospitals/{id}/approve)
        ↓
MySQL updates (approvalStatus='ACTIVE', isActive=1)
        ↓
Hospital becomes immediately available for Ambulance Destination Selection!
```

---

## 3. Key Features Implemented

### 🏥 1. Single Source of Truth
- Both Admin and Ambulance operate on the same MySQL `hospitals` table.
- No duplicate or mock arrays exist.

### 🛡️ 2. Controlled Hospital Suggestions & Validation
- Added `[ ✚ Add / Suggest Hospital ]` modal in [`frontend/ambulance-dashboard.html`](file:///d:/MedCareX/frontend/ambulance-dashboard.html).
- Validates latitude (-90 to 90) and longitude (-180 to 180), required fields, and non-negative available beds.
- Includes `[ 📍 Use Current Location ]` helper button with driver verification warning.
- Implemented duplicate prevention check in [`backend/src/com/medicarex/repository/HospitalRepository.java`](file:///d:/MedCareX/backend/src/com/medicarex/repository/HospitalRepository.java) comparing normalized name + city and distance $\le 150\text{m}$. Returns HTTP 409 Conflict with warning.

### 📋 3. Admin Approval & Rejection Workflow
- Added `PENDING_REVIEW` and `REJECTED` filters and status badges to [`frontend/admin-hospitals.html`](file:///d:/MedCareX/frontend/admin-hospitals.html).
- Added `[ ✓ Approve ]` and `[ ✗ Reject ]` action buttons with instant status transitions.
- Displays submitter attribution (`Ambulance Driver` with user ID vs `Admin`).
- Rejections preserve audit records with rejection reasons in database.

### 🗺️ 4. Dynamic Leaflet Map Markers & Routing
- Leaflet map pins update dynamically when hospital coordinates or emergency availability are updated.
- Amber map pins represent hospitals under `PENDING REVIEW`.
- Green map pins represent `EMERGENCY READY` active trauma centers.

### 🔒 5. RBAC & IDOR Enforcement
- Authenticated JWT/session user extraction (cannot spoof ambulance or driver credentials in request body).
- Ambulance users cannot approve, reject, activate, or edit approved hospitals (HTTP 403 Forbidden).

---

## 4. Files Modified

| File | Changes Made |
| :--- | :--- |
| [`backend/src/com/medicarex/model/Hospital.java`](file:///d:/MedCareX/backend/src/com/medicarex/model/Hospital.java) | Added `approvalStatus`, `createdByUserId`, `createdByRole`, `rejectionReason`, and serialization in `toMap()`. |
| [`backend/src/com/medicarex/config/DatabaseConnection.java`](file:///d:/MedCareX/backend/src/com/medicarex/config/DatabaseConnection.java) | Added schema migrations for `approval_status`, `created_by_user_id`, `created_by_role`, `rejection_reason`. |
| [`backend/src/com/medicarex/repository/HospitalRepository.java`](file:///d:/MedCareX/backend/src/com/medicarex/repository/HospitalRepository.java) | Added duplicate detection, `approveHospital`, `rejectHospital`, approval status filtering, and updated stats. |
| [`backend/src/com/medicarex/controller/AdminController.java`](file:///d:/MedCareX/backend/src/com/medicarex/controller/AdminController.java) | Added `/api/admin/hospitals/{id}/approve` and `/api/admin/hospitals/{id}/reject` endpoints. |
| [`backend/src/com/medicarex/controller/EmergencyController.java`](file:///d:/MedCareX/backend/src/com/medicarex/controller/EmergencyController.java) | Added `POST /api/ambulance/hospitals` with role check, coordinate validation, duplicate detection, and `PENDING_REVIEW` creation. |
| [`frontend/js/api.js`](file:///d:/MedCareX/frontend/js/api.js) | Added `suggestHospital()`, `approveAdminHospital()`, and `rejectAdminHospital()` methods. |
| [`frontend/admin-hospitals.html`](file:///d:/MedCareX/frontend/admin-hospitals.html) | Added Pending Review/Rejected filters, KPI cards, creator badges, and Approve/Reject action buttons. |
| [`frontend/ambulance-dashboard.html`](file:///d:/MedCareX/frontend/ambulance-dashboard.html) | Added "+ Add / Suggest Hospital" button & modal, GPS assist, duplicate alert handling, and controlled 15s polling. |

---

## 5. Verification & Test Results

### 🧪 Two-Way Hospital Sync & Approval E2E Test (`scratch/test_hospital_two_way_sync.py`)

```text
==================================================
   TESTING TWO-WAY HOSPITAL MANAGEMENT WORKFLOW  
==================================================

[Step 1] Authenticating Admin & Ambulance Users...
[OK] Admin authenticated (ID: 4, Role: ADMIN)
[OK] Ambulance authenticated (ID: 46, Role: AMBULANCE)

[Step 2] Admin Updates Hospital Details (Single Source of Truth)...
Original Hospital #1: Manipal Hospital Trauma & Multi-Specialty | Beds: 24
[OK] Admin saved update: Hospital updated successfully

[Step 3] Ambulance retrieves updated Hospital #1...
Ambulance sees: Name='City Emergency Trauma Center - Main' | Beds=42 | Lat=12.975 | Lng=77.601
[PASS] Admin updates immediately reflected in Ambulance portal without restart!

[Step 4] Ambulance Suggests New Hospital (Pending Review)...
[OK] Ambulance submitted suggestion: ID #14 | Status=PENDING_REVIEW | Active=False
[PASS] Suggested hospital created with PENDING_REVIEW and inactive status.

[Step 5] Checking that Pending Hospital is excluded from Ambulance Destination Selector...
[PASS] Pending hospital is NOT available as an emergency destination before approval.

[Step 6] Testing Duplicate Hospital Prevention...
[PASS] Duplicate prevented with HTTP 409 Conflict ()

[Step 7] Admin Lists Pending Hospitals & Approves Suggestion...
[OK] Admin sees hospital in Pending Review filter: St. Jude Emergency Center - North (Created by: AMBULANCE)
[OK] Admin approved hospital: Hospital approved and activated successfully | Status=ACTIVE | Active=True

[Step 8] Ambulance Refreshes and Sees Approved Hospital in Active Destinations...
[PASS] Approved hospital now immediately available in Ambulance destination selector: 'St. Jude Emergency Center - North' (Beds: 18)

[Step 9] Testing Rejection Workflow...
[OK] Admin rejected hospital: Hospital rejected | Status=REJECTED | Reason=Inadequate emergency trauma facilities.
[PASS] Rejected hospital preserved in audit history but excluded from ambulance destinations.

[Step 10] Testing RBAC Security Restrictions...
[PASS] Ambulance user forbidden from approving hospitals (HTTP 403)

[Step 11] Testing Active Emergency Safety & Real-Time Sync...
[OK] Emergency created: ID #19
[OK] Ambulance accepted emergency #19
[OK] Destination hospital selected: City Emergency Trauma Center - Main
[OK] Admin updated Hospital #1 during active emergency: City Trauma Center (Emergency Wing)
[OK] Active emergency destination: ID=1 | Latest Data: City Trauma Center (Emergency Wing) | Beds: 50
[PASS] Active emergency remains intact with latest MySQL hospital data!

==================================================
   ALL 11 TWO-WAY HOSPITAL INTEGRATION TESTS PASSED! 
==================================================
```

### 🧪 System Regression Test (`scratch/test_ambulance_navigation.ps1`)
```text
============================================================
MEDICAREX - AMBULANCE EMERGENCY NAVIGATION AND WORKFLOW TEST
============================================================
[1] Patient Priya Logged In (ID: 1)
[2] Ambulance Driver Logged In (ID: 46)
[3] Emergency Request Created (ID: 20, Status: REQUESTED)
    Patient Location: 12.9784, 77.6408
    Pickup Address: 102, 100 Feet Road, Indiranagar, Bengaluru
[4] Driver Requests Retrieved (My Requests: 14, Pending Pool: 5)
[5] Dispatch Accepted: Emergency dispatch accepted
[6] Active Emergency Verified (ID: 20, Status: ACCEPTED)
[7] Emergency Location Endpoint PASSED (Patient GPS: 12.9784, 77.6408)
[8] Security IDOR Test PASSED (Unauthorized role correctly rejected with HTTP 403)
[9] Ambulance GPS Broadcast Updated: Location updated
    --> Stage Advanced: EN_ROUTE
    --> Stage Advanced: ARRIVED
    --> Stage Advanced: PATIENT_PICKED_UP
    --> Stage Advanced: TRANSPORTING
    --> Stage Advanced: ARRIVED_AT_HOSPITAL
    --> Stage Advanced: COMPLETED
[12] Emergency Trip ID #20 successfully recorded in Mission History!
============================================================
ALL AMBULANCE NAVIGATION AND LIFECYCLE TESTS PASSED 100%!
============================================================
```
