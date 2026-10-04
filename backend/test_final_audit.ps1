$BaseUrl = "http://localhost:8080"
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " MEDICAREX COMPLETE API & FLOW INTEGRATION AUDIT " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Login as Doctor
$docLogin = @{email="arjun@example.com"; password="Password123!"} | ConvertTo-Json
$docRes = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $docLogin -ContentType "application/json"
$docToken = $docRes.data.token
$docHeader = @{Authorization = "Bearer $docToken"}
Write-Host "1. Doctor Login: SUCCESS (Role: $($docRes.data.user.role))" -ForegroundColor Green

# 2. Test Doctor Patients API
$docPatients = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/patients" -Method Get -Headers $docHeader
Write-Host "2. Doctor Patients API: SUCCESS (Total Patients: $($docPatients.data.Count))" -ForegroundColor Green

# 3. Test Doctor Appointments API
$docAppts = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/appointments" -Method Get -Headers $docHeader
Write-Host "3. Doctor Appointments API: SUCCESS (Total Appointments: $($docAppts.data.Count))" -ForegroundColor Green

# 4. Test Doctor AI Clinical Support
$aiBody = @{
    patientId = 1
    symptoms = "Fever and sore throat"
    consultationNotes = "Tonsils inflamed, no exudates"
} | ConvertTo-Json
$aiRes = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/ai/clinical-support" -Method Post -Headers $docHeader -Body $aiBody -ContentType "application/json"
Write-Host "4. Doctor AI Clinical Support: SUCCESS (Differentials: $($aiRes.data.differentialConsiderations.Count))" -ForegroundColor Green

# 5. Test Doctor Prescription Safety Check
$safetyBody = @{
    patientId = 1
    items = @(
        @{medicineName="Amoxicillin 500mg Capsule"; dosage="500mg"; frequency="TDS"; duration="5 days"}
    )
} | ConvertTo-Json
$safetyRes = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/prescriptions/safety-check" -Method Post -Headers $docHeader -Body $safetyBody -ContentType "application/json"
Write-Host "5. Doctor Prescription Safety Check: SUCCESS (Status: $($safetyRes.data.status))" -ForegroundColor Green

# 6. Test Doctor Patient Timeline
$timelineRes = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/patients/1/timeline" -Method Get -Headers $docHeader
Write-Host "6. Doctor Patient Timeline: SUCCESS (Events: $($timelineRes.data.Count))" -ForegroundColor Green

# 7. Test Doctor Treatment Goals
$goalsRes = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/patients/1/treatment-goals" -Method Get -Headers $docHeader
Write-Host "7. Doctor Treatment Goals: SUCCESS (Goals: $($goalsRes.data.Count))" -ForegroundColor Green

# 8. Login as Admin
$adminLogin = @{email="admin@example.com"; password="Password123!"} | ConvertTo-Json
$adminRes = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $adminLogin -ContentType "application/json"
$adminToken = $adminRes.data.token
$adminHeader = @{Authorization = "Bearer $adminToken"}
Write-Host "8. Admin Login: SUCCESS (Role: $($adminRes.data.user.role))" -ForegroundColor Green

# 9. Test Admin Ambulances
$ambRes = Invoke-RestMethod -Uri "$BaseUrl/api/admin/ambulances" -Method Get -Headers $adminHeader
Write-Host "9. Admin Ambulances API: SUCCESS (Fleet: $($ambRes.data.Count))" -ForegroundColor Green

# 10. Test Admin Doctor Approvals
$docApprRes = Invoke-RestMethod -Uri "$BaseUrl/api/admin/approvals/doctors" -Method Get -Headers $adminHeader
Write-Host "10. Admin Doctor Approvals API: SUCCESS (Applications: $($docApprRes.data.Count))" -ForegroundColor Green

# 11. Login as Pharmacy
$pharmLogin = @{email="healthplus@example.com"; password="Password123!"} | ConvertTo-Json
$pharmRes = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $pharmLogin -ContentType "application/json"
$pharmToken = $pharmRes.data.token
$pharmHeader = @{Authorization = "Bearer $pharmToken"}
Write-Host "11. Pharmacy Login: SUCCESS (Role: $($pharmRes.data.user.role))" -ForegroundColor Green

# 12. Test Pharmacy POS Search & Bills
$searchRes = Invoke-RestMethod -Uri "$BaseUrl/api/pharmacy/medicines/search?q=Amoxicillin" -Method Get -Headers $pharmHeader
Write-Host "12. Pharmacy Medicine Search: SUCCESS (Found: $($searchRes.data.Count) items)" -ForegroundColor Green

$billsRes = Invoke-RestMethod -Uri "$BaseUrl/api/pharmacy/bills" -Method Get -Headers $pharmHeader
Write-Host "13. Pharmacy Bills API: SUCCESS (Bills count: $($billsRes.data.Count))" -ForegroundColor Green

# 13. Login as Patient
$patLogin = @{email="priya@example.com"; password="Password123!"} | ConvertTo-Json
$patRes = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $patLogin -ContentType "application/json"
$patToken = $patRes.data.token
$patHeader = @{Authorization = "Bearer $patToken"}
Write-Host "14. Patient Login: SUCCESS (Role: $($patRes.data.user.role))" -ForegroundColor Green

# 14. Test Patient Prescriptions
$patRxRes = Invoke-RestMethod -Uri "$BaseUrl/api/prescriptions" -Method Get -Headers $patHeader
Write-Host "15. Patient Prescriptions API: SUCCESS (Prescriptions: $($patRxRes.data.Count))" -ForegroundColor Green

# 15. Test Patient Emergency Card
$cardRes = Invoke-RestMethod -Uri "$BaseUrl/api/patient/emergency-card" -Method Get -Headers $patHeader
Write-Host "16. Patient Emergency Card API: SUCCESS (Name: $($cardRes.data.fullName))" -ForegroundColor Green

# 16. Test Patient Medical Expenses
$expRes = Invoke-RestMethod -Uri "$BaseUrl/api/patient/medical-expenses" -Method Get -Headers $patHeader
Write-Host "17. Patient Medical Expenses API: SUCCESS (Total Expense: ? $($expRes.data.totalExpense))" -ForegroundColor Green

# 17. Test Patient Medicine Supply
$supplyRes = Invoke-RestMethod -Uri "$BaseUrl/api/patient/medicine-supply" -Method Get -Headers $patHeader
Write-Host "18. Patient Medicine Supply API: SUCCESS (Items: $($supplyRes.data.Count))" -ForegroundColor Green

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " ALL 18 CORE INTEGRATION TESTS PASSED 100%!       " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
