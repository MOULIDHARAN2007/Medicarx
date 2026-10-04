$pages = @(
  'http://localhost:8080/doctor-dashboard.html',
  'http://localhost:8080/doctor-patients.html',
  'http://localhost:8080/doctor-patient-overview.html',
  'http://localhost:8080/doctor-ai-support.html',
  'http://localhost:8080/doctor-prescription-safety.html',
  'http://localhost:8080/doctor-patient-timeline.html',
  'http://localhost:8080/doctor-treatment-progress.html',
  'http://localhost:8080/doctor-appointments.html',
  'http://localhost:8080/doctor-medicine-search.html',
  'http://localhost:8080/prescription.html',
  'http://localhost:8080/records.html'
)

foreach ($url in $pages) {
    try {
        $res = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
        Write-Host "$url -> Status: $($res.StatusCode)"
    } catch {
        Write-Host "$url -> Error: $_"
    }
}
