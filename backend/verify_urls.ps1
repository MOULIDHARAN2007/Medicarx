$urls = @(
    'http://localhost:8080/',
    'http://localhost:8080/dashboard.html',
    'http://localhost:8080/doctor-search.html',
    'http://localhost:8080/appointments.html',
    'http://localhost:8080/doctor-appointments.html',
    'http://localhost:8080/login.html'
)

foreach ($u in $urls) {
    try {
        $res = Invoke-WebRequest -Uri $u -UseBasicParsing -TimeoutSec 5
        Write-Host "[VERIFIED 200 OK] $u (Bytes: $($res.RawContentLength))" -ForegroundColor Green
    } catch {
        Write-Host "[FAILED] $u -> $($_.Exception.Message)" -ForegroundColor Red
    }
}
