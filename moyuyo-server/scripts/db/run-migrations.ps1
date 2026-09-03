# ============================================================
# run-migrations.ps1
# Run Flyway SQL files in order against a docker MySQL container.
#   - Strict filename order (V<...>.sql)
#   - Skip *.sql.disabled
#   - -DryRun validates directories without touching docker or MySQL
#   - -DropAndRecreate recreates the database before running (DANGEROUS)
#   - -WithSeed also runs db/seed
#   - -ContinueOnError keeps going on failure
#
# Credentials loading:
#   -MYSqlRootPassword  win: pass plain value (overrides .env)
#   -EnvFile            win: custom .env path (default ..\..\.env)
#   If neither given, reads MYSQL_ROOT_PASSWORD and MYSQL_DATABASE
#   from ..\..\.env relative to this script.
# ============================================================

[CmdletBinding()]
param(
    [string]$ContainerName = "moyuyo-mysql",
    [string]$Database,
    [string]$MySqlRootPassword,
    [string]$EnvFile,
    [switch]$DropAndRecreate,
    [switch]$WithSeed,
    [switch]$ContinueOnError,
    [switch]$DryRun,
    [string]$DockerExe = "docker"
)

function Step  { param($m) Write-Host "[$(Get-Date -Format 'HH:mm:ss')] >>> $m" -ForegroundColor Cyan }
function Ok    { param($m) Write-Host "[$(Get-Date -Format 'HH:mm:ss')] OK  $m" -ForegroundColor Green }
function Warn2 { param($m) Write-Host "[$(Get-Date -Format 'HH:mm:ss')] WRN $m" -ForegroundColor Yellow }
function Err2  { param($m) Write-Host "[$(Get-Date -Format 'HH:mm:ss')] ERR $m" -ForegroundColor Red }

# 1. Resolve directories
$ScriptDir    = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot  = Resolve-Path (Join-Path $ScriptDir "..\..")
$ApiDir       = Resolve-Path (Join-Path $ScriptDir "..\..\moyuyo-api\src\main\resources\db")
$MigrationDir = Join-Path $ApiDir "migration"
$SeedDir      = Join-Path $ApiDir "seed"

# 2. Auto-load credentials from .env (if password not passed)
if (-not $EnvFile) {
    $EnvFile = Join-Path $ProjectRoot ".env"
}
function Read-EnvValue([string]$path, [string]$key) {
    if (-not (Test-Path $path)) { return $null }
    $line = Get-Content $path -Encoding UTF8 |
        Where-Object { $_ -notmatch '^\s*#' -and $_ -match ('^' + [regex]::Escape($key) + '=') } |
        Select-Object -First 1
    if (-not $line) { return $null }
    $parts = $line.Split('=', 2)
    if ($parts.Count -lt 2) { return $null }
    return $parts[1].Trim()
}
if (-not $MySqlRootPassword) {
    $v = Read-EnvValue $EnvFile "MYSQL_ROOT_PASSWORD"
    if ($v) {
        Step "loaded MYSQL_ROOT_PASSWORD from $EnvFile"
        $MySqlRootPassword = $v
    }
}
if (-not $Database) {
    $v = Read-EnvValue $EnvFile "MYSQL_DATABASE"
    if ($v) {
        Step "loaded MYSQL_DATABASE from $EnvFile"
        $Database = $v
    }
}

if (-not $Database) { $Database = "moyuyo_prod" }
if (-not $MySqlRootPassword) {
    Err2 "no MySqlRootPassword. Supply -MySqlRootPassword or define MYSQL_ROOT_PASSWORD in .env"
    exit 1
}
Ok "DB=$Database container=$ContainerName"

Step "===== directory check ====="

function Test-Dir([string]$p, [string]$label) {
    if (-not (Test-Path $p)) { Err2 "$label dir NOT FOUND: $p"; return $false }
    Ok "$label dir: $p"
    return $true
}

if (-not (Test-Dir $MigrationDir "migration")) { exit 1 }
if ($WithSeed -and -not (Test-Dir $SeedDir "seed")) { exit 1 }

# 2. Build the planned file lists
function Get-Planned([string]$dir) {
    Get-ChildItem -Path $dir -Filter "*.sql" |
        Where-Object { $_.Name -notlike "*.disabled" } |
        Sort-Object Name
}

