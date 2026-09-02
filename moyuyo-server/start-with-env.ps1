# 加载 .env 并后台启动后端 jar
$ErrorActionPreference = 'Stop'
$Root = 'D:\MOYUYOWPC\moyuyo-server'

# 解析 .env
$envFile = Join-Path $Root '.env'
if (-not (Test-Path $envFile)) {
    throw "找不到 $envFile"
}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrEmpty($line) -or $line.StartsWith('#')) { return }
    $eq = $line.IndexOf('=')
    if ($eq -lt 1) { return }
    $key = $line.Substring(0, $eq).Trim()
    $val = $line.Substring($eq + 1).Trim()
    if ($val.StartsWith('"') -and $val.EndsWith('"')) { $val = $val.Substring(1, $val.Length - 2) }
    if ($val.StartsWith("'") -and $val.EndsWith("'")) { $val = $val.Substring(1, $val.Length - 2) }
    Set-Item -Path "Env:$key" -Value $val
}

Write-Host "MYSQL_HOST=$env:MYSQL_HOST  MYSQL_USER=$env:MYSQL_USER"
Write-Host "JWT_SECRET length=$($env:JWT_SECRET.Length)"

# 后台启动
$proc = Start-Process -FilePath 'C:\smallletters\small\Java\jdk-25.0.3\bin\java.exe' `
    -ArgumentList '-Dspring.profiles.active=dev', '-jar', "$Root\moyuyo-api\target\moyuyo-api-1.0.0.jar" `
    -RedirectStandardOutput "$Root\server-out.log" `
    -RedirectStandardError "$Root\server-err.log" `
    -WorkingDirectory $Root `
    -WindowStyle Hidden `
    -PassThru
Write-Host "started pid=$($proc.Id) at $($proc.StartTime)"
