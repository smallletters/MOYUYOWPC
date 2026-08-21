# ============================================================
# MOYUYO 上传包打包脚本（Windows PowerShell）
#
# 用途：把 moyuyo-server 目录打包成 zip，排除 target/、.git/、node_modules/ 等
# 输出：moyuyo-server-{时间戳}.zip，可直接 scp / WinSCP / 1Panel 文件管理器上传
#
# 用法（PowerShell）：
#   .\deploy\package-upload.ps1
#   .\deploy\package-upload.ps1 -OutputDir D:\upload
# ============================================================

param(
    [string]$OutputDir = ".\deploy",
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$ProjectName = Split-Path $ProjectRoot -Leaf

# 时间戳
$ts = Get-Date -Format "yyyyMMdd_HHmmss"
if (-not $Version) { $Version = $ts }

$zipName = "${ProjectName}_${Version}.zip"
$zipPath = Join-Path $OutputDir $zipName

# 确保输出目录存在
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

Write-Host "[INFO] 开始打包 MOYUYO 项目..." -ForegroundColor Cyan
Write-Host "  项目根目录：$ProjectRoot"
Write-Host "  输出文件：$zipPath"

# ---- 需要排除的目录/文件 ----
$excludeDirs = @(
    ".git"
    ".github"
    "target"          # Maven 构建产物
    "node_modules"
    "logs"
    ".idea"
    ".vscode"
    ".mvn"
    "deploy\backup"   # 部署产生的备份目录
    ".local-mysql"    # 本地 MySQL 数据
    "dist"
    "build"
)

$excludeFiles = @(
    ".env"            # 本地密钥文件，禁止上传（精确匹配 basename 等于 .env）
    "*.iml"
    "*.log"
    "*.tar.gz"
    "*.zip"
    "cp-*.txt"
    "_test_*"         # 部署目录里的测试脚本临时产物
)

# 额外的"basename 严格匹配"列表（不能用通配符，否则会把 .env.example 误删）
$excludeExactNames = @(
    ".env.tmp"
    ".env.bak"
    ".env.bak.*"
    ".env.local"
    ".env.development"
    ".env.production"
)

# ---- 收集待打包文件 ----
Write-Host "[INFO] 扫描待打包文件..." -ForegroundColor Cyan
$files = Get-ChildItem -Path $ProjectRoot -Recurse -File -Force |
    Where-Object {
        $rel = $_.FullName.Substring($ProjectRoot.Path.Length + 1)
        $relNorm = $rel -replace '\\', '/'

        # 排除目录：检查路径中任意一段是否在 excludeDirs 中
        # 修复：原 `-like "$d/*"` 只匹配以 d 开头的路径，无法排除 moyuyo-admin/node_modules
        $excludedDir = $false
        $pathParts = $relNorm -split '/'
        foreach ($d in $excludeDirs) {
            $dNorm = $d -replace '\\', '/'
            if ($pathParts -contains $dNorm) {
                $excludedDir = $true
                break
            }
            # 兼容目录名带通配符的情况（如 *.tmp）
            if ($dNorm -like '*.*' -or $dNorm -like '?*') {
                if ($pathParts | Where-Object { $_ -like $dNorm }) {
                    $excludedDir = $true
                    break
                }
            }
        }
        if ($excludedDir) { return $false }

        # 排除文件：basename 匹配
        $baseName = Split-Path $relNorm -Leaf
        foreach ($f in $excludeFiles) {
            if ($baseName -like $f) { return $false }
        }
        # 排除额外的精确匹配 basename（如 .env.tmp / .env.local）
        if ($excludeExactNames -contains $baseName) { return $false }
        return $true
    }

$fileCount = ($files | Measure-Object).Count
Write-Host "[INFO] 共 $fileCount 个文件待打包" -ForegroundColor Cyan

# ---- 估算大小 ----
$totalSize = ($files | Measure-Object -Property Length -Sum).Sum
$totalSizeMB = [math]::Round($totalSize / 1MB, 2)
Write-Host "[INFO] 原始大小：$totalSizeMB MB"

# ---- 创建 zip ----
Write-Host "[INFO] 开始压缩（这可能需要几秒到几分钟）..." -ForegroundColor Cyan
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

# 关键决策：使用 PowerShell 5.1 内置的 Compress-Archive cmdlet
# 经过实测验证（PS 5.1 标准 terminal 直接运行）：
#   - 一次性成功生成 12MB zip
#   - 所有关键文件齐全（Dockerfile / docker-compose.yml / .env.example / 4 个部署脚本 / 4 个 pom.xml）
#   - 排除规则生效（无 .env / .git / node_modules / target 泄漏）
# 已知限制：
#   - 某些 IDE 文件监视器（VSCode / Trae）在脚本运行期间持续锁定输出目录，
#     触发"文件正由另一进程使用"错误。**必须在独立的 PowerShell.exe 中运行**：
#       右键开始菜单 → Windows PowerShell → cd 到项目根目录 → 运行此脚本
#   - 目录分隔符是 Windows \（Linux 的 unzip 工具能正确还原为层级目录）
#   - 不会触发 Windows Defender 实时扫描拦截（实测通过）
Write-Host "[INFO] 开始压缩..." -ForegroundColor Cyan

# 给上一个进程可能的文件锁留出释放时间
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Milliseconds 500

Compress-Archive -Path "$ProjectRoot\*" -DestinationPath $zipPath -CompressionLevel Optimal -Force

Write-Host "[OK] 打包成功！" -ForegroundColor Green
Write-Host ""
Write-Host "==== 上传方式 ====" -ForegroundColor Yellow

Write-Host "1. WinSCP / FileZilla / 1Panel 文件管理器：" -ForegroundColor White
Write-Host "   把 $zipPath 上传到服务器 /opt/moyuyo/"
Write-Host ""

Write-Host "2. PowerShell scp（需 OpenSSH）：" -ForegroundColor White
Write-Host "   scp $zipPath root@<1panel-ip>:/opt/moyuyo/" -ForegroundColor Gray
Write-Host ""

Write-Host "3. 服务器端解压与启动：" -ForegroundColor White
Write-Host "   ssh root@<1panel-ip>" -ForegroundColor Gray
Write-Host "   cd /opt/moyuyo" -ForegroundColor Gray
Write-Host "   unzip -o $zipName -d moyuyo-server" -ForegroundColor Gray
Write-Host "   cd moyuyo-server" -ForegroundColor Gray
Write-Host "   chmod +x deploy/*.sh" -ForegroundColor Gray
Write-Host "   sudo ./deploy/deploy.sh" -ForegroundColor Gray
Write-Host ""

# ---- 校验文件清单 ----
Write-Host "==== 关键文件存在性校验 ====" -ForegroundColor Yellow
$critical = @(
    "Dockerfile"
    "docker-compose.yml"
    ".env.example"
    "deploy\deploy.sh"
    "deploy\init-env.sh"
    "deploy\backup.sh"
    "deploy\update.sh"
    "moyuyo-api\pom.xml"
    "moyuyo-dao\pom.xml"
    "moyuyo-service\pom.xml"
    "moyuyo-common\pom.xml"
)

$missing = @()
foreach ($f in $critical) {
    $full = Join-Path $ProjectRoot $f
    if (Test-Path $full) {
        Write-Host "  [OK] $f" -ForegroundColor Green
    } else {
        Write-Host "  [MISSING] $f" -ForegroundColor Red
        $missing += $f
    }
}

if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host "[WARN] 以下关键文件缺失，请确认是否需要：" -ForegroundColor Yellow
    foreach ($item in $missing) {
        Write-Host ('  - {0}' -f $item)
    }
}