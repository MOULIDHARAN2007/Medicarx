# ==============================================================================
# MediCareX — Comprehensive End-to-End Administrator Lifecycle Test Suite
# Tests all security constraints, role enforcement, and persistence
# ==============================================================================

$baseUrl = "http://localhost:8080"
$passCount = 0
$failCount = 0

function Assert-Condition($testName, $condition, $detail = "") {
    if ($condition) {
        Write-Host "[PASS] $testName" -ForegroundColor Green
        if ($detail) { Write-Host "       $detail" -ForegroundColor Gray }
        $global:passCount++
    } else {
        Write-Host "[FAIL] $testName" -ForegroundColor Red
        if ($detail) { Write-Host "       Detail: $detail" -ForegroundColor Yellow }
        $global:failCount++
    }
}

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Starting MediCareX Admin Security Verification  " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# ------------------------------------------------------------------------------
# TEST 1: Login as existing ACTIVE ADMIN
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 1: Existing Active Admin Login ---" -ForegroundColor Yellow
$adminLoginBody = @{
    email = "admin@example.com"
    password = "admin123"
} | ConvertTo-Json

try {
    $adminLoginRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json"
    $adminToken = $adminLoginRes.data.token
    $adminId = $adminLoginRes.data.user.id
    $adminRole = $adminLoginRes.data.user.role
    Assert-Condition "Admin login successful" ($adminToken -ne $null -and $adminRole -eq "ADMIN") "Logged in as Admin ID: $adminId"
} catch {
    Assert-Condition "Admin login failed" $false "$($_.Exception.Message)"
}

# ------------------------------------------------------------------------------
# TEST 2: Attempt Public Registration as ADMIN
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 2: Public Registration as ADMIN is Blocked ---" -ForegroundColor Yellow
$hackerRegBody = @{
    fullName = "Public Admin Attempter"
    email = "hacker_admin@example.com"
    phone = "+91 99999 88888"
    password = "password123"
    role = "ADMIN"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post -Body $hackerRegBody -ContentType "application/json"
    Assert-Condition "Public Admin Registration Should Be Blocked" $false "Server allowed public ADMIN creation!"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition "Public Admin Registration Rejected with 403" ($statusCode -eq 403) "Status: $statusCode (Correctly Forbidden)"
}

# ------------------------------------------------------------------------------
# TEST 3: Unauthenticated Request to Create Administrator
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 3: Unauthenticated Call to Create Admin API ---" -ForegroundColor Yellow
$newAdminPayload = @{
    fullName = "New Admin 1"
    email = "test_admin_01@medicarex.in"
    phone = "+91 91234 56789"
    password = "AdminSecurePass123!"
    confirmPassword = "AdminSecurePass123!"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/admin/users" -Method Post -Body $newAdminPayload -ContentType "application/json"
    Assert-Condition "Unauthenticated Create Admin Should Fail" $false "Server allowed unauthenticated access"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition "Unauthenticated Request Rejected with 401" ($statusCode -eq 401) "Status: $statusCode (Correctly Unauthorized)"
}

# ------------------------------------------------------------------------------
# TEST 4, 5, 6: Non-Admin Roles Calling Create Administrator
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 4: PATIENT Token Calling Create Admin API ---" -ForegroundColor Yellow
$patientLoginBody = @{ email = "priya@example.com"; password = "patient123" } | ConvertTo-Json
$patientToken = (Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $patientLoginBody -ContentType "application/json").data.token

try {
    $headers = @{ Authorization = "Bearer $patientToken" }
    $res = Invoke-RestMethod -Uri "$baseUrl/api/admin/users" -Method Post -Body $newAdminPayload -Headers $headers -ContentType "application/json"
    Assert-Condition "Patient Should Not Create Admin" $false "Patient created admin!"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition "Patient Rejected with 403 Forbidden" ($statusCode -eq 403) "Status: $statusCode"
}

Write-Host "`n--- TEST 5: DOCTOR Token Calling Create Admin API ---" -ForegroundColor Yellow
$doctorLoginBody = @{ email = "arjun@example.com"; password = "doctor123" } | ConvertTo-Json
$doctorToken = (Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $doctorLoginBody -ContentType "application/json").data.token

try {
    $headers = @{ Authorization = "Bearer $doctorToken" }
    $res = Invoke-RestMethod -Uri "$baseUrl/api/admin/users" -Method Post -Body $newAdminPayload -Headers $headers -ContentType "application/json"
    Assert-Condition "Doctor Should Not Create Admin" $false "Doctor created admin!"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition "Doctor Rejected with 403 Forbidden" ($statusCode -eq 403) "Status: $statusCode"
}

Write-Host "`n--- TEST 6: PHARMACY Token Calling Create Admin API ---" -ForegroundColor Yellow
$pharmLoginBody = @{ email = "healthplus@example.com"; password = "pharmacy123" } | ConvertTo-Json
$pharmToken = (Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $pharmLoginBody -ContentType "application/json").data.token

