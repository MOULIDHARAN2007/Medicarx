$loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body '{"email":"priya@example.com","password":"patient123"}' -ContentType "application/json"
Write-Host "Login response:" ($loginRes | ConvertTo-Json -Depth 3)
$tok = $loginRes.data.token

try {
    $profRes = Invoke-RestMethod -Uri "http://localhost:8080/api/user/profile" -Method GET -Headers @{ Authorization = "Bearer $tok" }
    Write-Host "Profile response:" ($profRes | ConvertTo-Json -Depth 3)
} catch {
    Write-Host "Profile error: " $_.Exception.ToString()
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        Write-Host "Error Body: " $reader.ReadToEnd()
    }
}
