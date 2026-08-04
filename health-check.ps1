# moyuyo 后端管理 API 健康检查脚本 (V2)
# 覆盖前端 admin.js 中调用的所有端点

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "123456"
)

$ErrorActionPreference = "Continue"

Write-Host "=== moyuyo 后端健康检查 V2 ===" -ForegroundColor Cyan
Write-Host "BaseUrl: $BaseUrl"
Write-Host ""

# 1) 登录获取 token
Write-Host ">>> 登录..." -ForegroundColor Yellow
$token = $null
try {
    $loginBody = '{"email":"' + $Username + '","password":"' + $Password + '"}'
    $loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/admin/auth/login" -Method POST -ContentType "application/json" -Body $loginBody -TimeoutSec 10
    $token = $loginResp.data.token
    if (-not $token) { $token = $loginResp.token }
    if (-not $token) { $token = $loginResp.data.accessToken }
    if ($token) { Write-Host "  OK, token len=$($token.Length)" -ForegroundColor Green }
    else { Write-Host "  WARN: 无 token，尝试无 token 测试" -ForegroundColor Yellow }
} catch {
    Write-Host "  登录失败: $($_.Exception.Message)" -ForegroundColor Red
}

$headers = @{}
if ($token) { $headers["Authorization"] = "Bearer $token" }

# 2) 从前端 admin.js 收集的端点 (前导斜杠已省略，使用时拼 /api/admin 前缀)
$endpoints = @(
    # dashboard
    "GET /dashboard/stats"
    "GET /dashboard/recent-orders"
    "GET /dashboard/sales-trend"
    "GET /dashboard/category-distribution"
    "GET /dashboard/top-products"
    # rbac
    "GET /rbac/roles"
    "GET /rbac/users"
    "GET /rbac/permissions"
    # cms
    "GET /cms/list"
    "GET /cms/1"
    # finance
    "GET /finance/overview"
    "GET /finance/settlements"
    "GET /finance/settlements/1"
    "GET /finance/records"
    # refunds
    "GET /refunds/stats"
    "GET /refunds/list"
    "GET /refunds/reason-distribution"
    "GET /refunds/1"
    # inventory
    "GET /inventory/overview"
    "GET /inventory/alerts"
    "GET /inventory/list"
    # push
    "GET /push/stats"
    "GET /push/records"
    "GET /push/scheduled"
    "GET /push/1"
    # ticket
    "GET /ticket/list"
    "GET /ticket/stats"
    "GET /ticket/1"
    # marketing
    "GET /marketing/campaigns"
    "GET /marketing/campaigns/1"
    "GET /marketing/ab-tests"
    "GET /marketing/effects"
    # complaint
    "GET /complaint/list"
    "GET /complaint/1"
    # review
    "GET /review/list"
    # product
    "GET /products/list"
    "GET /products/1"
    "GET /products/categories"
    "GET /products/brands"
    # product-analysis
    "GET /product-analysis/kpi"
    "GET /product-analysis/list"
    "GET /product-analysis/report"
    # price
    "GET /price/list"
    "GET /price/history"
    # order-ops
    "GET /order-ops/stats"
    "GET /order-ops/export"
    "GET /order-ops/print/list"
    "GET /order-ops/price-modify/list"
    "GET /order-ops/intercept/list"
    "GET /order-ops/monitor/data"
    "GET /order-ops/monitor/list"
    # logistics
    "GET /logistics/warehouses"
    "GET /logistics/overseas"
    "GET /logistics/merge-packages"
    "GET /logistics/split-packages"
    "GET /logistics/carriers"
    "GET /logistics/clearance"
    "GET /logistics/customs"
    "GET /logistics/shipping-strategies"
    "GET /logistics/kpi"
    "GET /logistics/packages"
    # sms
    "GET /sms/stats"
    "GET /sms/records"
    # sensitive
    "GET /sensitive/list"
    "GET /sensitive/categories"
    # risk
    "GET /risk/rules"
    "GET /risk/events"
    "GET /risk/event-stats"
    # satisfaction
    "GET /satisfaction/stats"
    "GET /satisfaction/list"
    # gdpr
    "GET /gdpr/consent-records"
    "GET /gdpr/data-requests"
    "GET /gdpr/policy"
    # audit log
    "GET /audit-log/list"
    "GET /audit-log/stats"
    # analysis
    "GET /analysis/funnel"
    "GET /analysis/rfm"
    "GET /analysis/search"
    "GET /analysis/traffic"
    # app version
    "GET /app-version/list"
    # system
    "GET /system/config"
    "GET /system/logs"
    "GET /system-info/security-config"
    "GET /system-info/info"
    "GET /settings/payment-methods"
    # batch import
    "GET /batch-import/records"
    "GET /batch-import/template/product"
    # live
    "GET /live/rooms"
    "GET /live/rooms/1"
    # knowledge base
    "GET /knowledge-base/list"
    # user profile
    "GET /user-profile/1"
    "GET /user-profile/1/behaviors"
    "GET /user-profile/1/orders"
    # crm/cs
    "GET /crm/cs-performance"
    "GET /crm/1/cs-detail"
    "GET /crm/realtime"
    "GET /crm/realtime-order-flow"
    "GET /crm/realtime/top-products"
    # product approval
    "GET /product-approval/list"
    "GET /product-approval/1"
    # content review
    "GET /content-review/list"
    "GET /content-review/1"
    "GET /content-review/stats"
    "GET /content-review/trend"
    # coupons
    "GET /coupons/list"
    "GET /coupons/stats"
    # flash sales
    "GET /flash-sales/list"
    "GET /flash-sales/stats"
    "GET /flash-sales/1"
    # points
    "GET /points/activities"
    "GET /points/logs"
    "GET /points/stats"
    # blacklist
    "GET /blacklist/list"
    # tariff
    "GET /tariff/configs"
    # cs sessions
    "GET /cs-sessions/list"
    "GET /cs-sessions/1"
    "GET /cs-sessions/stats"
    # risk alert
    "GET /risk-alert/configs"
    "GET /risk-alert/history"
    # order tags
    "GET /order-tags/list"
    "GET /order-tags/1/tags"
    # inventory transfer
    "GET /inventory-transfer/list"
    # users
    "GET /users/stats"
    "GET /users/list"
    "GET /users/1"
    # orders
    "GET /orders/list"
    "GET /orders/1"
    "GET /orders/stats"
    "GET /orders/recent"
    # ab test (实际在 marketing 模块下)
    "GET /marketing/ab-tests"
    # live 详情容错测试
    "GET /live/rooms/1"
)

