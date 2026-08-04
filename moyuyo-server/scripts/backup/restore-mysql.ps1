# ============================================================
# MySQL 恢复脚本（PowerShell）
# 使用方法：
#   .\restore-mysql.ps1 -ContainerName moyuyo-mysql -DbName moyuyo_prod -BackupFile D:\backups\moyuyo\moyuyo_moyuyo_prod_20260804_030000.sql.gz
# 注意：恢复会覆盖目标库，请先停掉 app 服务避免并发写入
# ============================================================

param(
    [Parameter(Mandatory = $true)] [string]$ContainerName,
    [Parameter(Mandatory = $true)] [string]$DbName,
    [Parameter(Mandatory = $true)] [string]$BackupFile
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    Write-Error "[FATAL] 备份文件不存在: $BackupFile"
    exit 1
}

# 二次确认
Write-Host "即将从 $BackupFile 恢复到 ${ContainerName}:${DbName}"
Write-Host "该操作会覆盖现有数据，是否继续？(yes/no)"
$confirm = Read-Host
if ($confirm -ne "yes") {
    Write-Host "已取消"
    exit 0
}

# 先删除再建空库，避免外键冲突
Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 重建数据库 ${DbName}"
docker exec $ContainerName sh -c "mysql -u root -p`$MYSQL_ROOT_PASSWORD -e 'DROP DATABASE IF EXISTS ${DbName}; CREATE DATABASE ${DbName} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'"

# 流式注入（gunzip + mysql）
Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 开始恢复"
if ($BackupFile.EndsWith(".gz")) {
    Get-Content $BackupFile -Raw | docker exec -i $ContainerName sh -c "gunzip | mysql -u root -p`$MYSQL_ROOT_PASSWORD ${DbName}"
} else {
    Get-Content $BackupFile -Raw | docker exec -i $ContainerName sh -c "mysql -u root -p`$MYSQL_ROOT_PASSWORD ${DbName}"
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "[FATAL] MySQL 恢复失败，退出码 $LASTEXITCODE"
    exit 1
}

Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 恢复完成，请启动 app 服务并验证数据完整性"
