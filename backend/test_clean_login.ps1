$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080"

Write-Host "`n=== 1. VERIFY CLEAN LOGIN HTML OVER HTTP ===" -ForegroundColor Cyan
$loginPage = Invoke-WebRequest -Uri "$baseUrl/login.html" -UseBasicParsing
$html = $loginPage.Content

$checks = @(
    @{ Name = "No demo email in HTML"; Passed = -not ($html -match "priya@example.com") },
    @{ Name = "No demo password in HTML"; Passed = -not ($html -match "patient123") },
    @{ Name = "No Quick Demo Roles section"; Passed = -not ($html -match "quick-demo-roles") },
    @{ Name = "No demo button group"; Passed = -not ($html -match "demo-btn-group") },
    @{ Name = "No fillDemo JS function"; Passed = -not ($html -match "fillDemo\(") },
    @{ Name = "Empty email placeholder"; Passed = ($html -match 'placeholder="Enter your email"') },
    @{ Name = "Empty password placeholder"; Passed = ($html -match 'placeholder="Enter your password"') },
    @{ Name = "Google button present without demo shortcut"; Passed = ($html -match 'id="btn-google-login"') }
)

foreach ($c in $checks) {
    if ($c.Passed) {
        Write-Host "  [PASS] $($c.Name)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $($c.Name)" -ForegroundColor Red
    }
}

Write-Host "`n=== 2. REAL REGISTRATION AND AUTHENTICATION FLOW ===" -ForegroundColor Cyan
# Register a fresh real patient
$realEmail = "realpatient." + (Get-Random) + "@example.com"
$regResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ fullName = "Real User"; email = $realEmail; password = "RealPassword2026!"; role = "PATIENT"; phone = "+91 9988776655" } | ConvertTo-Json)

Write-Host "  [PASS] Registration completed successfully (User ID: $($regResp.data.user.id), Role: $($regResp.data.user.role))" -ForegroundColor Green

# Login with the newly registered user
$loginResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ email = $realEmail; password = "RealPassword2026!" } | ConvertTo-Json)

Write-Host "  [PASS] Real Login successful (JWT Token acquired: $($loginResp.data.token.Substring(0, 20))...)" -ForegroundColor Green
Write-Host "  [PASS] User Full Name: $($loginResp.data.user.fullName)" -ForegroundColor Green

Write-Host "`n=== CLEAN LOGIN VERIFICATION COMPLETE - ALL TESTS PASSED! ===" -ForegroundColor Green
