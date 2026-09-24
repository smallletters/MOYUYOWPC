# MOYUYO 启动辅助：解析 .env 并写入当前进程环境变量
# 与 start-with-env.ps1 行为对齐；start-bg.bat 通过 powershell -File 调用本文件避免单行引号转义

param([string]$EnvFile = (Join-Path $PSScriptRoot '.env'))

if (-not (Test-Path $EnvFile)) {
    Write-Host "[load-env] not found: $EnvFile"
    exit 1
}

Get-Content -LiteralPath $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrEmpty($line) -or $line.StartsWith('#')) { return }
    $eq = $line.IndexOf('=')
    if ($eq -lt 1) { return }
    $name = $line.Substring(0, $eq).Trim()
    $value = $line.Substring($eq + 1).Trim()
    if ($value.StartsWith('"') -and $value.EndsWith('"')) { $value = $value.Substring(1, $value.Length - 2) }
    if ($value.StartsWith("'") -and $value.EndsWith("'")) { $value = $value.Substring(1, $value.Length - 2) }
    [System.Environment]::SetEnvironmentVariable($name, $value)
}

if ($env:MOYUYO_CORS_ALLOWED_ORIGINS) {
    Write-Host "[load-env] .env loaded: MOYUYO_CORS_ALLOWED_ORIGINS=$($env:MOYUYO_CORS_ALLOWED_ORIGINS)"
} else {
    Write-Host "[load-env] .env loaded, but MOYUYO_CORS_ALLOWED_ORIGINS empty (dev fallback will apply)"
}