# ============================================================
# MOYUYO MySQL TLS Truststore 初始化脚本（Windows PowerShell）
# 与 init-mysql-truststore.sh 等价，便于 Windows 运维使用
#
# 使用：
#   .\scripts\init-mysql-truststore.ps1 `
#     -CaFile C:\certs\mysql-ca.pem `
#     -OutPath D:\moyuyo\certs\mysql-ca.p12 `
#     -Alias moyuyo-mysql-ca
# ============================================================

param(
    [Parameter(Mandatory = $true)] [string] $CaFile,
    [string] $OutPath = "D:\moyuyo\certs\mysql-ca.p12",
    [string] $Alias = "moyuyo-mysql-ca",
    [string] $KeystoreType = "pkcs12"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $CaFile)) {
    Write-Error "[ERROR] CA 文件不存在: $CaFile"
    exit 1
}

$keytool = (Get-Command keytool -ErrorAction SilentlyContinue)
if ($null -eq $keytool) {
    Write-Error "[ERROR] 未找到 keytool，请先安装 JDK/JRE（建议 eclipse-temurin:25-jre）"
    exit 1
}

if (Test-Path $OutPath) {
    Write-Error "[ERROR] 输出文件已存在: $OutPath，如需重新生成请先备份或删除"
    exit 1
}

# 生成强随机 truststore 密码（24 位大小写+数字）
$pool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
$password = -join ((1..24) | ForEach-Object { $pool[(Get-Random -Maximum $pool.Length)] })
if ($password.Length -lt 16) {
    Write-Error "[ERROR] truststore 密码生成失败"
    exit 1
}

$outDir = Split-Path -Parent $OutPath
if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

# 调用 keytool 导入 CA
& keytool -importcert -noprompt `
    -storetype $KeystoreType `
    -keystore $OutPath `
    -storepass $password `
    -alias $Alias `
    -file $CaFile

# 限制文件权限
try {
    $acl = Get-Acl $OutPath
    $acl.SetAccessRuleProtection($true, $false)
    Set-Acl $OutPath $acl
} catch {
    Write-Warning "[WARN] 设置 ACL 失败（仅影响 Windows 文件权限，请人工确认）: $_"
}

Write-Host "============================================================"
Write-Host " MySQL truststore 已生成: $OutPath"
Write-Host " 别名: $Alias"
Write-Host " 类型: $KeystoreType"
Write-Host ""
Write-Host " 请把以下密码写入 .env 的 MYSQL_TRUSTSTORE_PASSWORD:"
Write-Host "   MYSQL_TRUSTSTORE_PASSWORD=$password"
Write-Host ""
Write-Host " 切勿与他人共享该密码，亦勿提交到 Git"
Write-Host "============================================================"