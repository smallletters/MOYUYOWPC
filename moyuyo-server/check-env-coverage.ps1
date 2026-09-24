# 检查 application-prod.yml 中通过 ${ENV} 占位符引用的环境变量，是否在 docker-compose.yml 的 app 容器 environment 中注入
$OutputEncoding = [System.Text.Encoding]::UTF8

$prodYml = Get-Content 'd:\MOYUYOWPC\moyuyo-server\moyuyo-api\src\main\resources\application-prod.yml' -Raw
$compose = Get-Content 'd:\MOYUYOWPC\moyuyo-server\docker-compose.yml' -Raw

# 用 Select-String -AllMatches 提取 ${XXX} 内的 key
$ymlMatches = Select-String -InputObject $prodYml -Pattern '\$\{([A-Z_][A-Z0-9_]*)' -AllMatches
$ymlKeys = @()
foreach ($m in $ymlMatches) {
    foreach ($cap in $m.Matches) {
        $key = $cap.Groups[1].Value
        if ($ymlKeys -notcontains $key) { $ymlKeys += $key }
    }
}

# 抓 compose 中所有 KEY: 形式的 key（覆盖 ${XXX} 透传 与 硬编码值 两种情况）
$composeKeys = @()
$composeKeyLines = Select-String -InputObject $compose -Pattern '^[ ]*([A-Z_][A-Z0-9_]*):' -AllMatches
foreach ($m in $composeKeyLines) {
    $key = $m.Matches[0].Groups[1].Value
    if ($composeKeys -notcontains $key) { $composeKeys += $key }
}

Write-Host '=== application-prod.yml 用到的 env ==='
$ymlKeys | Sort-Object | ForEach-Object { Write-Host ('  ' + $_) }

Write-Host ''
Write-Host '=== docker-compose.yml 注入到容器的 env ==='
$composeKeys | Sort-Object | ForEach-Object { Write-Host ('  ' + $_) }

Write-Host ''
Write-Host '=== prod yml 需要但 compose 未注入 (会拿 yml 默认值) ==='
$missing = @()
foreach ($k in $ymlKeys) { if ($composeKeys -notcontains $k) { $missing += $k } }
if ($missing.Count -eq 0) {
    Write-Host '  (none)'
} else {
    $missing | Sort-Object | ForEach-Object { Write-Host ('  ' + $_) }
    # 与 bash 版本对齐：missing 时退出非零，便于本地/CI 接入时作为门禁
    exit 1
}

Write-Host ''
Write-Host '=== compose 注入但 prod yml 未引用 ==='
$extra = @()
foreach ($k in $composeKeys) { if ($ymlKeys -notcontains $k) { $extra += $k } }
if ($extra.Count -eq 0) {
    Write-Host '  (none)'
} else {
    $extra | Sort-Object | ForEach-Object { Write-Host ('  ' + $_) }
}