try {
    $headers = @{ Authorization = "Bearer $pharmToken" }
    $res = Invoke-RestMethod -Uri "$baseUrl/api/admin/users" -Method Post -Body $newAdminPayload -Headers $headers -ContentType "application/json"
    Assert-Condition "Pharmacy Should Not Create Admin" $false "Pharmacy created admin!"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition "Pharmacy Rejected with 403 Forbidden" ($statusCode -eq 403) "Status: $statusCode"
}

# ------------------------------------------------------------------------------
# TEST 7: Validation Failures (Mismatched Passwords, Invalid Email)
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 7: Form Validation Failures ---" -ForegroundColor Yellow
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

# 7a. Mismatched passwords
$mismatchPayload = @{
    fullName = "Test Admin"
    email = "valid.email@example.com"
    password = "password123"
    confirmPassword = "different_password"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/admin/users" -Method Post -Body $mismatchPayload -Headers $adminHeaders -ContentType "application/json"
    Assert-Condition "Mismatched Passwords Rejected" $false "Allowed mismatch"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition "Mismatched Passwords Rejected with 400" ($statusCode -eq 400) "Status: $statusCode"
}

# 7b. Invalid email format
$badEmailPayload = @{
    fullName = "Test Admin"
    email = "invalid-email-string"
    password = "password123"
    confirmPassword = "password123"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/admin/users" -Method Post -Body $badEmailPayload -Headers $adminHeaders -ContentType "application/json"
    Assert-Condition "Invalid Email Rejected" $false "Allowed invalid email"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition "Invalid Email Rejected with 400" ($statusCode -eq 400) "Status: $statusCode"
}

# ------------------------------------------------------------------------------
# TEST 8: Existing Admin Successfully Creates New Administrator
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 8: Existing Admin Creates New Administrator ---" -ForegroundColor Yellow
$uniqueEmail = "super.admin.$(Get-Random)@medicarex.in"
$newAdminPass = "SuperSecAdmin#2026"
$validAdminPayload = @{
    fullName = "Vikram Aditya (Senior Director)"
    email = $uniqueEmail
    phone = "+91 98450 99887"
    password = $newAdminPass
    confirmPassword = $newAdminPass
} | ConvertTo-Json

try {
    $createRes = Invoke-RestMethod -Uri "$baseUrl/api/admin/users" -Method Post -Body $validAdminPayload -Headers $adminHeaders -ContentType "application/json"
    $createdUser = $createRes.data.user
    $newAdminId = $createdUser.id
    
    $checkRole = $createdUser.role -eq "ADMIN"
    $checkActive = $createdUser.isActive -eq $true
    $checkEmail = $createdUser.email -eq $uniqueEmail.ToLower()
    $checkCreatedBy = $createdUser.createdBy -eq $adminId
    $checkNoPassword = $createdUser.password -eq $null -and $createdUser.passwordHash -eq $null
    
    Assert-Condition "Admin Created via API with role=ADMIN" $checkRole "Role: $($createdUser.role)"
    Assert-Condition "Admin Account Status is ACTIVE" $checkActive "isActive: $($createdUser.isActive)"
    Assert-Condition "Admin createdBy is Set to Existing Admin ID" $checkCreatedBy "CreatedBy: $($createdUser.createdBy), Parent Admin: $adminId"
    Assert-Condition "Password & Hash are Not Exposed in Response" $checkNoPassword "Secure response payload"
} catch {
    Assert-Condition "Create Administrator Request Succeeded" $false "$($_.Exception.Message)"
}

# ------------------------------------------------------------------------------
# TEST 9: Duplicate Email Rejection
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 9: Duplicate Email Rejection ---" -ForegroundColor Yellow
try {
    $dupRes = Invoke-RestMethod -Uri "$baseUrl/api/admin/users" -Method Post -Body $validAdminPayload -Headers $adminHeaders -ContentType "application/json"
    Assert-Condition "Duplicate Email Must Fail" $false "Server accepted duplicate email"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition "Duplicate Email Rejected with 400 Bad Request" ($statusCode -eq 400) "Status: $statusCode"
}

# ------------------------------------------------------------------------------
# TEST 10: New Admin Logs In Normally
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 10: New Administrator Sign In ---" -ForegroundColor Yellow
$newAdminLoginBody = @{
    email = $uniqueEmail
    password = $newAdminPass
} | ConvertTo-Json

try {
    $newAdminLoginRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $newAdminLoginBody -ContentType "application/json"
    $newAdminToken = $newAdminLoginRes.data.token
    $newAdminUserData = $newAdminLoginRes.data.user
    
    Assert-Condition "New Admin Login Succeeded" ($newAdminToken -ne $null) "JWT Generated Successfully"
    Assert-Condition "New Admin Role is ADMIN" ($newAdminUserData.role -eq "ADMIN") "Role: $($newAdminUserData.role)"
} catch {
    Assert-Condition "New Admin Login Succeeded" $false "$($_.Exception.Message)"
}

