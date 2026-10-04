$htmlFiles = Get-ChildItem -Path frontend -Filter *.html
Write-Host "Testing $($htmlFiles.Count) HTML pages on http://localhost:8080 ..."
$passCount = 0
$failCount = 0

foreach ($file in $htmlFiles) {
    $url = "http://localhost:8080/$($file.Name)"
    try {
        $res = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
        if ($res.StatusCode -eq 200) {
            Write-Host "[200 OK] $url" -ForegroundColor Green
            $passCount++
        } else {
            Write-Host "[$($res.StatusCode)] $url" -ForegroundColor Yellow
            $failCount++
        }
    } catch {
        Write-Host "[ERROR] $url -> $_" -ForegroundColor Red
        $failCount++
    }
}

Write-Host "=========================================="
Write-Host "Page Audit Results: $passCount Passed, $failCount Failed"
if ($failCount -gt 0) { exit 1 } else { exit 0 }
