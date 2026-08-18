# ============================================================
# MOYUYO Elasticsearch 索引快照备份脚本（PowerShell 版）
#
# 用途：通过 Elasticsearch Snapshot API 把全部业务索引备份到共享卷
# 备份位置：容器内 /usr/share/elasticsearch/backups（已挂载到宿主机）
#
# 适用前提：ES 集群已注册 snapshot repository（详见 1PANEL_DEPLOY.md）
#
# 用法：
#   .\scripts\backup-elasticsearch.ps1 `
#       -ContainerName moyuyo-elasticsearch `
#       -RepositoryName moyuyo_backup `
#       -BackupDir D:\backups\moyuyo\es
# ============================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$ContainerName,

    [Parameter(Mandatory=$false)]
    [string]$RepositoryName = 'moyuyo_backup',

    [Parameter(Mandatory=$true)]
    [string]$BackupDir,

    [Parameter(Mandatory=$false)]
    [int]$RetentionDays = 14
)

$ErrorActionPreference = 'Stop'

# ---- 1. 准备备份目录 ----
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

# ---- 2. 读取 .env 中的 ES 密码 ----
$envFile = Join-Path $PSScriptRoot '..\.env'
$esPassword = $null
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^ELASTICSEARCH_PASSWORD=(.+)$') {
            $script:esPassword = $matches[1].Trim()
        }
    }
}
if ([string]::IsNullOrEmpty($esPassword)) {
    Write-Error "[FATAL] 未找到 ELASTICSEARCH_PASSWORD，请检查 $envFile"
    exit 1
}

# ---- 3. 检查容器运行状态 ----
$running = docker ps --format '{{.Names}}' | Where-Object { $_ -eq $ContainerName }
if (-not $running) {
    Write-Error "[FATAL] 容器 $ContainerName 未运行"
    exit 1
}

# ---- 4. 触发 snapshot ----
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$snapshotName = "moyuyo-snap-$timestamp"
$esUrl = 'https://localhost:9200'

Write-Host "[es-snapshot] 创建 snapshot $snapshotName" -ForegroundColor Green

# 通过 docker exec 调用 curl，避免本机需要 openssl 处理 ES 自签证书
$createResult = docker exec $ContainerName curl -sS -k -u "elastic:${esPassword}" `
    -X PUT "$esUrl/_snapshot/$RepositoryName/$snapshotName" `
    -H 'Content-Type: application/json' `
    -d '{"include_global_state": false, "wait_for_completion": false}'

Write-Host "[es-snapshot] 创建结果：$createResult"

if ($createResult -notmatch '"acknowledged":true') {
    Write-Error "[FATAL] snapshot 创建失败：$createResult"
    exit 1
}

# ---- 5. 等待 snapshot 完成 ----
Write-Host "[es-snapshot] 等待 snapshot 完成..."
while ($true) {
    Start-Sleep -Seconds 10
    $status = docker exec $ContainerName curl -sS -k -u "elastic:${esPassword}" `
        "$esUrl/_snapshot/$RepositoryName/$snapshotName" `
        | Select-String -Pattern '"state":"(SUCCESS|FAILED|PARTIAL)"' `
        | ForEach-Object { $matches[1] }

    if ($status -eq 'SUCCESS') {
        Write-Host "[es-snapshot] snapshot 完成" -ForegroundColor Green
        break
    } elseif ($status -eq 'FAILED' -or $status -eq 'PARTIAL') {
        Write-Error "[FATAL] snapshot 状态异常：$status"
        exit 1
    } else {
        Write-Host "[es-snapshot] 状态：$status，等待中..."
    }
}

# ---- 6. 导出元数据到本地备份目录 ----
$metaFile = Join-Path $BackupDir "${snapshotName}.json"
docker exec $ContainerName curl -sS -k -u "elastic:${esPassword}" `
    "$esUrl/_snapshot/$RepositoryName/$snapshotName" > $metaFile
Write-Host "[es-snapshot] 元数据已写入 $metaFile"

# ---- 7. 清理过期 snapshot ----
$cutoff = (Get-Date).AddDays(-$RetentionDays)
docker exec $ContainerName curl -sS -k -u "elastic:${esPassword}" `
    "$esUrl/_snapshot/$RepositoryName/_all" `
    | Select-String -Pattern '"snapshot":"moyuyo-snap-[^"]+"' `
    | ForEach-Object { $matches[0] -replace '"snapshot":"','' -replace '"','' } `
    | ForEach-Object {
        $snapMeta = docker exec $ContainerName curl -sS -k -u "elastic:${esPassword}" "$esUrl/_snapshot/$RepositoryName/$_"
        $startTime = ($snapMeta | Select-String -Pattern '"start_time":"([^"]+)"' | ForEach-Object { $matches[1] })
        if ($startTime -and ([datetime]$startTime) -lt $cutoff) {
            Write-Host "[es-snapshot] 删除过期 snapshot $_"
            docker exec $ContainerName curl -sS -k -u "elastic:${esPassword}" `
                -X DELETE "$esUrl/_snapshot/$RepositoryName/$_" | Out-Null
        }
    }

Write-Host "[es-snapshot] 备份完成" -ForegroundColor Green