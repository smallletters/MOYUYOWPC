# ============================================================
# Elasticsearch 索引备份脚本（PowerShell）
# 通过 snapshot API 备份到配置的 repository（推荐 s3 / nfs）
# 使用方法：
#   .\backup-elasticsearch.ps1 -ContainerName moyuyo-elasticsearch -RepositoryName moyuyo_backup -BackupDir D:\backups\moyuyo\es -RetentionDays 7
# 前置条件：
#   1. ES 已注册 snapshot repository（参考 application-prod.yml 中的 path.repo 或 s3 配置）
#   2. 容器内可访问 ELASTICSEARCH_PASSWORD 环境变量
# ============================================================

param(
    [Parameter(Mandatory = $true)] [string]$ContainerName,
    [Parameter(Mandatory = $true)] [string]$RepositoryName,
    [Parameter(Mandatory = $true)] [string]$BackupDir,
    [int]$RetentionDays = 7
)

$ErrorActionPreference = "Stop"
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$SnapshotName = "moyuyo-snapshot-$Timestamp"

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

# 从容器环境变量获取 ES 密码
$envBlock = docker inspect $ContainerName --format '{{ .Config.Env }}'
if ($envBlock -match 'ELASTICSEARCH_PASSWORD=(\S+)') {
    $esPassword = $Matches[1]
} else {
    Write-Error "[FATAL] 容器内未找到 ELASTICSEARCH_PASSWORD 环境变量"
    exit 1
}

Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 创建快照 $SnapshotName"

# 创建快照（异步执行，返回 acknowledged=true 表示接受）
$createResult = docker exec $ContainerName curl -sS -k -u "elastic:${esPassword}" \
    -X PUT "https://localhost:9200/_snapshot/${RepositoryName}/${SnapshotName}?wait_for_completion=false" `
    -H 'Content-Type: application/json' -d '{"include_global_state": false}'

if ($LASTEXITCODE -ne 0) {
    Write-Error "[FATAL] 创建快照请求失败"
    exit 1
}

Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 快照已启动，等待完成..."

# 轮询快照状态直到完成
while ($true) {
    Start-Sleep -Seconds 10
    $status = docker exec $ContainerName curl -sS -k -u "elastic:${esPassword}" \
        "https://localhost:9200/_snapshot/${RepositoryName}/${SnapshotName}"
    $state = ($status | ConvertFrom-Json).snapshots[0].state
    Write-Host "  快照状态: $state"
    if ($state -eq "SUCCESS") { break }
    if ($state -eq "FAILED" -or $state -eq "PARTIAL") {
        Write-Error "[FATAL] 快照失败: $state"
        exit 1
    }
}

# 记录快照元数据
$metaFile = Join-Path $BackupDir "${SnapshotName}.meta.json"
$status | Out-File -FilePath $metaFile -Encoding utf8
Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 快照成功: $SnapshotName，元数据: $metaFile"

# 清理过期快照
$expired = docker exec $ContainerName curl -sS -k -u "elastic:${esPassword}" \
    "https://localhost:9200/_snapshot/${RepositoryName}/_all" | ConvertFrom-Json
foreach ($s in $expired.snapshots) {
    $name = $s.snapshot
    if ($name -match '^moyuyo-snapshot-(\d{8})_(\d{6})$') {
        $snapTime = [datetime]::ParseExact("$($Matches[1])_$($Matches[2])", "yyyyMMdd_HHmmss", $null)
        if ($snapTime -lt (Get-Date).AddDays(-$RetentionDays)) {
            Write-Host "  删除过期快照: $name"
            docker exec $ContainerName curl -sS -k -u "elastic:${esPassword}" \
                -X DELETE "https://localhost:9200/_snapshot/${RepositoryName}/${name}" | Out-Null
            # 同步删除本地元数据
            $oldMeta = Join-Path $BackupDir "${name}.meta.json"
            if (Test-Path $oldMeta) { Remove-Item $oldMeta -Force }
        }
    }
}

Write-Host "[$(Get-Date -Format 'HH:mm:ss')] ES 备份流程结束"
