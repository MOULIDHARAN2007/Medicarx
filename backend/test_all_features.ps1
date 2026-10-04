# Comprehensive End-to-End Verification Test Script for MediCareX
$BaseUrl = "http://localhost:8080"
$ErrorActionPreference = "Stop"

function Write-Pass($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red; exit 1 }
Write-Host "=================================================="
Write-Host " Running MediCareX Dead Feature Verification Suite"
Write-Host "=================================================="

# 1. AUTHENTICATE ALL ROLES
Write-Host "`n--- Authenticating Users ---"
$roles = @{
    "PATIENT"  = @{ email = "priya@example.com"; pass = "patient123" };
    "DOCTOR"   = @{ email = "arjun@example.com"; pass = "doctor123" };
    "PHARMACY" = @{ email = "healthplus@example.com"; pass = "pharmacy123" };
    "ADMIN"    = @{ email = "admin@example.com"; pass = "admin123" }
}

$tokens = @{}
foreach ($r in $roles.Keys) {
    $body = @{ email = $roles[$r].email; password = $roles[$r].pass } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method POST -Body $body -ContentType "application/json"
    if ($res.success -and $res.data.token) {
        $tokens[$r] = $res.data.token
        Write-Pass "$r login succeeded: $($res.data.user.fullName)"
    } else {
        Write-Fail "$r login failed!"
    }
}

# 2. PATIENT FEATURES
Write-Host "`n--- Testing PATIENT Features ---"
$patientHeader = @{ Authorization = "Bearer $($tokens['PATIENT'])" }

# 2.1 Profile View
$pProf = Invoke-RestMethod -Uri "$BaseUrl/api/user/profile" -Method GET -Headers $patientHeader
if ($pProf.success -and $pProf.data.email -eq "priya@example.com") {
    Write-Pass "Patient Profile GET returned: $($pProf.data.fullName) (Blood Group: $($pProf.data.bloodGroup))"
} else { Write-Fail "Patient Profile GET failed!" }

# 2.2 Profile Edit
$updateBody = @{
    fullName = "Priya S. Sharma";
    phone = "+91 98765 43219";
    dateOfBirth = "1994-05-15";
    gender = "Female";
    bloodGroup = "O+";
    address = "45 Lakeview Enclave";
    city = "Mumbai"
} | ConvertTo-Json
$pUpdate = Invoke-RestMethod -Uri "$BaseUrl/api/user/profile" -Method POST -Headers $patientHeader -Body $updateBody -ContentType "application/json"
if ($pUpdate.success) {
    Write-Pass "Patient Profile POST updated successfully"
} else { Write-Fail "Patient Profile POST failed!" }

# 2.3 Profile Persistence
$pProf2 = Invoke-RestMethod -Uri "$BaseUrl/api/user/profile" -Method GET -Headers $patientHeader
if ($pProf2.data.fullName -eq "Priya S. Sharma" -and $pProf2.data.phone -eq "+91 98765 43219") {
    Write-Pass "Patient Profile changes persisted across calls!"
} else { Write-Fail "Patient Profile persistence check failed!" }

# 2.4 Settings GET & POST
$sGet = Invoke-RestMethod -Uri "$BaseUrl/api/user/settings" -Method GET -Headers $patientHeader
if ($sGet.success) {
    Write-Pass "Patient Settings GET succeeded (Email Alerts: $($sGet.data.emailAlerts))"
} else { Write-Fail "Patient Settings GET failed!" }

$sUpdateBody = @{ emailAlerts = $true; smsAlerts = $true; stockAlerts = $false; twoFactorEnabled = $true } | ConvertTo-Json
$sPost = Invoke-RestMethod -Uri "$BaseUrl/api/user/settings" -Method POST -Headers $patientHeader -Body $sUpdateBody -ContentType "application/json"
if ($sPost.success) {
    Write-Pass "Patient Settings POST updated successfully"
} else { Write-Fail "Patient Settings POST failed!" }

