# 构建 9 个 Android 原生插件的 release aar，并放入 HBuilderX 本地插件包结构
#
# 前置条件：
#   1. 已安装 Gradle 8.x（命令行可执行 gradle）与 JDK 17
#   2. 已把 DCloud Android 离线 SDK 中的 uniapp-v8-release.aar 放到：
#        nativeplugins/android/libs/uniapp-v8-release.aar
#      （各模块 build.gradle 以 compileOnly 方式引用该 aar，缺失会编译失败）
#
# 产物位置：
#   nativeplugins/<插件名>/android/<模块名>-release.aar
#
# 用法（在任意目录执行）：
#   powershell -ExecutionPolicy Bypass -File nativeplugins/build-aar.ps1

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$androidProject = Join-Path $root 'android'
$sdkAar = Join-Path $androidProject 'libs\uniapp-v8-release.aar'

# 模块目录名 → HBuilderX 本地插件包目录名（与各插件 package.json 的 id 一致）
$moduleMap = [ordered]@{
  'moyuyo-ar'        = 'MOYUYO-AR'
  'moyuyo-auth'      = 'MOYUYO-Auth'
  'moyuyo-biometric' = 'MOYUYO-Biometric'
  'moyuyo-camera'    = 'MOYUYO-Camera'
  'moyuyo-location'  = 'MOYUYO-Location'
  'moyuyo-payment'   = 'MOYUYO-Payment'
  'moyuyo-pethub'    = 'MOYUYO-PetHub3D'
  'moyuyo-push'      = 'MOYUYO-Push'
  'moyuyo-secure'    = 'MOYUYO-SecureStorage'
}

if (-not (Test-Path $sdkAar)) {
  Write-Host "[错误] 缺少 DCloud 离线 SDK 依赖：" -ForegroundColor Red
  Write-Host "       $sdkAar"
  Write-Host "请从 DCloud 官网下载与当前 uni-app 版本匹配的 Android 离线 SDK，"
  Write-Host "把 uniapp-v8-release.aar 复制到 nativeplugins/android/libs/ 后重试。"
  exit 1
}

if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
  Write-Host "[错误] 未找到 gradle 命令，请安装 Gradle 8.x 并加入 PATH。" -ForegroundColor Red
  exit 1
}

Push-Location $androidProject
$failed = @()
$succeeded = @()
try {
  foreach ($module in $moduleMap.Keys) {
    $package = $moduleMap[$module]
    Write-Host "==> 构建 $module ($package)" -ForegroundColor Cyan

    # 每个模块单独构建，某个插件失败不影响其它插件
    & gradle ":$module`:assembleRelease" --console=plain
    if ($LASTEXITCODE -ne 0) {
      $failed += $module
      continue
    }

    $aar = Join-Path $androidProject "$module\build\outputs\aar\$module-release.aar"
    if (-not (Test-Path $aar)) {
      Write-Host "    [警告] 未找到产物 $aar" -ForegroundColor Yellow
      $failed += $module
      continue
    }

    $targetDir = Join-Path $root "$package\android"
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    Copy-Item $aar $targetDir -Force
    Write-Host "    -> $targetDir\$module-release.aar" -ForegroundColor Green
    $succeeded += $package
  }
}
finally {
  Pop-Location
}

Write-Host ""
Write-Host "==== 构建结果 ====" -ForegroundColor Cyan
Write-Host "成功：$($succeeded.Count) 个 -> $($succeeded -join ', ')"
if ($failed.Count -gt 0) {
  Write-Host "失败：$($failed.Count) 个 -> $($failed -join ', ')" -ForegroundColor Red
  exit 1
}
Write-Host "全部完成。下一步：HBuilderX 打开工程根目录 -> manifest.json -> App原生插件配置 -> 选择本地插件" -ForegroundColor Green
