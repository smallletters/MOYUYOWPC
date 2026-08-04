$body = '{"email":"admin","password":"123456"}'
$loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/auth/login" -Method POST -ContentType "application/json" -Body $body
$token = $loginResp.data.token
$headers = @{ Authorization = "Bearer $token" }

# 测试有问题的接口
$paths = @(
    "/api/admin/cms/list",
    "/api/admin/logistics/merge-packages",
    "/api/admin/logistics/split-packages",
    "/api/admin/logistics/carriers",
    "/api/admin/logistics/overseas",
    "/api/admin/logistics/warehouses",
    "/api/admin/logistics/clearance",
    "/api/admin/logistics/customs",
    "/api/admin/logistics/shipping-strategies"
)

foreach ($p in $paths) {
    Write-Host "=== $p ===" -ForegroundColor Cyan
    $r = Invoke-RestMethod -Uri "http://localhost:8080$p" -Headers $headers -TimeoutSec 8
    $r | ConvertTo-Json -Depth 2 -Compress
    Write-Host ""
}