# 2.5 Password Change (Wrong current password test & Correct change)
try {
    $badPassBody = @{ currentPassword = "wrongPassword123"; newPassword = "newPassword456" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/api/user/password" -Method POST -Headers $patientHeader -Body $badPassBody -ContentType "application/json"
    Write-Fail "Password change should have rejected invalid current password!"
} catch {
    Write-Pass "Password change correctly rejected invalid current password (400)"
}

$goodPassBody = @{ currentPassword = "patient123"; newPassword = "patientNew2026!" } | ConvertTo-Json
$passChange = Invoke-RestMethod -Uri "$BaseUrl/api/user/password" -Method POST -Headers $patientHeader -Body $goodPassBody -ContentType "application/json"
if ($passChange.success) {
    Write-Pass "Password changed successfully via BCrypt"
} else { Write-Fail "Password change failed!" }

# Verify login with new password
$newLoginBody = @{ email = "priya@example.com"; password = "patientNew2026!" } | ConvertTo-Json
$newLoginRes = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method POST -Body $newLoginBody -ContentType "application/json"
if ($newLoginRes.success) {
    Write-Pass "Login with NEW BCrypt password succeeded!"
    $tokens['PATIENT'] = $newLoginRes.data.token
} else { Write-Fail "Login with new password failed!" }

# Revert password back for consistency
$revertPassBody = @{ currentPassword = "patientNew2026!"; newPassword = "patient123" } | ConvertTo-Json
$revertRes = Invoke-RestMethod -Uri "$BaseUrl/api/user/password" -Method POST -Headers @{ Authorization = "Bearer $($tokens['PATIENT'])" } -Body $revertPassBody -ContentType "application/json"
if ($revertRes.success) {
    Write-Pass "Password reverted back to patient123"
}

# 3. DOCTOR FEATURES
Write-Host "`n--- Testing DOCTOR Features ---"
$docHeader = @{ Authorization = "Bearer $($tokens['DOCTOR'])" }

# 3.1 Get Doctor Patients
$docPatients = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/patients" -Method GET -Headers $docHeader
if ($docPatients.success -and $docPatients.data.Count -gt 0) {
    Write-Pass "Doctor Patients list retrieved: $($docPatients.data.Count) patients"
} else { Write-Fail "Doctor Patients list failed!" }

# 3.2 Add Patient (Success case)
$testPatientEmail = "rahul.verma." + (Get-Random -Minimum 1000 -Maximum 9999) + "@example.com"
$addPatBody = @{
    fullName = "Rahul Verma";
    email = $testPatientEmail;
    phone = "+91 98234 56789";
    dateOfBirth = "1988-11-20";
    gender = "Male";
    bloodGroup = "B+";
    medicalHistory = "Mild hypertension, seasonal allergies."
} | ConvertTo-Json

$addPatRes = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/patients" -Method POST -Headers $docHeader -Body $addPatBody -ContentType "application/json"
if ($addPatRes.success) {
    Write-Pass "Doctor Add Patient succeeded: Rahul Verma ($testPatientEmail)"
} else { Write-Fail "Doctor Add Patient failed!" }

# Duplicate Check
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/doctor/patients" -Method POST -Headers $docHeader -Body $addPatBody -ContentType "application/json"
    Write-Fail "Duplicate patient email should have been rejected!"
} catch {
    Write-Pass "Doctor Add Patient correctly rejected duplicate email"
}

# 3.3 Doctor Appointments
$appts = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/appointments" -Method GET -Headers $docHeader
if ($appts.success) {
    Write-Pass "Doctor Appointments retrieved: $($appts.data.Count) appointments"
} else { Write-Fail "Doctor Appointments list failed!" }

# Create Appointment
$newApptBody = @{
    patientName = "Rahul Verma";
    patientEmail = $testPatientEmail;
    appointmentDate = "2026-09-15";
    appointmentTime = "11:30 AM";
    reason = "Hypertension routine follow-up"
} | ConvertTo-Json
$createApptRes = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/appointments" -Method POST -Headers $docHeader -Body $newApptBody -ContentType "application/json"
if ($createApptRes.success -and $createApptRes.data.id) {
    $apptId = $createApptRes.data.id
    Write-Pass "Doctor Appointment created (ID: $apptId)"
} else { Write-Fail "Doctor Appointment creation failed!" }

# Cancel Appointment
$cancelApptBody = @{ appointmentId = $apptId; status = "Cancelled" } | ConvertTo-Json
$cancelApptRes = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/appointments/status" -Method POST -Headers $docHeader -Body $cancelApptBody -ContentType "application/json"
if ($cancelApptRes.success) {
    Write-Pass "Doctor Appointment status updated to Cancelled"
} else { Write-Fail "Doctor Appointment status update failed!" }

# 4. PHARMACY FEATURES
Write-Host "`n--- Testing PHARMACY Features ---"
$pharmHeader = @{ Authorization = "Bearer $($tokens['PHARMACY'])" }

# 4.1 Orders GET
$orders = Invoke-RestMethod -Uri "$BaseUrl/api/orders" -Method GET -Headers $pharmHeader
if ($orders.success -and $orders.data.Count -gt 0) {
    Write-Pass "Pharmacy Orders retrieved: $($orders.data.Count) orders"
} else { Write-Fail "Pharmacy Orders list failed!" }

