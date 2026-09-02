# Real diff: Entity tables vs ACTUAL docker database tables
$OutFile = 'D:\MOYUYOWPC\moyuyo-server\diff-real.txt'
$DbTablesFile = 'D:\MOYUYOWPC\moyuyo-server\db-tables.txt'
Remove-Item $OutFile -ErrorAction SilentlyContinue

function Out($s) { Add-Content -Path $OutFile -Value $s }

# 1) Load ACTUAL database tables from mysql output
$dbTablesRaw = Get-Content $DbTablesFile | Where-Object { $_.Trim() -ne '' -and $_ -notmatch '^WARNING' }
$dbTables = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($line in $dbTablesRaw) {
    $t = $line.Trim()
    if ($t -ne '') { [void]$dbTables.Add($t) }
}

# 2) Collect entity tables
$entityFiles = Get-ChildItem -Path 'D:\MOYUYOWPC\moyuyo-server\moyuyo-dao\src\main\java\com\moyuyo\dao' -Recurse -Filter *.java |
    Where-Object { $_.FullName -like '*\entity\*' }
$entityTables = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($f in $entityFiles) {
    $content = Get-Content $f.FullName -Raw
    if ($content -match '@TableName\("([a-zA-Z_][a-zA-Z0-9_]*)"\)') {
        [void]$entityTables.Add($Matches[1])
    }
}

# 3) Diff: Entity - DB
$missingInDb = @($entityTables | Where-Object { -not $dbTables.Contains($_) } | Sort-Object)

# 4) Diff: DB - Entity (extra tables in DB without entity)
$extraInDb = @($dbTables | Where-Object { -not $entityTables.Contains($_) -and $_ -ne 'schema_history' } | Sort-Object)

Out ('ACTUAL docker database table count = ' + $dbTables.Count)
Out ('Entity @TableName count             = ' + $entityTables.Count)
Out ''
Out ('=== Entity declares table but NOT in docker DB - ' + $missingInDb.Count + ' tables ===')
if ($missingInDb.Count -eq 0) {
    Out '  (none - all entity tables exist in DB)'
} else {
    foreach ($t in $missingInDb) { Out ('  MISSING ' + $t) }
}

Out ''
Out ('=== Extra tables in docker DB without Entity - ' + $extraInDb.Count + ' tables ===')
if ($extraInDb.Count -eq 0) {
    Out '  (none)'
} else {
    foreach ($t in $extraInDb) { Out ('  EXTRA ' + $t) }
}

Write-Host 'done -> see diff-real.txt'