$planMigration = Get-Planned $MigrationDir
Step "migration files: $($planMigration.Count)"
$i = 0
foreach ($f in $planMigration) {
    $i++
    $name = $f.Name
    Write-Host ("  {0:D3}  {1}" -f $i, $name)
}

if ($WithSeed -and (Test-Path $SeedDir)) {
    $planSeed = Get-Planned $SeedDir
    Step "seed files: $($planSeed.Count)"
    $j = 0
    foreach ($f in $planSeed) {
        $j++
        $name = $f.Name
        Write-Host ("  {0:D3}  {1}" -f $j, $name)
    }
}

# 3. Detect disabled files
$disabled = Get-ChildItem -Path $MigrationDir -Filter "*.sql.disabled" | Sort-Object Name
if ($disabled.Count -gt 0) {
    Warn2 "disabled files found, will be skipped automatically:"
    foreach ($f in $disabled) { Write-Host "    " $f.Name }
}

# 4. Dry-run ends here
if ($DryRun) {
    Ok "Dry-run done. No docker connection, no SQL executed."
    exit 0
}

# ===== Real execution below =====
Step "===== connecting docker container $ContainerName ====="
& $DockerExe --version *> $null
if ($LASTEXITCODE -ne 0) { Err2 "docker not available"; exit 1 }

$running = & $DockerExe inspect -f "{{.State.Running}}" $ContainerName 2> $null
if ($running -ne "true") { Err2 "container $ContainerName not running"; exit 1 }
Ok "container is running"

if ($DropAndRecreate) {
    Warn2 "DROP and CREATE database $Database (destructive)"
    $dropSql = "SET FOREIGN_KEY_CHECKS=0; DROP DATABASE IF EXISTS `$Database; CREATE DATABASE `$Database DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; SET FOREIGN_KEY_CHECKS=1;"
    $dropSql | & $DockerExe exec -i $ContainerName mysql -u root -p"$MySqlRootPassword"
    if ($LASTEXITCODE -ne 0) { Err2 "recreate failed"; exit 1 }
    Ok "database $Database recreated"
} else {
    Step "skip DROP (relying on IF NOT EXISTS idem-potency)"
}

function Run-File([string]$SqlFile, [string]$DbName, [bool]$IgnoreError) {
    $name = Split-Path -Leaf $SqlFile
    Step "exec [$name]"
    Get-Content $SqlFile -Encoding UTF8 | & $DockerExe exec -i $ContainerName mysql `
        --default-character-set=utf8mb4 -u root -p"$MySqlRootPassword" $DbName
    if ($LASTEXITCODE -ne 0) {
        if ($IgnoreError) {
            Warn2 "[$name] exit=$LASTEXITCODE (continuing)"
            return $false
        } else {
            Err2 "[$name] failed exit=$LASTEXITCODE"
            exit $LASTEXITCODE
        }
    }
    Ok "[$name] ok"
    return $true
}

# 本脚本为"补缺"场景: 默认忽略失败, 用户仍可用 -ContinueOnError:$false 覆盖
$IgnoreFailure = $true
if ($PSBoundParameters.ContainsKey('ContinueOnError')) {
    $IgnoreFailure = [bool]$ContinueOnError
}

Step "===== run db/migration (continue-on-error=$IgnoreFailure) ====="
$ok = 0; $fail = 0
foreach ($f in $planMigration) {
    if (Run-File $f.FullName $Database $IgnoreFailure) { $ok++ } else { $fail++ }
}
Ok "migration done: ok=$ok fail=$fail"

if ($WithSeed -and (Test-Path $SeedDir)) {
    Step "===== run db/seed ====="
    foreach ($f in $planSeed) {
        if (Run-File $f.FullName $Database) { $ok++ } else { $fail++ }
    }
} else {
    Step "skip db/seed"
}

Step "===== smoke check ====="
$cnt = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database';"
$cnt | & $DockerExe exec -i $ContainerName mysql -N -B -u root -p"$MySqlRootPassword" $Database

$hist = "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
Step "flyway_schema_history (head 5):"
$hist | & $DockerExe exec -i $ContainerName mysql -u root -p"$MySqlRootPassword" $Database | Select-Object -First 5

if ($fail -gt 0) { Warn2 "total failures: $fail"; exit 2 }
Ok "all done"
exit 0
