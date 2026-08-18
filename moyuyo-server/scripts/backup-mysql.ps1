# ============================================================
# MOYUYO MySQL 数据库备份脚本（PowerShell 版）
#
# 用途：从运行中的 MySQL 容器导出全库 SQL（mysqldump 形式），
#      压缩归档到指定目录，便于定时任务调度（推荐凌晨 3 点）
#
# 用法：
#   .\scripts\backup-mysql.ps1 `
#       -ContainerName moyuyo-mysql `
#       -DbName moyuyo_prod `
#       -BackupDir D:\backups\moyuyo\mysql
#
# 配合 Windows 任务计划程序：
#   - 触发器：每天 03:00
#   - 操作：powershell -File "D:\MOYUYOWPC\moyuyo-server\scripts\backup-mysql.ps1" -BackupDir "D:\backups\moyuyo\mysql"
# ============================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$ContainerName,

    [Parameter(Mandatory=$false)]
    [string]$DbName = 'moyuyo_prod',

    [Parameter(Mandatory=$false)]
    [string]$MySqlUser = 'root',

    [Parameter(Mandatory=$true)]
    [string]$BackupDir,

    [Parameter(Mandatory=$false)]
    [int]$RetentionDays = 30
)

# 严格模式：遇错即停，避免备份残缺被误归档
$ErrorActionPreference = 'Stop'

# ---- 1. 准备备份目录 ----
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    Write-Host "[backup] 创建备份目录 $BackupDir" -ForegroundColor Cyan
}

# ---- 2. 计算时间戳 ----
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$filename = "moyuyo_${DbName}_${timestamp}.sql"
$filepath = Join-Path $BackupDir $filename

# ---- 3. 从 .env 读取 root 密码（避免明文出现在脚本参数中）----
$envFile = Join-Path $PSScriptRoot '..\.env'
$mysqlPassword = $null
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^MYSQL_ROOT_PASSWORD=(.+)$') {
            $script:mysqlPassword = $matches[1].Trim()
        }
    }
}
if ([string]::IsNullOrEmpty($mysqlPassword)) {
    Write-Error "[FATAL] 未找到 MYSQL_ROOT_PASSWORD，请检查 $envFile 是否存在并包含该变量"
    exit 1
}

# ---- 4. 检查容器运行状态 ----
$running = docker ps --format '{{.Names}}' | Where-Object { $_ -eq $ContainerName }
if (-not $running) {
    Write-Error "[FATAL] 容器 $ContainerName 未运行，请先执行 docker compose up -d mysql"
    exit 1
}

# ---- 5. 执行 mysqldump（docker exec 形式）----
Write-Host "[backup] 开始备份 $DbName -> $filepath" -ForegroundColor Green
docker exec $ContainerName sh -c "exec mysqldump -u${MySqlUser} -p\"${mysqlPassword}\" --single-transaction --routines --triggers --events --quick --lock-tables=false $DbName" `
    > $filepath

if ($LASTEXITCODE -ne 0) {
    Write-Error "[FATAL] mysqldump 失败（exit=$LASTEXITCODE）"
    Remove-Item $filepath -ErrorAction SilentlyContinue
    exit 1
}

# ---- 6. gzip 压缩 ----
$gzipPath = "$filepath.gz"
Write-Host "[backup] 压缩 $filepath -> $gzipPath"
# Windows 10 1803+ 自带 tar.exe，支持 gzip
$compress = Start-Process -FilePath "tar" -ArgumentList @("-czf", $gzipPath, $filepath) -NoNewWindow -PassThru -Wait
if ($compress.ExitCode -ne 0) {
    Write-Warning "[backup] 压缩失败（exit=$($compress.ExitCode)），保留未压缩文件"
} else {
    Remove-Item $filepath
    $filepath = $gzipPath
}

$sizeMB = [math]::Round((Get-Item $filepath).Length / 1MB, 2)
Write-Host "[backup] 备份完成：$filepath（$sizeMB MB）" -ForegroundColor Green

# ---- 7. 清理过期备份 ----
$cutoff = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem $BackupDir -Filter "moyuyo_${DbName}_*.sql.gz" `
    | Where-Object { $_.LastWriteTime -lt $cutoff } `
    | ForEach-Object {
        Write-Host "[cleanup] 删除过期备份 $($_.Name)"
        Remove-Item $_.FullName -Force
    }

Write-Host "[backup] 完成" -ForegroundColor Green