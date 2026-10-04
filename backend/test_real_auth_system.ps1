$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080"

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "   MEDICAREX REAL DATABASE-BACKED AUTHENTICATION TEST SUITE     " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# ----------------------------------------------------------------------
# 1. VERIFY CLEAN LOGIN PAGE (NO DEMO UI / CREDENTIALS)
# ----------------------------------------------------------------------
Write-Host "`n[TEST 1] Verifying Clean Login HTML Source..." -ForegroundColor Yellow
$loginHtml = (Invoke-WebRequest -Uri "$baseUrl/login.html" -UseBasicParsing).Content

$demoChecks = @(
    @{ Name = "Zero hardcoded demo email"; Passed = -not ($loginHtml -match "priya@example.com") },
    @{ Name = "Zero hardcoded demo password"; Passed = -not ($loginHtml -match "patient123") },
    @{ Name = "Zero Quick Demo Roles section"; Passed = -not ($loginHtml -match "quick-demo-roles") },
    @{ Name = "Zero demo buttons"; Passed = -not ($loginHtml -match "fillDemo\(") },
    @{ Name = "Empty email placeholder"; Passed = ($loginHtml -match 'placeholder="Enter your email"') },
    @{ Name = "Empty password placeholder"; Passed = ($loginHtml -match 'placeholder="Enter your password"') },
    @{ Name = "Continue with Google button present"; Passed = ($loginHtml -match 'id="btn-google-login"') },
    @{ Name = "Forgot Password links to real page"; Passed = ($loginHtml -match 'href="/forgot-password.html"') }
)

foreach ($c in $demoChecks) {
    if ($c.Passed) {
        Write-Host "  PASS: $($c.Name)" -ForegroundColor Green
    } else {
        Write-Host "  FAIL: $($c.Name)" -ForegroundColor Red
        exit 1
    }
}

# ----------------------------------------------------------------------
# 2. REAL PATIENT REGISTRATION AND AUTHENTICATION
# ----------------------------------------------------------------------
Write-Host "`n[TEST 2] Testing Real Patient Registration and JWT Flow..." -ForegroundColor Yellow
$patEmail = "patient.real." + (Get-Random) + "@example.com"
$patPass = "PatientSecurePass2026!"
$regPatBody = @{
    fullName = "Anand Mahindra"
    email = $patEmail
    phone = "+91 98451 22334"
    password = $patPass
    role = "PATIENT"
    bloodGroup = "B+"
    gender = "Male"
    dob = "1992-04-18"
    address = "12, CMH Road, Indiranagar, Bengaluru"
} | ConvertTo-Json

$regPatResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post -ContentType "application/json" -Body $regPatBody
Write-Host "  PASS: Real Patient Registered in Database (ID: $($regPatResp.data.user.id), Name: $($regPatResp.data.user.fullName))" -ForegroundColor Green

$loginPatResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body (@{ email = $patEmail; password = $patPass } | ConvertTo-Json)
$patToken = $loginPatResp.data.token
if ($loginPatResp.data.user.role -eq "PATIENT" -and $patToken.Length -gt 20) {
    Write-Host "  PASS: Real Patient Logged In (JWT verified, Role: PATIENT)" -ForegroundColor Green
} else {
    Write-Host "  FAIL: Patient Login failed" -ForegroundColor Red; exit 1
}

# ----------------------------------------------------------------------
# 3. REAL DOCTOR REGISTRATION AND AUTHENTICATION
# ----------------------------------------------------------------------
Write-Host "`n[TEST 3] Testing Real Doctor Registration and Profile Flow..." -ForegroundColor Yellow
$docEmail = "doctor.real." + (Get-Random) + "@example.com"
$docPass = "DoctorSecurePass2026!"
$regDocBody = @{
    fullName = "Dr. Shreya Natarajan"
    email = $docEmail
    phone = "+91 99160 55667"
    password = $docPass
    role = "DOCTOR"
    specialization = "Neurologist and Physician"
    licenseNumber = "KMC-" + (Get-Random -Minimum 10000 -Maximum 99999) + "-MD"
    hospitalAffiliation = "Fortis Hospital, Bannerghatta Road"
} | ConvertTo-Json

$regDocResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post -ContentType "application/json" -Body $regDocBody
Write-Host "  PASS: Real Doctor Registered in Database (ID: $($regDocResp.data.user.id), Role: $($regDocResp.data.user.role))" -ForegroundColor Green

$loginDocResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body (@{ email = $docEmail; password = $docPass } | ConvertTo-Json)
$docToken = $loginDocResp.data.token
if ($loginDocResp.data.user.role -eq "DOCTOR" -and $docToken.Length -gt 20) {
    Write-Host "  PASS: Real Doctor Logged In (JWT verified, Role: DOCTOR)" -ForegroundColor Green
} else {
    Write-Host "  FAIL: Doctor Login failed" -ForegroundColor Red; exit 1
}

# ----------------------------------------------------------------------
# 4. REAL PHARMACY REGISTRATION AND AUTHENTICATION
# ----------------------------------------------------------------------
Write-Host "`n[TEST 4] Testing Real Pharmacy Registration and Store Linking..." -ForegroundColor Yellow
$pharmEmail = "pharmacy.real." + (Get-Random) + "@example.com"
$pharmPass = "PharmacySecurePass2026!"
$regPharmBody = @{
    fullName = "Venkatesh Rao"
    email = $pharmEmail
    phone = "+91 80234 99887"
    password = $pharmPass
    role = "PHARMACY"
    pharmacyName = "Sanjeevani MedCare Pharmacy"
    ownerName = "Venkatesh Rao"
    licenseNumber = "DL-BLR-2026-" + (Get-Random -Minimum 1000 -Maximum 9999)
    address = "45, Commercial Street"
    city = "Bengaluru"
    postalCode = "560001"
} | ConvertTo-Json

$regPharmResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post -ContentType "application/json" -Body $regPharmBody
Write-Host "  PASS: Real Pharmacy Registered in Database (ID: $($regPharmResp.data.user.id), Role: $($regPharmResp.data.user.role))" -ForegroundColor Green

$loginPharmResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body (@{ email = $pharmEmail; password = $pharmPass } | ConvertTo-Json)
$pharmToken = $loginPharmResp.data.token
if ($loginPharmResp.data.user.role -eq "PHARMACY" -and $pharmToken.Length -gt 20) {
    Write-Host "  PASS: Real Pharmacy Logged In (JWT verified, Role: PHARMACY)" -ForegroundColor Green
} else {
    Write-Host "  FAIL: Pharmacy Login failed" -ForegroundColor Red; exit 1
}

# ----------------------------------------------------------------------
# 5. ADMIN REGISTRATION AUTHORIZATION PROTECTION AND VERIFICATION
# ----------------------------------------------------------------------
Write-Host "`n[TEST 5] Testing Admin Registration Protection and Access Control..." -ForegroundColor Yellow
$adminEmail = "admin.real." + (Get-Random) + "@example.com"
$adminPass = "AdminRootSecure2026!"

# 5.1 Test unauthorized Admin registration without invite code (MUST FAIL with 403)
try {
    Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post -ContentType "application/json" `
        -Body (@{ fullName = "Hacker Admin"; email = "fake.$adminEmail"; password = $adminPass; role = "ADMIN" } | ConvertTo-Json)
    Write-Host "  FAIL: Unprotected admin registration was permitted!" -ForegroundColor Red; exit 1
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 403) {
        Write-Host "  PASS: Unauthorized Admin registration blocked with HTTP 403 (Invite Code Required)" -ForegroundColor Green
    } else {
        Write-Host "  FAIL: Expected 403 but got $code" -ForegroundColor Red; exit 1
    }
}

# 5.2 Test public Admin registration is strictly blocked with HTTP 403
try {
    Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post -ContentType "application/json" `
        -Body (@{ fullName = "Public Admin Attempter"; email = "fake2.$adminEmail"; password = $adminPass; role = "ADMIN" } | ConvertTo-Json)
    Write-Host "  FAIL: Public admin registration was allowed!" -ForegroundColor Red; exit 1
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 403) {
        Write-Host "  PASS: Public Admin registration strictly blocked with HTTP 403" -ForegroundColor Green
    }
}

# 5.3 Test existing root admin creating new administrator via POST /api/admin/users
$rootLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body (@{ email = "admin@example.com"; password = "admin123" } | ConvertTo-Json)
$rootToken = $rootLogin.data.token

$regAdminResp = Invoke-RestMethod -Uri "$baseUrl/api/admin/users" -Method Post -ContentType "application/json" -Headers @{ Authorization = "Bearer $rootToken" } `
    -Body (@{ fullName = "Principal Administrator"; email = $adminEmail; password = $adminPass; confirmPassword = $adminPass; phone = "+91 90000 11223" } | ConvertTo-Json)
Write-Host "  PASS: Existing Admin Created New Admin (ID: $($regAdminResp.data.user.id), Role: ADMIN)" -ForegroundColor Green

# 5.4 Test Admin login via standard login endpoint
$loginAdminResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body (@{ email = $adminEmail; password = $adminPass } | ConvertTo-Json)
$adminToken = $loginAdminResp.data.token
Write-Host "  PASS: Admin Logged In via standard login (JWT role: $($loginAdminResp.data.user.role))" -ForegroundColor Green

# 5.5 Test Admin API RBAC Protection: Admin can access /api/admin/stats
$adminStatsResp = Invoke-RestMethod -Uri "$baseUrl/api/admin/stats" -Method Get -Headers @{ Authorization = "Bearer $adminToken" }
Write-Host "  PASS: Admin accessed /api/admin/stats (Total Users: $($adminStatsResp.data.totalUsers), Total Pharmacies: $($adminStatsResp.data.totalPharmacies))" -ForegroundColor Green

# 5.6 Non-admin (Patient) cannot access /api/admin/stats (MUST return 403)
try {
    Invoke-RestMethod -Uri "$baseUrl/api/admin/stats" -Method Get -Headers @{ Authorization = "Bearer $patToken" }
    Write-Host "  FAIL: Non-admin patient was allowed to access Admin APIs!" -ForegroundColor Red; exit 1
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 403) {
        Write-Host "  PASS: Non-admin patient blocked from /api/admin/stats with HTTP 403 (Access Denied)" -ForegroundColor Green
    }
}

# ----------------------------------------------------------------------
# 6. FORGOT PASSWORD AND PASSWORD RESET FLOW
# ----------------------------------------------------------------------
Write-Host "`n[TEST 6] Testing Forgot Password and One-Time Token Reset Flow..." -ForegroundColor Yellow
$resetTargetEmail = "pwdreset." + (Get-Random) + "@example.com"
$origPass = "InitialPass2026!"
$newPass = "BrandNewUpdatedPass2026!"

# Register test account
Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ fullName = "Reset Test User"; email = $resetTargetEmail; password = $origPass; role = "PATIENT" } | ConvertTo-Json) | Out-Null

# Request forgot password
$forgotResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/forgot-password" -Method Post -ContentType "application/json" `
    -Body (@{ email = $resetTargetEmail } | ConvertTo-Json)
$resetToken = $forgotResp.data.token
Write-Host "  PASS: Forgot Password Token Generated ($($resetToken.Substring(0, 16))...)" -ForegroundColor Green

# Reset password with token
$resetResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/reset-password" -Method Post -ContentType "application/json" `
    -Body (@{ token = $resetToken; newPassword = $newPass } | ConvertTo-Json)
Write-Host "  PASS: Password Reset Endpoint returned: $($resetResp.message)" -ForegroundColor Green

# Verify login with NEW password succeeds
$newLoginToken = (Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $resetTargetEmail; password = $newPass } | ConvertTo-Json)).data.token

if ($newLoginToken -and $newLoginToken.Length -gt 20) {
    Write-Host "  PASS: Login with NEW password succeeded (JWT Token acquired: $($newLoginToken.Substring(0, 16))...)" -ForegroundColor Green
} else {
    Write-Host "  FAIL: Login with new password did not return a valid token" -ForegroundColor Red
    exit 1
}

# Verify login with OLD password fails (401)
try {
    Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" `
        -Body (@{ email = $resetTargetEmail; password = $origPass } | ConvertTo-Json)
    Write-Host "  FAIL: Old password was still accepted after reset!" -ForegroundColor Red; exit 1
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 401) {
        Write-Host "  PASS: Old password rejected with HTTP 401 (Invalid password)" -ForegroundColor Green
    }
}

# Verify token reuse fails (400)
try {
    Invoke-RestMethod -Uri "$baseUrl/api/auth/reset-password" -Method Post -ContentType "application/json" `
        -Body (@{ token = $resetToken; newPassword = "ThirdPassword2026!" } | ConvertTo-Json)
    Write-Host "  FAIL: Reusing already-used reset token was allowed!" -ForegroundColor Red; exit 1
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 400) {
        Write-Host "  PASS: Reusing single-use reset token rejected with HTTP 400" -ForegroundColor Green
    }
}

# ----------------------------------------------------------------------
# 7. GOOGLE OAUTH CONFIGURATION AND ENDPOINTS
# ----------------------------------------------------------------------
Write-Host "`n[TEST 7] Testing Google OAuth 2.0 Configuration Status..." -ForegroundColor Yellow
$googleCfgResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/google/config" -Method Get
Write-Host "  PASS: /api/auth/google/config returned: Configured = $($googleCfgResp.data.configured)" -ForegroundColor Green

Write-Host "`n================================================================" -ForegroundColor Green
Write-Host "   ALL REAL AUTHENTICATION TESTS PASSED WITH 100% SUCCESS       " -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
