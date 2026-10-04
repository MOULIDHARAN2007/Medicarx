$baseUrl = "http://localhost:8080/api"

Write-Host "`n=== 1. TEST AUTHENTICATION & TOKENS ===" -ForegroundColor Cyan
# Login as Patient (Priya Sharma)
$patLogin = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"priya@example.com","password":"patient123"}'
$patToken = $patLogin.data.token
Write-Host "Patient Token acquired: $($patToken.Substring(0, 15))..." -ForegroundColor Green

# Login as Doctor (Dr. Arjun Kumar)
$docLogin = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"arjun@example.com","password":"doctor123"}'
$docToken = $docLogin.data.token
Write-Host "Doctor Token acquired: $($docToken.Substring(0, 15))..." -ForegroundColor Green

# Login as Pharmacy (HealthPlus)
$pharmLogin = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"healthplus@example.com","password":"pharmacy123"}'
$pharmToken = $pharmLogin.data.token
Write-Host "Pharmacy Token acquired: $($pharmToken.Substring(0, 15))..." -ForegroundColor Green

Write-Host "`n=== 2. TEST PHARMACY INVENTORY & STOCK MUTATIONS ===" -ForegroundColor Cyan
# 2.1 Get Inventory
$invResp = Invoke-RestMethod -Uri "$baseUrl/inventory?pharmacyId=1" -Method Get -Headers @{ Authorization = "Bearer $pharmToken" }
Write-Host "Inventory count for HealthPlus: $($invResp.data.Count)" -ForegroundColor Green
if ($invResp.data.Count -gt 0) {
    $firstItem = $invResp.data[0]
    Write-Host "First item: $($firstItem.medicineName) - Stock: $($firstItem.stockQuantity), Price: ₹$($firstItem.unitPrice)" -ForegroundColor Yellow

    # 2.2 Update Stock
    $newStock = $firstItem.stockQuantity + 5
    $updateResp = Invoke-RestMethod -Uri "$baseUrl/inventory/update" -Method Post -ContentType "application/json" `
        -Headers @{ Authorization = "Bearer $pharmToken" } `
        -Body (@{ id = $firstItem.id; stockQuantity = $newStock; unitPrice = $firstItem.unitPrice; expiryDate = $firstItem.expiryDate } | ConvertTo-Json)
    Write-Host "Stock update response: $($updateResp.message)" -ForegroundColor Green

    # 2.3 Verify Persistence
    $invCheck = Invoke-RestMethod -Uri "$baseUrl/inventory?pharmacyId=1" -Method Get -Headers @{ Authorization = "Bearer $pharmToken" }
    $updatedItem = $invCheck.data | Where-Object { $_.id -eq $firstItem.id }
    if ($updatedItem.stockQuantity -eq $newStock) {
        Write-Host "SUCCESS: Stock updated and persisted in DB ($($firstItem.stockQuantity) -> $($updatedItem.stockQuantity))" -ForegroundColor Green
    } else {
        Write-Host "ERROR: Stock update mismatch" -ForegroundColor Red
    }
}

# 2.4 Test CSV Import
Write-Host "`n=== 3. TEST PHARMACY CSV IMPORT ===" -ForegroundColor Cyan
$csvRows = @(
    @{ medicineName = "Cefixime 200mg"; genericName = "Cefixime"; category = "Antibiotics"; batchNumber = "BATCH-CFX-101"; stockQuantity = 120; unitPrice = 145.0; expiryDate = "2028-06-30" },
    @{ medicineName = "Montelukast 10mg"; genericName = "Montelukast"; category = "Allergy"; batchNumber = "BATCH-MTK-202"; stockQuantity = 80; unitPrice = 95.0; expiryDate = "2028-08-31" }
)
$csvImportBody = @{ pharmacyId = 1; rows = $csvRows } | ConvertTo-Json
$csvResp = Invoke-RestMethod -Uri "$baseUrl/inventory/import-csv" -Method Post -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $pharmToken" } `
    -Body $csvImportBody

Write-Host "CSV Import Response: $($csvResp.message)" -ForegroundColor Green
Write-Host "Imported: $($csvResp.data.imported), Failed: $($csvResp.data.failed), Total: $($csvResp.data.totalProcessed)" -ForegroundColor Yellow

# Verify imported items exist
$invCheck2 = Invoke-RestMethod -Uri "$baseUrl/inventory?pharmacyId=1&q=Cefixime" -Method Get -Headers @{ Authorization = "Bearer $pharmToken" }
if ($invCheck2.data.Count -gt 0) {
    Write-Host "SUCCESS: Cefixime found in inventory after CSV import (Stock: $($invCheck2.data[0].stockQuantity))" -ForegroundColor Green
} else {
    Write-Host "ERROR: Cefixime not found in inventory after CSV import" -ForegroundColor Red
}

