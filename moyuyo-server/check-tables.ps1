# 检查 Entity 中声明的表是否在 migration 脚本里创建
$Root = 'D:\MOYUYOWPC\moyuyo-server'
$OutFile = "$Root\table-check.txt"
Remove-Item $OutFile -ErrorAction SilentlyContinue

Add-Content -Path $OutFile -Value ('DEBUG Root=[' + $Root + ']')

# 1) 收集 Entity 表名
$entityFiles = Get-ChildItem -Path (Join-Path $Root 'moyuyo-dao\src\main\java\com\moyuyo\dao') -Recurse -Filter *.java |
    Where-Object { $_.FullName -like '*\entity\*' }
$entityTables = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($f in $entityFiles) {
    $content = Get-Content $f.FullName -Raw
    if ($content -match '@TableName\("([a-zA-Z_][a-zA-Z0-9_]*)"\)') {
        [void]$entityTables.Add($Matches[1])
    }
}

# 2) 收集 migration 中 CREATE 的表名
$migDir = Join-Path $Root 'moyuyo-api/src/main/resources/db/migration'
Add-Content -Path $OutFile -Value ('DEBUG migDir=[' + $migDir + ']')
$migFiles = Get-ChildItem -Path $migDir -Filter V*.sql
Add-Content -Path $OutFile -Value ('DEBUG migFiles count=' + $migFiles.Count)
$migTables = New-Object 'System.Collections.Generic.HashSet[string]'
$opt = [System.Text.RegularExpressions.RegexOptions]::Singleline
$pattern = 'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`"\s]?(mo_[a-zA-Z0-9_]+)'
foreach ($f in $migFiles) {
    $content = Get-Content $f.FullName -Raw
    $matches2 = [System.Text.RegularExpressions.Regex]::Matches($content, $pattern, $opt)
    foreach ($m in $matches2) {
        [void]$migTables.Add($m.Groups[1].Value)
    }
}

Add-Content -Path $OutFile -Value ('==== Entity tables (' + $entityTables.Count + ' total) ====')
foreach ($t in ($entityTables | Sort-Object)) { Add-Content -Path $OutFile -Value ('  ' + $t) }
Add-Content -Path $OutFile -Value ''
Add-Content -Path $OutFile -Value ('==== Migration tables (' + $migTables.Count + ' total) ====')
foreach ($t in ($migTables | Sort-Object)) { Add-Content -Path $OutFile -Value ('  ' + $t) }

$missing = @($entityTables | Where-Object { -not $migTables.Contains($_) } | Sort-Object)
Add-Content -Path $OutFile -Value ''
Add-Content -Path $OutFile -Value ('==== MISSING (Entity declared but no migration) - ' + $missing.Count + ' tables ====')
if ($missing.Count -eq 0) {
    Add-Content -Path $OutFile -Value '  (none)'
} else {
    foreach ($t in $missing) { Add-Content -Path $OutFile -Value ('  MISS ' + $t) }
}

$orphan = @($migTables | Where-Object { -not $entityTables.Contains($_) } | Sort-Object)
Add-Content -Path $OutFile -Value ''
Add-Content -Path $OutFile -Value ('==== ORPHAN (migration built but no Entity) - ' + $orphan.Count + ' tables ====')
foreach ($t in $orphan) { Add-Content -Path $OutFile -Value ('  -- ' + $t) }

Write-Host 'done'
