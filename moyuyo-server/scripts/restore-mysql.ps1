# ============================================================
# MOYUYO MySQL 数据库恢复脚本（PowerShell 版）
#
# 用途：从 backup-mysql.ps1 生成的 .sql.gz 归档恢复到目标 MySQL 容器
#
# 警告：此操作会覆盖目标数据库的全部数据，二次确认后才会执行
#
# 用法：
#   .\scripts\restore-mysql.ps1 `
#       -ContainerName moyuyo-mysql `
#       -DbName moyuyo_prod `
#       -BackupFile D:\backups\moyuyo\mysql\moyuyo_moyuyo_prod_20260813_030000.sql.gz
# ============================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$ContainerName,

    [Parameter(Mandatory=$false)]
    [string]$DbName = 'moyuyo_prod',

    [Parameter(Mandatory=$true)]
    [string]$BackupFile,

    [Parameter(Mandatory=$false)]
    [string]$MySqlUser = 'root'
)

$ErrorActionPreference = 'Stop'

# ---- 1. 文件存在性检查 ----
if (-not (Test-Path $BackupFile)) {
    Write-Error "[FATAL] 备份文件不存在：$BackupFile"
    exit 1
}
$sizeMB = [math]::Round((Get-Item $BackupFile).Length / 1MB, 2)
Write-Host "[restore] 备份文件：$BackupFile（$sizeMB MB）" -ForegroundColor Cyan

# ---- 2. 读取 .env 密码 ----
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
    Write-Error "[FATAL] 未找到 MYSQL_ROOT_PASSWORD，请检查 $envFile"
    exit 1
}

# ---- 3. 二次确认（避免误操作覆盖生产库）----
Write-Host "[restore] ⚠️  即将恢复备份到容器 $ContainerName 的数据库 $DbName"
Write-Host "         该操作会覆盖目标库的全部数据！"
$confirm = Read-Host "         确认继续？请输入 'YES' 继续"
if ($confirm -ne 'YES') {
    Write-Host "[restore] 已取消"
    exit 0
}

# ---- 4. 检查容器状态 ----
$running = docker ps --format '{{.Names}}' | Where-Object { $_ -eq $ContainerName }
if (-not $running) {
    Write-Error "[FATAL] 容器 $ContainerName 未运行"
    exit 1
}

# ---- 5. 解压 + 管道恢复 ----
Write-Host "[restore] 开始恢复，请稍候..." -ForegroundColor Green

# 使用 tar -xzf 解压到 stdout，管道给 docker exec 的 mysql 命令
# 注意 -p 参数：tar 解压后保留文件权限（虽然 .sql 文件不太需要，但保险起见）
$process = Start-Process -FilePath "tar" -ArgumentList @("-xzOf", $BackupFile) `
                          -NoNewWindow -PassThru -RedirectStandardOutput "pipe_out.sql" `
                          -Wait
if ($process.ExitCode -ne 0) {
    Write-Error "[FATAL] 解压失败（exit=$($process.ExitCode)）"
    exit 1
}

# 通过 docker exec 注入到 mysql 客户端
docker exec -i $ContainerName mysql -u${MySqlUser} -p"${mysqlPassword}" $DbName < pipe_out.sql
$restoreExit = $LASTEXITCODE
Remove-Item pipe_out.sql -ErrorAction SilentlyContinue

if ($restoreExit -ne 0) {
    Write-Error "[FATAL] mysql 恢复失败（exit=$restoreExit）"
    exit 1
}

Write-Host "[restore] ✓ 恢复完成" -ForegroundColor Green
Write-Host "[restore] 建议验证："
Write-Host "          docker exec $ContainerName mysql -uroot -p\$MYSQL_ROOT_PASSWORD $DbName -e 'SELECT COUNT(*) FROM mo_user;'"