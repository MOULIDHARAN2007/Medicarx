$baseUrl = "https://designer-lie-constant-indicator.trycloudflare.com"
$urls = @(
  "/",
  "/login.html",
  "/register.html",
  "/css/style.css",
  "/js/api.js",
  "/api/medicines",
  "/dashboard.html",
  "/doctor-dashboard.html",
  "/pharmacy-dashboard.html",
  "/ambulance-dashboard.html",
  "/admin-dashboard.html",
  "/emergency-center.html",
  "/patient-emergency-card.html",
  "/patient-data-sharing.html"
)

Write-Host "=================================================="
Write-Host "   MEDICAREX PUBLIC TUNNEL ENDPOINT VERIFICATION  "
Write-Host "=================================================="
Write-Host "Testing Public URL: $baseUrl"

$pass = 0
$fail = 0

foreach ($u in $urls) {
  try {
    $fullUrl = $baseUrl + $u
    $res = Invoke-WebRequest -Uri $fullUrl -UseBasicParsing -TimeoutSec 15
    Write-Host "[PASS] $u -> HTTP $($res.StatusCode) (Size: $($res.Content.Length) bytes)"
    $pass++
  } catch {
    Write-Host "[FAIL] $u -> $($_.Exception.Message)"
    $fail++
  }
}

Write-Host "`nSummary: $pass PASSED, $fail FAILED"
