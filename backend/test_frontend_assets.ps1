$urls = @(
    'http://localhost:8080/map.html',
    'http://localhost:8080/css/variables.css',
    'http://localhost:8080/css/base.css',
    'http://localhost:8080/css/components.css',
    'http://localhost:8080/css/layout.css',
    'http://localhost:8080/css/pages/patient.css',
    'http://localhost:8080/js/api.js',
    'http://localhost:8080/js/components/toast.js',
    'http://localhost:8080/js/components/shell.js'
)

foreach ($u in $urls) {
    $res = Invoke-WebRequest -Uri $u -UseBasicParsing
    Write-Host "$($res.StatusCode) OK - $u"
}