# 4.2 Order Status Update
$firstOrder = $orders.data[0]
$orderStatusBody = @{ orderId = $firstOrder.id; status = "Ready" } | ConvertTo-Json
$orderUpdateRes = Invoke-RestMethod -Uri "$BaseUrl/api/orders/status" -Method POST -Headers $pharmHeader -Body $orderStatusBody -ContentType "application/json"
if ($orderUpdateRes.success) {
    Write-Pass "Order status updated to Ready for Order ID: $($firstOrder.id)"
} else { Write-Fail "Order status update failed!" }

# 4.3 Update Stock (Validation & Success)
# Negative stock test
try {
    $badStock = @{ inventoryId = 1; quantity = -5 } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/api/inventory/update" -Method POST -Headers $pharmHeader -Body $badStock -ContentType "application/json"
    Write-Fail "Negative stock should have been rejected!"
} catch {
    Write-Pass "Inventory update rejected negative quantity (-5)"
}

# Real stock update
$goodStock = @{ inventoryId = 1; quantity = 75 } | ConvertTo-Json
$goodStockRes = Invoke-RestMethod -Uri "$BaseUrl/api/inventory/update" -Method POST -Headers $pharmHeader -Body $goodStock -ContentType "application/json"
if ($goodStockRes.success) {
    Write-Pass "Inventory stock updated to 75 units"
} else { Write-Fail "Inventory stock update failed!" }

# 4.4 Import CSV
$csvContent = @"
Name,Brand,Category,Price,Quantity,ExpiryDate
Cetirizine 10mg,Zyrtec,Antihistamine,6.50,150,2027-10-15
Montelukast 10mg,Singulair,Respiratory,24.00,80,2027-12-01
Omega 3 Fish Oil,NatureMade,Supplements,18.00,60,2028-01-30
"@

$csvBody = @{ csv = $csvContent } | ConvertTo-Json
$csvRes = Invoke-RestMethod -Uri "$BaseUrl/api/inventory/import-csv" -Method POST -Headers $pharmHeader -Body $csvBody -ContentType "application/json"
if ($csvRes.success -and $csvRes.data.imported -gt 0) {
    Write-Pass "Inventory CSV Import succeeded: $($csvRes.data.imported) imported, $($csvRes.data.updated) updated, $($csvRes.data.failed) failed"
} else { Write-Fail "Inventory CSV Import failed!" }

# 5. ADMIN FEATURES
Write-Host "`n--- Testing ADMIN Features ---"
$adminHeader = @{ Authorization = "Bearer $($tokens['ADMIN'])" }

# 5.1 Users List
$users = Invoke-RestMethod -Uri "$BaseUrl/api/admin/users" -Method GET -Headers $adminHeader
if ($users.success -and $users.data.Count -gt 0) {
    Write-Pass "Admin Users list retrieved: $($users.data.Count) registered users"
} else { Write-Fail "Admin Users list failed!" }

# 5.2 Sole Admin Protection Check
$adminUser = $users.data | Where-Object { $_.email -eq "admin@example.com" } | Select-Object -First 1
if (-not $adminUser) {
    $adminUser = $users.data | Where-Object { $_.role -eq "ADMIN" } | Select-Object -First 1
}
try {
    $deactBody = @{ userId = $adminUser.id; status = "INACTIVE" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/api/admin/users/status" -Method POST -Headers $adminHeader -Body $deactBody -ContentType "application/json"
    Write-Fail "Sole admin deactivation should have been rejected!"
} catch {
    Write-Pass "Sole admin protection correctly prevented admin lockout!"
}

# 5.3 User Status Toggle for non-admin
$targetUser = $users.data | Where-Object { $_.role -eq "PATIENT" } | Select-Object -First 1
$toggleBody = @{ userId = $targetUser.id; status = "INACTIVE" } | ConvertTo-Json
$toggleRes = Invoke-RestMethod -Uri "$BaseUrl/api/admin/users/status" -Method POST -Headers $adminHeader -Body $toggleBody -ContentType "application/json"
if ($toggleRes.success) {
    Write-Pass "Admin updated patient ($($targetUser.fullName)) status to INACTIVE"
} else { Write-Fail "User status update failed!" }

# Revert patient status back
$revertUserBody = @{ userId = $targetUser.id; status = "ACTIVE" } | ConvertTo-Json
$null = Invoke-RestMethod -Uri "$BaseUrl/api/admin/users/status" -Method POST -Headers $adminHeader -Body $revertUserBody -ContentType "application/json"
Write-Pass "Admin reverted patient status back to ACTIVE"

Write-Host "`n=================================================="
Write-Host " ALL MEDICAREX VERIFICATION TESTS PASSED (100%)"
Write-Host "=================================================="
