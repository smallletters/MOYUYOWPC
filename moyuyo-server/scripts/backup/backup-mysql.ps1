# ============================================================
# MySQL 自动备份脚本（PowerShell，兼容 Windows / 1Panel）
# 使用方法：
#   .\backup-mysql.ps1 -ContainerName moyuyo-mysql -DbName moyuyo_prod -BackupDir D:\backups\moyuyo
# 推荐：加入 Windows 任务计划程序，每天凌晨 3 点执行
# ============================================================

param(
    [Parameter(Mandatory = $true)] [string]$ContainerName,
    [Parameter(Mandatory = $true)] [string]$DbName,
    [Parameter(Mandatory = $true)] [string]$BackupDir,
    [int]$RetentionDays = 7
)

$ErrorActionPreference = "Stop"
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$BackupFile = Join-Path $BackupDir "moyuyo_${DbName}_${Timestamp}.sql.gz"

# 确保备份目录存在
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 开始备份 MySQL: ${DbName} -> ${BackupFile}"

# 从容器内执行 mysqldump，并通过管道压缩输出到宿主机
# 注意：mysql 8.0 镜像已包含 mysqldump 与 gzip
$dumpCmd = "mysqldump -u root -p`$MYSQL_ROOT_PASSWORD --single-transaction --routines --triggers --events ${DbName} | gzip"
docker exec $ContainerName sh -c $dumpCmd | Out-File -FilePath $BackupFile -Encoding utf8

if ($LASTEXITCODE -ne 0) {
    Write-Error "[FATAL] MySQL 备份失败，退出码 $LASTEXITCODE"
    exit 1
}

# 校验备份文件
$fileInfo = Get-Item $BackupFile
if ($fileInfo.Length -lt 1KB) {
    Write-Error "[FATAL] 备份文件异常（仅 $fileInfo.Length 字节），疑似空备份"
    Remove-Item $BackupFile -Force
    exit 1
}

Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 备份成功: ${BackupFile} ($([math]::Round($fileInfo.Length / 1MB, 2)) MB)"

# 清理过期备份
$expired = Get-ChildItem $BackupDir -Filter "moyuyo_${DbName}_*.sql.gz" |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) }
foreach ($f in $expired) {
    Remove-Item $f.FullName -Force
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 已清理过期备份: $($f.Name)"
}

Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 备份流程结束"
