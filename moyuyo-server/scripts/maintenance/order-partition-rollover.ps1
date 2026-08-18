# ============================================================
# 订单分区滚动维护脚本
#
# 用途：
#   为 mo_order / mo_payment 等大表按月滚动创建分区
#   默认每月 1 号凌晨执行（与备份脚本错开）
#
# 用法：
#   .\order-partition-rollover.ps1 -DbHost localhost -DbUser root -DbPassword xxx -MonthsAhead 3
#
# 设计：
#   - 保留 3 个月历史分区（参数化）
#   - 添加未来 3 个月分区（提前创建，避免月初尖峰阻塞）
#   - 删除超过保留期的历史分区（物理删除，提升查询效率）
#
# 注意：
#   - 修改表结构可能导致短暂锁表，建议在低峰期执行
#   - 删除分区是不可逆操作，请提前确认归档策略
# ============================================================

param(
    [Parameter(Mandatory = $true)] [string]$DbHost,
    [Parameter(Mandatory = $true)] [string]$DbUser,
    [Parameter(Mandatory = $true)] [string]$DbPassword,
    [string]$DbName = "moyuyo_prod",
    [int]$MonthsAhead = 3,
    [int]$MonthsRetain = 12,
    [string[]]$Tables = @("mo_order", "mo_payment")
)

$ErrorActionPreference = "Stop"
$mysql = "mysql -h $DbHost -u $DbUser --password=`"$DbPassword`" -N -B $DbName"

Write-Host "[$((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))] 订单分区维护开始"
Write-Host "  数据库: $DbName"
Write-Host "  维护表: $($Tables -join ', ')"
Write-Host "  保留: $MonthsRetain 月"
Write-Host "  提前: $MonthsAhead 月"

# 计算下个月开始时间
$nextMonthStart = (Get-Date).AddMonths(1).ToString("yyyy-MM-01")
$currentMonth = (Get-Date).ToString("yyyy-MM-01")

foreach ($table in $Tables) {
    Write-Host "`n=== 维护表 $table ==="

    # 1. 检查表是否已分区
    $partitionInfo = & $mysql -e "SELECT PARTITION_NAME, PARTITION_DESCRIPTION FROM information_schema.PARTITIONS WHERE TABLE_NAME='$table' AND PARTITION_NAME IS NOT NULL ORDER BY PARTITION_ORDINAL_POSITION"
    if (-not $partitionInfo) {
        Write-Host "  [跳过] 表 $table 未分区（可能为非分区表或分区已禁用）"
        continue
    }

    # 2. 添加未来 N 个月分区
    for ($i = 1; $i -le $MonthsAhead; $i++) {
        $monthStart = (Get-Date).AddMonths($i).ToString("yyyy-MM-01")
        $partName = "p$($monthStart.Substring(0,7).Replace('-',''))"
        $partValue = (Get-Date $monthStart).AddDays(1).ToString("yyyy-MM-dd")
        $partBound = "TO_DAYS('$partValue')"

        # 检查分区是否已存在
        $exists = $partitionInfo | Where-Object { $_ -match "^$partName\s" }
        if ($exists) {
            Write-Host "  分区已存在: $partName"
            continue
        }

        $sql = "ALTER TABLE $table ADD PARTITION (PARTITION $partName VALUES LESS THAN ($partBound))"
        Write-Host "  添加分区: $partName (bound=$partBound)"
        $sql | & $mysql 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "  添加分区失败: $sql"
        }
    }

    # 3. 删除超过保留期的分区
    $retentionBound = (Get-Date).AddMonths(-$MonthsRetain).ToString("yyyy-MM-dd")
    foreach ($line in $partitionInfo) {
        if ($line -match "^p(\d{6})\s+(\d+)") {
            $partName = $Matches[1]
            $partDesc = $Matches[2]
            # 分区命名格式: pYYYYMM, 推断月份
            $partYear = $Matches[1].Substring(0,4)
            $partMonth = $Matches[1].Substring(4,2)
            $partMonthStart = Get-Date "$partYear-$partMonth-01"
            if ($partMonthStart -lt (Get-Date $retentionBound)) {
                Write-Host "  [警告] 删除过期分区: $partName (month=$partYear-$partMonth)"
                $dropSql = "ALTER TABLE $table DROP PARTITION $partName"
                $dropSql | & $mysql 2>&1
                if ($LASTEXITCODE -ne 0) {
                    Write-Warning "  删除分区失败: $dropSql"
                }
            }
        }
    }
}

Write-Host "`n[$((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))] 分区维护完成"