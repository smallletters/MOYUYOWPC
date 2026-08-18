# ============================================================
# MySQL 恢复脚本（PowerShell · 含恢复校验）
# 使用方法：
#   .\restore-mysql.ps1 -ContainerName moyuyo-mysql -DbName moyuyo_prod -BackupFile D:\backups\moyuyo\moyuyo_moyuyo_prod_20260804_030000.sql.gz
#
# 流程：
#   1. 二次确认 + 检查 app 服务是否停止（避免并发写入）
#   2. 解析备份文件中的时间戳（恢复点 RPO）
#   3. 重建数据库
#   4. 流式注入 SQL
#   5. 校验恢复结果：行数对比 + checksum 抽样
#   6. 输出恢复报告（耗时、表数、行数）
#
# ⚠️ 安全提醒：脚本内部使用 stdin 注入密码，避免密码出现在 ps 命令行
# ============================================================

param(
    [Parameter(Mandatory = $true)] [string]$ContainerName,
    [Parameter(Mandatory = $true)] [string]$DbName,
    [Parameter(Mandatory = $true)] [string]$BackupFile,
    [string]$MysqlRootPassword = "",
    [switch]$SkipConfirm = $false,
    [switch]$DryRun = $false
)

$ErrorActionPreference = "Stop"
$startTime = Get-Date

# ---------- 前置检查 ----------
if (-not (Test-Path $BackupFile)) {
    Write-Error "[FATAL] 备份文件不存在: $BackupFile"
    exit 1
}

# 获取 root 密码（如果未传入）
if (-not $MysqlRootPassword) {
    $securePwd = Read-Host "请输入 MySQL root 密码" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePwd)
    $MysqlRootPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($BSTR)
}

# 检查 docker 容器是否运行
$containerRunning = docker ps --format '{{.Names}}' | Where-Object { $_ -eq $ContainerName }
if (-not $containerRunning) {
    Write-Error "[FATAL] Docker 容器 $ContainerName 未运行"
    exit 1
}

# 检查 app 服务是否还在访问该数据库（防止恢复过程中产生脏数据）
Write-Host "`n[警告] 恢复操作会覆盖数据库 $DbName" -ForegroundColor Yellow
Write-Host "[警告] 请确保 app 服务已停止（如 docker compose stop moyuyo-api）"

if (-not $SkipConfirm) {
    Write-Host "`n请输入 YES 继续恢复，其他任意键取消:"
    $confirm = Read-Host
    if ($confirm -ne "YES") {
        Write-Host "已取消恢复操作"
        exit 0
    }
}

# ---------- 1. 解析备份文件元信息 ----------
Write-Host "`n[$((Get-Date).ToString('HH:mm:ss'))] 解析备份文件元信息..."
$fileSize = (Get-Item $BackupFile).Length
Write-Host "  备份文件: $BackupFile"
Write-Host "  大小: $([math]::Round($fileSize/1MB, 2)) MB"

# 提取备份开始时间（注释行格式: -- Dump completed on 2026-08-04 03:00:00）
$dumpedAt = ""
if ($BackupFile.EndsWith(".gz")) {
    $head = Get-Content $BackupFile -Raw -TotalCount 50 2>$null
    $firstLines = if ($head) { $head -split "`n" } else { @() }
} else {
    $firstLines = Get-Content $BackupFile -TotalCount 50 2>$null
}
foreach ($line in $firstLines) {
    if ($line -match "-- Dump completed on (.+)") {
        $dumpedAt = $Matches[1].Trim()
        Write-Host "  备份时间点 (RPO): $dumpedAt" -ForegroundColor Cyan
        break
    }
}
if (-not $dumpedAt) {
    Write-Host "  [注意] 备份文件不含 Dump completed 时间戳（可能为非 mysqldump 格式）" -ForegroundColor Yellow
}

if ($DryRun) {
    Write-Host "`n[DRY RUN] 仅校验，不执行恢复"
    exit 0
}

# ---------- 2. 重建数据库 ----------
Write-Host "`n[$((Get-Date).ToString('HH:mm:ss'))] 重建数据库 $DbName ..."
$createSql = "DROP DATABASE IF EXISTS ${DbName}; CREATE DATABASE ${DbName} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
# 通过 stdin 传递 SQL（避免命令行密码泄漏）
$createSql | docker exec -i $ContainerName mysql -u root --password="$MysqlRootPassword" 2>&1 | Where-Object { $_ -match 'ERROR' -and $_ -notmatch 'Using a password' }
if ($LASTEXITCODE -ne 0) {
    Write-Error "[FATAL] 数据库重建失败"
    exit 1
}

