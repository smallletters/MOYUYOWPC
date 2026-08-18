# ============================================================
# OpenAPI 文档导出脚本
#
# 用途：
#   从运行中的 Spring Boot 应用导出 OpenAPI JSON / YAML，
#   用于客户端 SDK 生成 / API 文档托管。
#
# 用法：
#   .\openapi-export.ps1 -BaseUrl http://localhost:8080 -OutputDir ./docs/openapi
#
# 输出：
#   docs/openapi/openapi.json  - 完整 OpenAPI 规范
#   docs/openapi/openapi.yaml  - YAML 版本（更适合 review）
# ============================================================

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OutputDir = "./docs/openapi",
    [string]$Profile = "dev"  # dev 环境可访问 Swagger；prod 已禁用
)

$ErrorActionPreference = "Stop"

# 检查应用是否运行
Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] 检查应用是否运行: $BaseUrl"
try {
    $health = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -UseBasicParsing -TimeoutSec 5
    if ($health.StatusCode -ne 200) {
        throw "Health check failed"
    }
} catch {
    Write-Error "[FATAL] 应用未运行或不可达: $BaseUrl. 请先启动应用: mvn -pl moyuyo-api spring-boot:run"
    exit 1
}

# 创建输出目录
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

# 导出 JSON
$jsonPath = "$OutputDir/openapi.json"
Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] 导出 OpenAPI JSON: $jsonPath"
Invoke-WebRequest -Uri "$BaseUrl/api-docs" -UseBasicParsing -OutFile $jsonPath

# 导出 YAML
$yamlPath = "$OutputDir/openapi.yaml"
Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] 导出 OpenAPI YAML: $yamlPath"
Invoke-WebRequest -Uri "$BaseUrl/api-docs.yaml" -UseBasicParsing -OutFile $yamlPath

# 统计接口数量
$json = Get-Content $jsonPath -Raw | ConvertFrom-Json
$pathCount = $json.paths.PSObject.Properties.Count
Write-Host "`n=== 导出完成 ===" -ForegroundColor Green
Write-Host "  接口数量: $pathCount"
Write-Host "  OpenAPI 版本: $($json.openapi)"
Write-Host "  JSON: $jsonPath"
Write-Host "  YAML: $yamlPath"
Write-Host "`n下一步："
Write-Host "  1. 用 openapi-generator 生成客户端 SDK:"
Write-Host "     npx @openapitools/openapi-generator-cli generate -i $yamlPath -g typescript-axios -o ./sdk/typescript"
Write-Host "  2. 提交到 docs 目录供团队 review"
Write-Host "  3. 上传到 SwaggerHub / ReadMe.io 托管"