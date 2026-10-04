$files = Get-ChildItem -Path "$PSScriptRoot\src" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
javac -Xlint:all -cp "$PSScriptRoot\lib\*" -d "$PSScriptRoot\bin" $files
if ($LASTEXITCODE -eq 0) {
    Write-Host "Java Build Successful! Compiled $($files.Count) classes with 0 errors." -ForegroundColor Green
} else {
    Write-Host "Java Build Failed!" -ForegroundColor Red
}