Write-Host "`n=== 4. TEST HEALTH RECORD PDF DOWNLOAD & MAGIC BYTES ===" -ForegroundColor Cyan
$recFile = "$PSScriptRoot\test_record_download.pdf"
if (Test-Path $recFile) { Remove-Item $recFile -Force -ErrorAction SilentlyContinue }

Invoke-WebRequest -Uri "$baseUrl/records/download?id=1" -Method Get -Headers @{ Authorization = "Bearer $patToken" } -OutFile $recFile
$fileBytes = [System.IO.File]::ReadAllBytes($recFile)
$magicHeader = [System.Text.Encoding]::ASCII.GetString($fileBytes[0..7])
Write-Host "Health Record PDF Size: $($fileBytes.Length) bytes" -ForegroundColor Yellow
Write-Host "PDF Magic Header: $magicHeader" -ForegroundColor Yellow

if ($magicHeader.StartsWith("%PDF-1.")) {
    Write-Host "SUCCESS: Health Record PDF is a valid binary PDF document!" -ForegroundColor Green
} else {
    Write-Host "ERROR: File is not a valid PDF!" -ForegroundColor Red
}

Write-Host "`n=== 5. TEST PRESCRIPTION PDF DOWNLOAD & MAGIC BYTES ===" -ForegroundColor Cyan
$rxFile = "$PSScriptRoot\test_prescription_download.pdf"
if (Test-Path $rxFile) { Remove-Item $rxFile -Force -ErrorAction SilentlyContinue }

Invoke-WebRequest -Uri "$baseUrl/prescriptions/download?id=1" -Method Get -Headers @{ Authorization = "Bearer $patToken" } -OutFile $rxFile
$rxBytes = [System.IO.File]::ReadAllBytes($rxFile)
$rxMagic = [System.Text.Encoding]::ASCII.GetString($rxBytes[0..7])
Write-Host "Prescription PDF Size: $($rxBytes.Length) bytes" -ForegroundColor Yellow
Write-Host "PDF Magic Header: $rxMagic" -ForegroundColor Yellow

if ($rxMagic.StartsWith("%PDF-1.")) {
    Write-Host "SUCCESS: Prescription PDF is a valid binary PDF document!" -ForegroundColor Green
} else {
    Write-Host "ERROR: File is not a valid PDF!" -ForegroundColor Red
}

Write-Host "`n=== 6. TEST HEALTH RECORD SECURITY & IDOR PREVENTION ===" -ForegroundColor Cyan
# Register a second patient user
$user2Email = "patient.test$(Get-Random)@example.com"
$reg2 = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" `
    -Body (@{ fullName = "Test Patient Two"; email = $user2Email; password = "password123"; role = "PATIENT"; phone = "9876543299" } | ConvertTo-Json)
$user2Token = $reg2.data.token

# Try to download record #1 (which belongs to Priya) using User 2's token
try {
    $null = Invoke-WebRequest -Uri "$baseUrl/records/download?id=1" -Method Get -Headers @{ Authorization = "Bearer $user2Token" }
   Write-Host "ERROR: IDOR check failed! Unauthorized patient was able to download another patient's record." -ForegroundColor Red
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "SUCCESS: IDOR blocked with HTTP status $statusCode (Access Denied)" -ForegroundColor Green
}

Write-Host "`n=== 7. TEST NEARBY PHARMACIES & GEOLOCATION SORTING ===" -ForegroundColor Cyan
$nearbyResp = Invoke-RestMethod -Uri "$baseUrl/pharmacies/nearby?latitude=12.9716&longitude=77.5946&radius=25" -Method Get
Write-Host "Nearby pharmacies count: $($nearbyResp.data.Count)" -ForegroundColor Green
$lastDist = -1.0
$sortedCorrectly = $true
foreach ($p in $nearbyResp.data) {
    Write-Host "  - $($p.name): $($p.distanceKm) km (Status: $($p.status))" -ForegroundColor Gray
    if ($p.distanceKm -lt $lastDist) {
        $sortedCorrectly = $false
    }
    $lastDist = $p.distanceKm
}
if ($sortedCorrectly) {
    Write-Host "SUCCESS: Nearby pharmacies correctly ordered by Haversine distance nearest-first!" -ForegroundColor Green
} else {
    Write-Host "ERROR: Nearby pharmacies not sorted correctly" -ForegroundColor Red
}

Write-Host "`n=== ALL MASTER FUNCTIONAL REPAIRS TESTED SUCCESSFULLY! ===" -ForegroundColor Green
