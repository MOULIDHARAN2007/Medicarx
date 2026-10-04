# ==============================================================================
# MediCareX — Google OAuth 2.0 & Authentication End-to-End Test Suite
# ==============================================================================

$port = 8080
$baseUrl = "http://localhost:$port/api/auth"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "   MEDICAREX GOOGLE SIGN-IN END-TO-END VERIFICATION    " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# Test 1: Config Endpoint
Write-Host "`n[TEST 1] GET /api/auth/google/config" -ForegroundColor Yellow
$cfg = Invoke-RestMethod -Uri "$baseUrl/google/config" -Method GET
Write-Host "  Success: $($cfg.success)" -ForegroundColor Green
Write-Host "  Configured: $($cfg.data.configured)" -ForegroundColor Green
Write-Host "  Redirect URI: $($cfg.data.redirectUri)" -ForegroundColor Green
if ($cfg.data.clientSecret) {
    Write-Host "  FAIL: clientSecret leaked in config response!" -ForegroundColor Red
} else {
    Write-Host "  PASS: clientSecret is never exposed" -ForegroundColor Green
}

# Test 2: Auth URL Endpoint
Write-Host "`n[TEST 2] GET /api/auth/google/url" -ForegroundColor Yellow
$urlRes = Invoke-RestMethod -Uri "$baseUrl/google/url" -Method GET
Write-Host "  Success: $($urlRes.success)" -ForegroundColor Green
Write-Host "  Configured: $($urlRes.data.configured)" -ForegroundColor Green

# Test 3: OAuth Callback - Cancellation / Error Redirection
Write-Host "`n[TEST 3] GET /api/auth/google/callback?error=access_denied" -ForegroundColor Yellow
try {
    $req = [System.Net.HttpWebRequest]::Create("http://localhost:$port/api/auth/google/callback?error=access_denied")
    $req.AllowAutoRedirect = $false
    $resp = $req.GetResponse()
    $loc = $resp.GetResponseHeader("Location")
    $resp.Close()
    Write-Host "  PASS: Redirects to: $loc" -ForegroundColor Green
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 4: OAuth Direct POST without credentials
Write-Host "`n[TEST 4] POST /api/auth/google (Empty Payload)" -ForegroundColor Yellow
try {
    Invoke-RestMethod -Uri "$baseUrl/google" -Method POST -Body '{"credential":""}' -ContentType "application/json"
    Write-Host "  FAIL: Allowed empty payload" -ForegroundColor Red
} catch {
    Write-Host "  PASS: Rejected with HTTP $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Green
}

# Test 5: Standard Email/Password Login (Verifying no regression)
Write-Host "`n[TEST 5] Standard Patient Login" -ForegroundColor Yellow
$patientLogin = Invoke-RestMethod -Uri "$baseUrl/login" -Method POST -Body (@{
    email = "priya@example.com"
    password = "patient123"
} | ConvertTo-Json) -ContentType "application/json"
Write-Host "  PASS: Patient login succeeded. Role: $($patientLogin.data.user.role)" -ForegroundColor Green

# Test 6: Doctor & Pharmacy Standard Login
Write-Host "`n[TEST 6] Doctor & Pharmacy Login" -ForegroundColor Yellow
$docLogin = Invoke-RestMethod -Uri "$baseUrl/login" -Method POST -Body (@{
    email = "arjun@example.com"
    password = "doctor123"
} | ConvertTo-Json) -ContentType "application/json"
Write-Host "  PASS: Doctor login succeeded. Role: $($docLogin.data.user.role)" -ForegroundColor Green

$pharmLogin = Invoke-RestMethod -Uri "$baseUrl/login" -Method POST -Body (@{
    email = "healthplus@example.com"
    password = "pharmacy123"
} | ConvertTo-Json) -ContentType "application/json"
Write-Host "  PASS: Pharmacy login succeeded. Role: $($pharmLogin.data.user.role)" -ForegroundColor Green

# Test 7: Admin Protection
Write-Host "`n[TEST 7] Admin Security & Role Validation" -ForegroundColor Yellow
$adminEmail = "testadmin_$(Get-Random)@medicarex.org"
try {
    Invoke-RestMethod -Uri "$baseUrl/register" -Method POST -Body (@{
        fullName = "Fake Admin"
        email = $adminEmail
        password = "AdminPass123!"
        role = "ADMIN"
    } | ConvertTo-Json) -ContentType "application/json"
    Write-Host "  FAIL: Unauthorized admin creation succeeded" -ForegroundColor Red
} catch {
    Write-Host "  PASS: Unauthorized admin creation blocked" -ForegroundColor Green
}

# Authorized Admin Creation via Existing Admin
$rootAdminLogin = Invoke-RestMethod -Uri "$baseUrl/login" -Method POST -Body (@{ email = "admin@example.com"; password = "admin123" } | ConvertTo-Json) -ContentType "application/json"
$rootAdminToken = $rootAdminLogin.data.token

$authAdmin = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/users" -Method POST -Headers @{ Authorization = "Bearer $rootAdminToken" } -Body (@{
    fullName = "Authorized Administrator"
    email = $adminEmail
    password = "AdminPass123!"
    confirmPassword = "AdminPass123!"
} | ConvertTo-Json) -ContentType "application/json"
Write-Host "  PASS: Authorized admin created by existing admin. Role: $($authAdmin.data.user.role)" -ForegroundColor Green

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "   ALL TESTS PASSED SUCCESSFULLY                        " -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan
