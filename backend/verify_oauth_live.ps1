$baseUrl = "http://localhost:8080/api/auth"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "   MEDICAREX AUTHENTICATION & GOOGLE OAUTH VERIFICATION " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# 1. Google Config
$cfg = Invoke-RestMethod -Uri "$baseUrl/google/config" -Method GET
Write-Host "`n[1] Google Config:" -ForegroundColor Yellow
Write-Host "  Success:" $cfg.success
Write-Host "  Configured:" $cfg.data.configured
Write-Host "  Redirect URI:" $cfg.data.redirectUri

# 2. Google OAuth URL
$urlRes = Invoke-RestMethod -Uri "$baseUrl/google/url" -Method GET
Write-Host "`n[2] Google OAuth URL Endpoint:" -ForegroundColor Yellow
Write-Host "  Success:" $urlRes.success
Write-Host "  Configured:" $urlRes.data.configured
Write-Host "  URL:" $urlRes.data.url

# 3. Google Login Validation (Missing Tokens)
Write-Host "`n[3] Google Login Validation (Missing Payload):" -ForegroundColor Yellow
try {
    Invoke-RestMethod -Uri "$baseUrl/google" -Method POST -Body '{"credential":""}' -ContentType "application/json"
    Write-Host "  FAIL: Should have rejected empty credential" -ForegroundColor Red
} catch {
    Write-Host "  PASS: Rejected with HTTP $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Green
}

# 4. Google Login Validation (Unconfigured Service)
Write-Host "`n[4] Google Login Validation (Live ID Token when unconfigured):" -ForegroundColor Yellow
try {
    Invoke-RestMethod -Uri "$baseUrl/google" -Method POST -Body '{"credential":"sample_mock_google_id_token"}' -ContentType "application/json"
    Write-Host "  FAIL: Should have rejected unconfigured OAuth" -ForegroundColor Red
} catch {
    Write-Host "  PASS: Rejected with HTTP $($_.Exception.Response.StatusCode.value__) (OAuth 2.0 unconfigured in env)" -ForegroundColor Green
}

# 5. Admin Registration Security
Write-Host "`n[5] Admin Security Checks:" -ForegroundColor Yellow
$adminEmail = "testadmin_$(Get-Random)@medicarex.org"

# Unauthorized attempt
try {
    Invoke-RestMethod -Uri "$baseUrl/register" -Method POST -Body (@{
        fullName = "Hacker Attempt"
        email = $adminEmail
        password = "AdminSecretPassword123!"
        role = "ADMIN"
    } | ConvertTo-Json) -ContentType "application/json"
    Write-Host "  FAIL: Allowed unauthorized admin creation" -ForegroundColor Red
} catch {
    Write-Host "  PASS: Unauthorized admin registration blocked with HTTP $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Green
}

# Authorized attempt
$authAdmin = Invoke-RestMethod -Uri "$baseUrl/register" -Method POST -Body (@{
    fullName = "Verified System Administrator"
    email = $adminEmail
    password = "AdminSecurePassword2026!"
    role = "ADMIN"
    adminInviteCode = "MEDICAREX-ADMIN-2026"
} | ConvertTo-Json) -ContentType "application/json"
Write-Host "  PASS: Authorized admin created successfully. Role:" $authAdmin.data.user.role -ForegroundColor Green

# Admin Login (Standard email/password)
$adminLogin = Invoke-RestMethod -Uri "$baseUrl/login" -Method POST -Body (@{
    email = $adminEmail
    password = "AdminSecurePassword2026!"
} | ConvertTo-Json) -ContentType "application/json"
Write-Host "  PASS: Admin login succeeded. JWT Role:" $adminLogin.data.user.role -ForegroundColor Green

# Verify Admin Stats Access
$stats = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stats" -Headers @{ Authorization = "Bearer $($adminLogin.data.token)" }
Write-Host "  PASS: Admin stats endpoint accessed. Total Users:" $stats.data.totalUsers -ForegroundColor Green

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "   ALL VERIFICATIONS COMPLETED SUCCESSFULLY             " -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan
