# 写操作接口验证脚本 - moyuyo-server (V2)
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "123456"
)

$ErrorActionPreference = "Continue"
$results = New-Object System.Collections.ArrayList

function Do-Request {
    param($Method, $Path, $BodyObj, $Headers)
    try {
        $jsonBody = ""
        if ($BodyObj) { $jsonBody = $BodyObj | ConvertTo-Json -Depth 4 }
        $r = Invoke-WebRequest -Uri "$BaseUrl$Path" -Method $Method -ContentType "application/json" -Body $jsonBody -Headers $Headers -TimeoutSec 10 -UseBasicParsing
        return [pscustomobject]@{ code = [int]$r.StatusCode; ok = ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300); body = $r.Content }
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode.value__ }
        return [pscustomobject]@{ code = $code; ok = $false; body = $_.Exception.Message }
    }
}

# 1) 登录
Write-Host "=== 登录 ===" -ForegroundColor Cyan
$loginBody = '{"email":"' + $Username + '","password":"' + $Password + '"}'
$loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/admin/auth/login" -Method POST -ContentType "application/json" -Body $loginBody -TimeoutSec 10
$token = $loginResp.data.token
$headers = @{ Authorization = "Bearer $token" }
Write-Host "  Token 长度: $($token.Length)"

# 2) 写操作
Write-Host ""
Write-Host "=== 写操作测试 ===" -ForegroundColor Cyan

# 商品创建
$body = @{ name = "API测试商品_$(Get-Date -Format 'HHmmss')"; price = 99.0; stock = 100; categoryId = 1 }
$r = Do-Request -Method "POST" -Path "/api/admin/products/create" -BodyObj $body -Headers $headers
$results.Add([pscustomobject]@{ name = "POST /products/create"; r = $r }) | Out-Null

# 商品上下架 - 先拿 ID
$listResp = Invoke-RestMethod -Uri "$BaseUrl/api/admin/products/list?page=1&size=1" -Headers $headers -TimeoutSec 8
$pid = $listResp.data.records[0].id
$r = Do-Request -Method "PUT" -Path "/api/admin/products/$pid/status" -BodyObj $null -Headers $headers
$results.Add([pscustomobject]@{ name = "PUT /products/$pid/status"; r = $r }) | Out-Null

# 仓库创建
$body = @{ name = "API测试仓库_$(Get-Date -Format 'HHmmss')"; type = "DOMESTIC"; city = "上海"; address = "测试地址"; area = 100; manager = "测试"; status = "ACTIVE" }
$r = Do-Request -Method "POST" -Path "/api/admin/logistics/warehouses" -BodyObj $body -Headers $headers
$results.Add([pscustomobject]@{ name = "POST /logistics/warehouses"; r = $r }) | Out-Null

# 仓库列表
$r = Do-Request -Method "GET" -Path "/api/admin/logistics/warehouses?page=1&size=1" -BodyObj $null -Headers $headers
$results.Add([pscustomobject]@{ name = "GET /logistics/warehouses"; r = $r }) | Out-Null

# 敏感词 - 不同路径
$body = @{ word = "测试敏感词_$(Get-Date -Format 'HHmmss')"; category = "POLITICS"; level = 1 }
$r = Do-Request -Method "POST" -Path "/api/admin/sensitive" -BodyObj $body -Headers $headers
$results.Add([pscustomobject]@{ name = "POST /sensitive"; r = $r }) | Out-Null

# 系统配置
$r = Do-Request -Method "GET" -Path "/api/admin/system/config" -BodyObj $null -Headers $headers
$results.Add([pscustomobject]@{ name = "GET /system/config"; r = $r }) | Out-Null

# 用户列表
$r = Do-Request -Method "GET" -Path "/api/admin/users/list?page=1&size=1" -BodyObj $null -Headers $headers
$results.Add([pscustomobject]@{ name = "GET /users/list"; r = $r }) | Out-Null

# 订单列表
$r = Do-Request -Method "GET" -Path "/api/admin/orders/list?page=1&size=1" -BodyObj $null -Headers $headers
$results.Add([pscustomobject]@{ name = "GET /orders/list"; r = $r }) | Out-Null

# 角色列表
$r = Do-Request -Method "GET" -Path "/api/admin/rbac/roles" -BodyObj $null -Headers $headers
$results.Add([pscustomobject]@{ name = "GET /rbac/roles"; r = $r }) | Out-Null

# 内容审核
$r = Do-Request -Method "GET" -Path "/api/admin/content-review/list" -BodyObj $null -Headers $headers
$results.Add([pscustomobject]@{ name = "GET /content-review/list"; r = $r }) | Out-Null

# 输出
Write-Host ""
Write-Host "=== 结果 ===" -ForegroundColor Cyan
$pass = 0
$fail = 0
foreach ($item in $results) {
    if ($item.r.ok) {
        $pass++
        Write-Host "  [OK]   $($item.name) -> $($item.r.code)" -ForegroundColor Green
    } else {
        $fail++
        Write-Host "  [FAIL] $($item.name) -> $($item.r.code)  $($item.r.body.Substring(0, [Math]::Min(100, $item.r.body.Length)))" -ForegroundColor Red
    }
}
Write-Host ""
Write-Host "通过: $pass / 总数: $($results.Count) / 失败: $fail" -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Red" })