# ------------------------------------------------------------------------------
# TEST 11: New Admin Accesses Admin APIs
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 11: New Admin Accesses Protected Admin APIs ---" -ForegroundColor Yellow
$newAdminHeaders = @{ Authorization = "Bearer $newAdminToken" }

try {
    $usersListRes = Invoke-RestMethod -Uri "$baseUrl/api/admin/users" -Method Get -Headers $newAdminHeaders
    $statsRes = Invoke-RestMethod -Uri "$baseUrl/api/admin/stats" -Method Get -Headers $newAdminHeaders
    
    $usersCount = $usersListRes.data.Count
    $statsOk = $statsRes.data.totalUsers -gt 0
    Assert-Condition "New Admin Fetched Users List from Database" ($usersCount -gt 0) "Found $usersCount users"
    Assert-Condition "New Admin Fetched Stats from Database" $statsOk "Total Users: $($statsRes.data.totalUsers)"
} catch {
    Assert-Condition "New Admin Accessed Admin APIs" $false "$($_.Exception.Message)"
}

# ------------------------------------------------------------------------------
# TEST 12: Admin Self-Deactivation Prevention
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 12: Admin Self-Deactivation Prevention ---" -ForegroundColor Yellow
$selfDeactivateBody = @{
    userId = $adminId
    isActive = $false
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/admin/users/status" -Method Post -Body $selfDeactivateBody -Headers $adminHeaders -ContentType "application/json"
    Assert-Condition "Self-Deactivation Blocked" $false "Server allowed admin to deactivate self"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition "Self-Deactivation Rejected with 400" ($statusCode -eq 400) "Status: $statusCode (Protected Self)"
}

# ------------------------------------------------------------------------------
# TEST 13: Deactivate New Admin and Verify Access Block
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 13: Deactivate New Admin & Verify Enforcement ---" -ForegroundColor Yellow
$deactivateBody = @{
    userId = $newAdminId
    isActive = $false
} | ConvertTo-Json

try {
    $deactRes = Invoke-RestMethod -Uri "$baseUrl/api/admin/users/status" -Method Post -Body $deactivateBody -Headers $adminHeaders -ContentType "application/json"
    Assert-Condition "Admin Deactivated Successfully" ($deactRes.data.isActive -eq $false) "Status: Inactive"
} catch {
    Assert-Condition "Deactivation Failed" $false "$($_.Exception.Message)"
}

# 13a. Deactivated admin attempts login
try {
    $failLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $newAdminLoginBody -ContentType "application/json"
    Assert-Condition "Deactivated Admin Login Blocked" $false "Allowed deactivated admin to log in"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition "Deactivated Admin Login Rejected with 403" ($statusCode -eq 403) "Status: $statusCode (Account Inactive)"
}

# 13b. Deactivated admin token attempts admin API call
try {
    $failApi = Invoke-RestMethod -Uri "$baseUrl/api/admin/users" -Method Get -Headers $newAdminHeaders
    Assert-Condition "Deactivated Admin API Access Blocked" $false "Allowed deactivated token to access API"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition "Deactivated Admin API Rejected with 403" ($statusCode -eq 403) "Status: $statusCode (Forbidden Inactive)"
}

# ------------------------------------------------------------------------------
# TEST 14: Reactivate Administrator
# ------------------------------------------------------------------------------
Write-Host "`n--- TEST 14: Reactivate Administrator ---" -ForegroundColor Yellow
$reactivateBody = @{
    userId = $newAdminId
    isActive = $true
} | ConvertTo-Json

try {
    $reactRes = Invoke-RestMethod -Uri "$baseUrl/api/admin/users/status" -Method Post -Body $reactivateBody -Headers $adminHeaders -ContentType "application/json"
    Assert-Condition "Admin Reactivated Successfully" ($reactRes.data.isActive -eq $true) "Status: Active"
    
    # Login again
    $reactLoginRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $newAdminLoginBody -ContentType "application/json"
    Assert-Condition "Reactivated Admin Can Log In Again" ($reactLoginRes.data.token -ne $null) "Login succeeded after reactivation"
} catch {
    Assert-Condition "Reactivation Failed" $false "$($_.Exception.Message)"
}

# ------------------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------------------
Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "                TEST SUMMARY                     " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "Total Passed: $passCount" -ForegroundColor Green
Write-Host "Total Failed: $failCount" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })

if ($failCount -eq 0) {
    Write-Host "`nALL ADMINISTRATOR MANAGEMENT TESTS PASSED PERFECTLY!" -ForegroundColor Green
} else {
    Write-Host "`nSOME TESTS FAILED. PLEASE REVIEW DETAILS ABOVE." -ForegroundColor Red
}
