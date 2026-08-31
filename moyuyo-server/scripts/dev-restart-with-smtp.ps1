# 同步阻塞运行：先 set 当前进程 env，再调 mvn，mvn 子进程继承 env
$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
if (-not (Test-Path $envFile)) { Write-Error ".env not found at $envFile"; exit 1 }

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -match '^\s*$' -or $line -match '^\s*#') { return }
    if ($line -match '^(?<k>[A-Z0-9_]+)\s*=\s*(?<v>.*)$') {
        $k = $matches['k']
        $v = $matches['v']
        if ($k -match '^(SPRING_|MOYUYO_|REDIS_|MYSQL_|JWT_|API_|PAYPAL_|WOOCOMMERCE_|ADMIN_|STRIPE_|ROCKETMQ_|ELASTICSEARCH_|OPENAPI_)') {
            [System.Environment]::SetEnvironmentVariable($k, $v, 'Process')
            Write-Host "  set $k=$v"
        }
    }
}
Write-Host "ENV loaded; starting mvn spring-boot:run (sync, blocking) ..."
Set-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'moyuyo-api')
$mvnCmd = (Get-Command mvn).Source
& $mvnCmd --% spring-boot:run -Dmaven.test.skip=true -Dspring-boot.run.profiles=dev 2>&1 | Tee-Object -FilePath (Join-Path (Split-Path $PSScriptRoot -Parent) 'backend-dev.log')