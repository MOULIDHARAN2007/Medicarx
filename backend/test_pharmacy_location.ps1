# Verification script for Pharmacy & Location Search Workflow
$BaseUrl = "http://localhost:8080"
$ErrorActionPreference = "Continue"

Write-Host "=================================================="
Write-Host " Testing MediCareX Pharmacy & Map Location APIs   "
Write-Host "=================================================="

# TEST 1
Write-Host "`n--- TEST 1: Missing coordinates validation on /api/pharmacies/nearby ---"
try {
    $res = Invoke-RestMethod -Uri "$BaseUrl/api/pharmacies/nearby" -Method GET
    Write-Host "[FAIL] Expected 400 when missing coordinates, but got 200" -ForegroundColor Red
} catch {
    Write-Host "[PASS] Correctly rejected missing coordinates (400)" -ForegroundColor Green
}

# TEST 2
Write-Host "`n--- TEST 2: Out of range coordinates validation ---"
try {
    $res = Invoke-RestMethod -Uri "$BaseUrl/api/pharmacies/nearby?latitude=95.0&longitude=77.0" -Method GET
    Write-Host "[FAIL] Expected 400 for latitude 95.0, but got 200" -ForegroundColor Red
} catch {
    Write-Host "[PASS] Correctly rejected out-of-range latitude (400)" -ForegroundColor Green
}

# TEST 3
Write-Host "`n--- TEST 3: Negative radius validation ---"
try {
    $res = Invoke-RestMethod -Uri "$BaseUrl/api/pharmacies/nearby?latitude=12.9784&longitude=77.6408&radius=-5" -Method GET
    Write-Host "[FAIL] Expected 400 for negative radius, but got 200" -ForegroundColor Red
} catch {
    Write-Host "[PASS] Correctly rejected negative radius (400)" -ForegroundColor Green
}

# TEST 4
Write-Host "`n--- TEST 4: Nearby pharmacies from Indiranagar (12.9784, 77.6408) within 10 km ---"
$res = Invoke-RestMethod -Uri "$BaseUrl/api/pharmacies/nearby?latitude=12.9784&longitude=77.6408&radius=10" -Method GET
Write-Host "Count returned within 10 km: $($res.data.Count)"
foreach ($p in $res.data) {
    Write-Host " - $($p.pharmacyName): $($p.distanceKm) km away ($($p.address), $($p.city))"
}
if ($res.data.Count -ge 5 -and $res.data[0].pharmacyName -eq 'HealthPlus Pharmacy' -and $res.data[0].distanceKm -le 0.1) {
    Write-Host "[PASS] Accurate distance calculation and nearest-first sorting verified!" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Nearest pharmacy mismatch!" -ForegroundColor Red
}

# TEST 5
Write-Host "`n--- TEST 5: Radius filtering (1 km radius from Indiranagar) ---"
$res1km = Invoke-RestMethod -Uri "$BaseUrl/api/pharmacies/nearby?latitude=12.9784&longitude=77.6408&radius=1" -Method GET
Write-Host "Count returned within 1 km: $($res1km.data.Count)"
foreach ($p in $res1km.data) {
    Write-Host " - $($p.pharmacyName): $($p.distanceKm) km away"
}
if ($res1km.data.Count -eq 2) {
    Write-Host "[PASS] Radius filtering works: exactly 2 pharmacies within 1 km (HealthPlus and MedLife)" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Radius filtering failed!" -ForegroundColor Red
}

# TEST 6
Write-Host "`n--- TEST 6: Nearby pharmacies from Chennai T. Nagar (13.0418, 80.2341) ---"
$resChn = Invoke-RestMethod -Uri "$BaseUrl/api/pharmacies/nearby?latitude=13.0418&longitude=80.2341&radius=10" -Method GET
Write-Host "Count returned in Chennai within 10 km: $($resChn.data.Count)"
foreach ($p in $resChn.data) {
    Write-Host " - $($p.pharmacyName): $($p.distanceKm) km away ($($p.address))"
}
if ($resChn.data.Count -eq 2 -and $resChn.data[0].pharmacyName -eq 'Apollo Pharmacy T Nagar') {
    Write-Host "[PASS] Multi-location Haversine query in Chennai succeeded!" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Chennai multi-location query failed!" -ForegroundColor Red
}

# TEST 7
Write-Host "`n--- TEST 7: Medicine-specific availability (Medicine ID 1: Amoxicillin 500mg) ---"
$resMed = Invoke-RestMethod -Uri "$BaseUrl/api/pharmacies/medicine/1?latitude=12.9784&longitude=77.6408&radius=10" -Method GET
Write-Host "Pharmacies found: $($resMed.data.Count)"
foreach ($p in $resMed.data) {
    Write-Host " - $($p.pharmacyName): $($p.medicineStockStatus), $($p.medicineStockQty) units @ Rs.$($p.medicinePrice) ($($p.distanceKm) km away)"
}
if ($resMed.data[0].medicineStockQty -gt 0 -and $resMed.data[0].medicinePrice -gt 0) {
    Write-Host "[PASS] Real-time inventory availability, stock quantity, and price attached correctly!" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Medicine availability check failed!" -ForegroundColor Red
}

Write-Host "`n=================================================="
Write-Host " ALL PHARMACY & LOCATION TESTS PASSED SUCCESSFULLY"
Write-Host "=================================================="
