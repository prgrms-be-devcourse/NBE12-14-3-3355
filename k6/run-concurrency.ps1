param(
    [ValidateRange(1, 1000)][int]$Vus = 20,
    [ValidatePattern('^([0-9]+(ms|s|m|h))+$')][string]$Duration = '2m',
    [ValidateSet('list', 'suggest')][string]$Kind = 'list',
    [AllowEmptyString()][string]$Keyword = 'a',
    [ValidateRange(0, 2147483647)][int]$Page = 0,
    [ValidateRange(1, 100)][int]$Size = 20,
    [ValidateRange(0, 3600)][double]$ThinkTime = 0,
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$PrometheusUrl = 'http://localhost:9090/api/v1/write'
)
$ErrorActionPreference = 'Stop'
$null = Get-Command k6 -ErrorAction Stop
$testId = 'concurrency-' + (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfffZ') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 6)
$resultDir = Join-Path $PSScriptRoot 'results'
$null = New-Item -ItemType Directory -Force -Path $resultDir
$settings = @{
    K6_PROMETHEUS_RW_SERVER_URL = $PrometheusUrl
    K6_PROMETHEUS_RW_TREND_STATS = 'avg,p(95)'
    K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM = 'false'
    K6_PROMETHEUS_RW_STALE_MARKERS = 'false'
    SEARCH_VUS = [string]$Vus
    SEARCH_DURATION = $Duration
    SEARCH_KIND = $Kind
    KEYWORD = $Keyword
    PAGE = [string]$Page
    SIZE = [string]$Size
    THINK_TIME = $ThinkTime.ToString([Globalization.CultureInfo]::InvariantCulture)
    BASE_URL = $BaseUrl
}
$previous = @{}
foreach ($key in $settings.Keys) {
    $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
# Save the workload separately from the k6 aggregate summary for reproducibility.
@{ testid = $testId; vus = $Vus; duration = $Duration; kind = $Kind; keyword = $Keyword;
    page = $Page; size = $Size; thinkTime = $ThinkTime; baseUrl = $BaseUrl } |
    ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $resultDir "$testId.config.json")
Write-Host "Grafana: http://localhost:3001/d/gamelog-k6-concurrency?var-testid=$testId"
Write-Host "testid: $testId | $Vus users | $Duration | $Kind | think time: $ThinkTime seconds"
$runExitCode = 1
try {
    foreach ($key in $settings.Keys) {
        [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process')
    }
    # Explicit -e also preserves an empty keyword on Windows PowerShell.
    & k6 run -o experimental-prometheus-rw --system-tags=name `
        --tag "testid=$testId" --tag 'mode=concurrency' --tag "target_vus=$Vus" `
        -e "KEYWORD=$Keyword" `
        --summary-export (Join-Path $resultDir "$testId.json") `
        (Join-Path $PSScriptRoot 'search-concurrency.js')
    $runExitCode = $LASTEXITCODE
} finally {
    foreach ($key in $settings.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process')
    }
}
Write-Host "Results: $resultDir\$testId.json (exit code: $runExitCode)"
exit $runExitCode
