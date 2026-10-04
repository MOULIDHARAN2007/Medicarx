$pages = @(
    @{ Name = "MediCareX Root"; Url = "http://localhost:8080/" },
    @{ Name = "Login"; Url = "http://localhost:8080/login.html" },
    @{ Name = "Register"; Url = "http://localhost:8080/register.html" },
    @{ Name = "Forgot Password"; Url = "http://localhost:8080/forgot-password.html" },
    @{ Name = "Patient Dashboard"; Url = "http://localhost:8080/dashboard.html" },
    @{ Name = "Medicine Search"; Url = "http://localhost:8080/search.html" },
    @{ Name = "Medicine Details"; Url = "http://localhost:8080/medicine-details.html?id=1" },
    @{ Name = "Prescription"; Url = "http://localhost:8080/prescription.html" },
    @{ Name = "Health Records"; Url = "http://localhost:8080/records.html" },
    @{ Name = "Appointments"; Url = "http://localhost:8080/appointments.html" },
    @{ Name = "Notifications"; Url = "http://localhost:8080/notifications.html" },
    @{ Name = "AI Assistant"; Url = "http://localhost:8080/ai-assistant.html" },
    @{ Name = "Map"; Url = "http://localhost:8080/map.html" },
    @{ Name = "Profile"; Url = "http://localhost:8080/profile.html" },
    @{ Name = "Settings"; Url = "http://localhost:8080/settings.html" },
    @{ Name = "Doctor Dashboard"; Url = "http://localhost:8080/doctor-dashboard.html" },
    @{ Name = "Doctor Patients"; Url = "http://localhost:8080/doctor-patients.html" },
    @{ Name = "Doctor Appointments"; Url = "http://localhost:8080/doctor-appointments.html" },
    @{ Name = "Doctor Search"; Url = "http://localhost:8080/doctor-search.html" },
    @{ Name = "Pharmacy Dashboard"; Url = "http://localhost:8080/pharmacy-dashboard.html" },
    @{ Name = "Pharmacy Inventory"; Url = "http://localhost:8080/pharmacy-inventory.html" },
    @{ Name = "Pharmacy Medicines"; Url = "http://localhost:8080/pharmacy-medicines.html" },
    @{ Name = "Pharmacy Orders"; Url = "http://localhost:8080/pharmacy-orders.html" },
    @{ Name = "Admin Dashboard"; Url = "http://localhost:8080/admin-dashboard.html" },
    @{ Name = "Admin Users"; Url = "http://localhost:8080/admin-users.html" },
    @{ Name = "Admin Medicines"; Url = "http://localhost:8080/admin-medicines.html" },
    @{ Name = "Admin Pharmacies"; Url = "http://localhost:8080/admin-pharmacies.html" },
    @{ Name = "Admin Inventory"; Url = "http://localhost:8080/admin-inventory.html" }
)

$passed = 0
$failed = 0

foreach ($p in $pages) {
    try {
        $res = Invoke-WebRequest -Uri $p.Url -UseBasicParsing -TimeoutSec 5
        if ($res.StatusCode -eq 200) {
            Write-Host "[VERIFIED 200 OK] $($p.Name) -> $($p.Url) ($($res.RawContentLength) bytes)" -ForegroundColor Green
            $passed++
        } else {
            Write-Host "[NON-200 STATUS $($res.StatusCode)] $($p.Name) -> $($p.Url)" -ForegroundColor Yellow
            $failed++
        }
    } catch {
        Write-Host "[FAILED] $($p.Name) -> $($p.Url) : $($_.Exception.Message)" -ForegroundColor Red
        $failed++
    }
}

Write-Host "`nSummary: $passed PASSED, $failed FAILED"
