$roles = @(
    @{email="admin@example.com"; password="Password123!"; role="Admin"},
    @{email="arjun@example.com"; password="Password123!"; role="Doctor"},
    @{email="healthplus@example.com"; password="Password123!"; role="Pharmacy"},
    @{email="priya@example.com"; password="Password123!"; role="Patient"}
)

foreach ($u in $roles) {
    $json = @{email=$u.email; password=$u.password} | ConvertTo-Json
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $json -ContentType "application/json"
        Write-Host "[$($u.role)] $($u.email) -> SUCCESS (Role: $($res.data.user.role), Token length: $($res.data.token.Length))" -ForegroundColor Green
    } catch {
        Write-Host "[$($u.role)] $($u.email) -> FAILED: $($_.Exception.Message)" -ForegroundColor Red
    }
}
