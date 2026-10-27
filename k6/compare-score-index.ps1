# Runs sequential read-only queries against the local DB; requires the invisible candidate index.
$ErrorActionPreference = 'Stop'
$outDir = Join-Path $PSScriptRoot ('results/score-index-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$null = New-Item -ItemType Directory -Force $outDir
$until = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$cases = @(
    @{Name='broad'; Keyword='a'; Offset=0},
    @{Name='rare'; Keyword='Baldur'; Offset=0},
    @{Name='none'; Keyword='zzzz_no_match_20261027'; Offset=0},
    @{Name='deep'; Keyword='a'; Offset=2000}
)
$results = @()
foreach ($case in $cases) {
    $where = "release_date >= '1970-01-01' AND release_date < '$until' AND LOWER(title) LIKE LOWER('%$($case.Keyword)%') ESCAPE '!'"
    $query = "SELECT g.* FROM (SELECT id, igdb_rating FROM games WHERE $where ORDER BY COALESCE(igdb_rating, -1.0) DESC, id ASC LIMIT 20 OFFSET $($case.Offset)) matched JOIN games g ON g.id=matched.id ORDER BY COALESCE(g.igdb_rating, -1.0) DESC, g.id ASC"
    $count = "SELECT COUNT(*) FROM games WHERE $where"
    $beforeRows = $null
    foreach ($mode in @('off', 'on')) {
        $prefix = "SET SESSION optimizer_switch='use_invisible_indexes=$mode';"
        $rows = "$prefix $query; $count;" | docker exec -i gamelog-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -D gamelog_retry --batch --raw --skip-column-names'
        if ($LASTEXITCODE) { throw 'Query failed' }
        $serialized = $rows -join "`n"
        if ($mode -eq 'off') { $beforeRows = $serialized }
        elseif ($beforeRows -cne $serialized) { throw "Result mismatch: $($case.Name)" }
        foreach ($round in 0..3) {
            foreach ($kind in @('list','count')) {
                $statement = if ($kind -eq 'list') { $query } else { $count }
                $plan = "$prefix EXPLAIN ANALYZE $statement;" | docker exec -i gamelog-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -D gamelog_retry --batch --raw --skip-column-names'
                if ($LASTEXITCODE) { throw 'Explain failed' }
                $plan | Set-Content -Encoding utf8 (Join-Path $outDir "$($case.Name)-$mode-$kind-$round.txt")
                if ($round -gt 0) {
                    $match = [regex]::Match(($plan | Select-Object -First 1), 'actual time=[0-9.]+\.\.([0-9.]+)')
                    if (!$match.Success) { throw 'Timing not found' }
                    $results += [pscustomobject]@{Case=$case.Name; Mode=$mode; Kind=$kind; Round=$round; Ms=[double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)}
                }
            }
        }
    }
    Write-Output "Identical rows, order and count: $($case.Name)"
}
$results | Export-Csv -NoTypeInformation -Encoding utf8 (Join-Path $outDir 'timings.csv')
$results | Group-Object Case,Mode,Kind | ForEach-Object {
    $times = @($_.Group.Ms | Sort-Object)
    Write-Output "$($_.Name): median $($times[1]) ms"
}
Write-Output "Plans and timings: $outDir"
