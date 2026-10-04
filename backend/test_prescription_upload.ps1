$BaseUrl = "http://localhost:8080"
$ErrorActionPreference = "Stop"

Write-Host "=================================================="
Write-Host " Testing Prescription Upload & Extraction APIs   "
Write-Host "=================================================="

# 1. Login Patient
$loginBody = @{ email = "priya@example.com"; password = "patient123" } | ConvertTo-Json
$loginRes = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method POST -Body $loginBody -ContentType "application/json"
$patToken = $loginRes.data.token
$patHeader = @{ Authorization = "Bearer $patToken" }
Write-Host "[PASS] Patient authenticated."

# 2. Prepare Sample PDF Prescription
$samplePdfBytes = [System.Text.Encoding]::ASCII.GetBytes("%PDF-1.4`nRx: Amoxicillin 500mg - 1 capsule TDS for 5 days`nParacetamol 650mg - 1 tablet SOS`n%%EOF")
$base64Data = [System.Convert]::ToBase64String($samplePdfBytes)

# 3. Upload Prescription
$uploadBody = @{
    fileName = "prescription_dr_arjun.pdf";
    fileType = "application/pdf";
    fileData = $base64Data
} | ConvertTo-Json

$uploadRes = Invoke-RestMethod -Uri "$BaseUrl/api/prescriptions/upload" -Method POST -Headers $patHeader -Body $uploadBody -ContentType "application/json"
if ($uploadRes.success -and $uploadRes.data.id) {
    $uploadId = $uploadRes.data.id
    Write-Host "[PASS] Prescription uploaded successfully! Upload ID: $uploadId"
    Write-Host "       Extracted medicines count: $($uploadRes.data.extractedMedicines.Count)"
} else {
    Write-Error "Prescription upload failed!"
}

# 4. Get Upload Details
$getRes = Invoke-RestMethod -Uri "$BaseUrl/api/prescriptions/uploads/$uploadId" -Method GET -Headers $patHeader
if ($getRes.success -and $getRes.data.id -eq $uploadId) {
    Write-Host "[PASS] Upload details retrieved for ID $uploadId"
} else {
    Write-Error "Failed to retrieve upload details!"
}

# 5. List Patient Uploads
$listRes = Invoke-RestMethod -Uri "$BaseUrl/api/prescriptions/uploads" -Method GET -Headers $patHeader
if ($listRes.success -and $listRes.data.Count -gt 0) {
    Write-Host "[PASS] Patient prescription uploads list retrieved ($($listRes.data.Count) uploads)"
} else {
    Write-Error "Failed to list uploads!"
}

# 6. Multi-Medicine Pharmacy Search
$searchBody = @{
    medicineIds = @(1, 2);
    latitude = 12.9784;
    longitude = 77.6408;
    radius = 25.0
} | ConvertTo-Json

$searchRes = Invoke-RestMethod -Uri "$BaseUrl/api/pharmacies/search-medicines" -Method POST -Headers $patHeader -Body $searchBody -ContentType "application/json"
if ($searchRes.success -and $searchRes.data.Count -gt 0) {
    $firstPharm = $searchRes.data[0]
    Write-Host "[PASS] Multi-medicine pharmacy availability search returned $($searchRes.data.Count) pharmacies."
    Write-Host "       Top Pharmacy: $($firstPharm.pharmacyName) (Distance: $($firstPharm.distanceKm) km, All Available: $($firstPharm.allMedicinesAvailable))"
} else {
    Write-Error "Multi-medicine pharmacy search failed!"
}

Write-Host "`n=================================================="
Write-Host " ALL PRESCRIPTION UPLOAD & SEARCH TESTS PASSED!   "
Write-Host "=================================================="
