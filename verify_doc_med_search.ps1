$pages = @(
  "/doctor-medicine-search.html",
  "/doctor-search.html",
  "/search.html",
  "/appointments.html",
  "/doctor-appointments.html",
  "/doctor-dashboard.html",
  "/medicine-search.html"
)

Write-Host "=================================================="
Write-Host "   MEDICAREX DOCTOR MEDICINE SEARCH VERIFICATION   "
Write-Host "=================================================="

foreach ($p in $pages) {
  try {
    $res = Invoke-WebRequest -Uri ("http://localhost:8080" + $p) -UseBasicParsing -TimeoutSec 10
    Write-Host "[PASS] $p -> HTTP $($res.StatusCode) ($($res.Content.Length) bytes)"
  } catch {
    Write-Host "[FAIL] $p -> $($_.Exception.Message)"
  }
}