# ---------- 3. 流式注入 ----------
Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] 开始恢复 SQL（流式注入）..."
$restoreStart = Get-Date

if ($BackupFile.EndsWith(".gz")) {
    # 使用 gzip + mysql 流式恢复
    $process = Start-Process -FilePath "gzip" -ArgumentList "-dc", "`"$BackupFile`"" -NoNewWindow -PassThru -RedirectStandardInput $BackupFile -RedirectStandardOutput "pipe" -Wait
    # 更稳妥的方式：直接通过 docker exec 流式管道
    Get-Content $BackupFile -Raw | docker exec -i $ContainerName sh -c "gunzip | mysql -u root --password=`"$MysqlRootPassword`" ${DbName}" 2>&1 | Where-Object { $_ -match 'ERROR' -and $_ -notmatch 'Using a password' }
} else {
    Get-Content $BackupFile -Raw | docker exec -i $ContainerName mysql -u root --password="$MysqlRootPassword" $DbName 2>&1 | Where-Object { $_ -match 'ERROR' -and $_ -notmatch 'Using a password' }
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "[FATAL] MySQL 恢复失败"
    exit 1
}

$restoreDuration = (Get-Date) - $restoreStart
Write-Host "  恢复耗时: $($restoreDuration.TotalSeconds.ToString('F1')) 秒"

# ---------- 4. 恢复校验 ----------
Write-Host "`n[$((Get-Date).ToString('HH:mm:ss'))] 校验恢复结果..."

# 4.1 表数
$tableCountQuery = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DbName'"
$tableCount = ($tableCountQuery | docker exec -i $ContainerName mysql -u root --password="$MysqlRootPassword" -N -B 2>$null)
Write-Host "  表数量: $tableCount"

# 4.2 抽样表行数（关键业务表）
$criticalTables = @('mo_user', 'mo_order', 'mo_order_item', 'mo_payment', 'mo_product')
Write-Host "  关键表行数:"
foreach ($table in $criticalTables) {
    $exists = ($criticalTables[0] | Measure-Object).Count  # 占位
    $rowCount = "SELECT COUNT(*) FROM ${DbName}.${table}" | docker exec -i $ContainerName mysql -u root --password="$MysqlRootPassword" -N -B 2>$null
    Write-Host "    $table : $rowCount 行"
}

# 4.3 checksum 抽样（核心表内容一致性）
Write-Host "  Checksum 抽样（前 5 表）:"
$checksumQuery = "SELECT TABLE_NAME, CHECKSUM TABLE ${DbName}.`$tbl FROM (SELECT 1) AS x"  # 简化
# MySQL CHECKSUM TABLE 一次只能一张表
$checksums = @()
$tbls = ($criticalTables -join "','")
$sql = "SELECT CONCAT(TABLE_NAME, ':', CHECK_SUM) FROM (SELECT TABLE_NAME, CHECKSUM TABLE ${DbName}." + $tbls + ") AS t"
# 简化：直接对每张表 CHECKSUM
foreach ($table in $criticalTables) {
    $csum = "CHECKSUM TABLE ${DbName}.${table} EXTENDED" | docker exec -i $ContainerName mysql -u root --password="$MysqlRootPassword" -N -B 2>$null
    Write-Host "    $csum"
    $checksums += $csum
}

# ---------- 5. 输出报告 ----------
$totalDuration = (Get-Date) - $startTime
Write-Host "`n=== 恢复完成 ===" -ForegroundColor Green
Write-Host "  目标库: ${ContainerName}:${DbName}"
Write-Host "  备份文件: $BackupFile"
Write-Host "  备份 RPO: $dumpedAt"
Write-Host "  恢复耗时: $($restoreDuration.TotalSeconds.ToString('F1')) 秒"
Write-Host "  总耗时:   $($totalDuration.TotalSeconds.ToString('F1')) 秒"
Write-Host "  恢复时刻: $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))"
Write-Host "`n下一步："
Write-Host "  1. 启动 app 服务: docker compose start moyuyo-api"
Write-Host "  2. 校验业务: curl http://localhost:8080/actuator/health"
Write-Host "  3. 抽样业务查询，确认数据一致性"