$pass = 0
$fail = 0
$failList = @()
$okList = @()

foreach ($line in $endpoints) {
    $parts = $line.Split(" ", 2)
    $method = $parts[0]
    $path = $parts[1]
    $url = "$BaseUrl/api/admin$path"
    try {
        $resp = Invoke-WebRequest -Uri $url -Method $method -Headers $headers -TimeoutSec 8 -UseBasicParsing
        $code = [int]$resp.StatusCode
        if ($code -ge 200 -and $code -lt 300) {
            $pass++
            $okList += "$method $path -> $code"
        } else {
            $fail++
            $failList += "$method $path -> $code"
        }
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode.value__ }
        $fail++
        $failList += "$method $path -> $code"
    }
}

Write-Host ""
Write-Host "=== 汇总 ===" -ForegroundColor Cyan
Write-Host "  通过: $pass / 总数: $($endpoints.Count)"
Write-Host "  失败: $fail"

if ($fail -gt 0) {
    Write-Host ""
    Write-Host "=== 失败列表 ===" -ForegroundColor Yellow
    $failList | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
}

$report = @{
    time = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    baseUrl = $BaseUrl
    pass = $pass
    fail = $fail
    total = $endpoints.Count
    failed = $failList
    passed = $okList
}
$reportJson = $report | ConvertTo-Json -Depth 4

$reportPath = "D:\MOYUYOWPC\health-report-$(Get-Date -Format 'yyyyMMdd-HHmmss').json"
$reportJson | Out-File -FilePath $reportPath -Encoding UTF8
Write-Host ""
Write-Host "报告: $reportPath" -ForegroundColor Cyan
