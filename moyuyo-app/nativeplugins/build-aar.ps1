# 构建 Android 原生插件的 release aar，并放入 HBuilderX 本地插件包结构
#
# 自动探测：Gradle（PATH 或 ~/.gradle/wrapper/dist）· JDK 17 · Android SDK
# 前置条件：DCloud 离线 SDK 中的 uniapp-v8-release.aar 已放到 android/libs/
#
# 用法：
#   # 全部插件
#   powershell -ExecutionPolicy Bypass -File "D:\MOYUYOWPC\moyuyo-app\nativeplugins\build-aar.ps1"
#   # 只编一个（先验证链路）
#   powershell -ExecutionPolicy Bypass -File "...\build-aar.ps1" -Only moyuyo-secure
#
# 产物位置：nativeplugins/<插件名>/android/<模块名>-release.aar

param(
  # 只构建指定模块，支持传模块目录名（moyuyo-secure）或插件包名（MOYUYO-SecureStorage）
  [string[]]$Only,
  # 手动指定 JDK 17 根目录（默认自动探测）
  [string]$JdkHome,
  # 手动指定 gradle.bat / gradle 路径（默认自动探测）
  [string]$GradleBin
)

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

function Resolve-GradleBin {
  if ($GradleBin) {
    if (Test-Path $GradleBin) { return $GradleBin }
    throw "指定的 GradleBin 不存在：$GradleBin"
  }
  $cmd = Get-Command gradle -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }

  # HBuilderX / Android Studio 安装过 Gradle wrapper 时，发行包会缓存在这里
  $dists = Join-Path $env:USERPROFILE '.gradle\wrapper\dists'
  if (Test-Path $dists) {
    $hit = Get-ChildItem $dists -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -like 'gradle-8.*' } |
      Sort-Object Name -Descending |
      ForEach-Object { Get-ChildItem $_.FullName -Recurse -Depth 4 -Filter 'gradle.bat' -ErrorAction SilentlyContinue } |
      Select-Object -First 1
    if ($hit) { return $hit.FullName }
  }
  return $null
}

function Resolve-Jdk17 {
  if ($JdkHome) {
    if (Test-Path (Join-Path $JdkHome 'bin\java.exe')) { return $JdkHome }
    throw "指定的 JdkHome 下找不到 bin\java.exe：$JdkHome"
  }
  # AGP 8.2 / Kotlin 1.9 需要 JDK 17；本机默认 java 可能是更高版本，必须显式指定
  if ($env:JAVA_HOME -and $env:JAVA_HOME -match '17' -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    return $env:JAVA_HOME
  }
  $patterns = @(
    'C:\Program Files\Java\jdk-17*',
    'C:\Program Files\Eclipse Adoptium\jdk-17*',
    (Join-Path $env:USERPROFILE '.jdks\*17*'),
    'C:\smallletters\small\Java\jdk-17*',
    'D:\Java\jdk-17*',
    'D:\Android\Android Studio\jbr'
  )
  foreach ($pattern in $patterns) {
    $hit = Get-Item $pattern -ErrorAction SilentlyContinue |
      Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
      Select-Object -First 1
    if ($hit) { return $hit.FullName }
  }
  return $null
}

function Resolve-AndroidSdk {
  $candidates = @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    'C:\Android\Sdk',
    'D:\Android\Sdk'
  )
  foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path (Join-Path $candidate 'platforms'))) { return $candidate }
  }
  return $null
}

# ---------- 前置检查 ----------

if (-not (Test-Path $sdkAar)) {
  Write-Host "[错误] 缺少 DCloud 离线 SDK 依赖：" -ForegroundColor Red
  Write-Host "       $sdkAar"
  Write-Host "请下载与 HBuilderX 版本一致的 Android 离线 SDK，把 libs\uniapp-v8-release.aar 放进来后重试。"
  exit 1
}

$gradlePath = Resolve-GradleBin
if (-not $gradlePath) {
  Write-Host "[错误] 未找到 Gradle 8.x。安装后再试，或显式指定：" -ForegroundColor Red
  Write-Host "       -GradleBin `"C:\path\to\gradle.bat`""
  exit 1
}

$jdkPath = Resolve-Jdk17
if (-not $jdkPath) {
  Write-Host "[错误] 未找到 JDK 17（AGP 8.2 不支持更高版本）。安装后重试，或显式指定：" -ForegroundColor Red
  Write-Host "       -JdkHome `"C:\path\to\jdk-17`""
  exit 1
}

$androidSdk = Resolve-AndroidSdk
if (-not $androidSdk) {
  Write-Host "[错误] 未找到 Android SDK（需要 platforms\android-34）。" -ForegroundColor Red
  Write-Host "请打开 Android Studio -> SDK Manager 安装："
  Write-Host "  - SDK Platforms: Android 14.0 (API 34)"
  Write-Host "  - SDK Tools: Android SDK Build-Tools 34、Android SDK Platform-Tools"
  Write-Host "安装后重试；若 SDK 不在默认位置，请设置环境变量 ANDROID_HOME。"
  exit 1
}

$platform34 = Join-Path $androidSdk 'platforms\android-34'
if (-not (Test-Path $platform34)) {
  # 不直接退出：AGP 8.2 默认开启 sdkDownload，会在构建时自动补装缺失的 platform / build-tools
  Write-Host "[警告] 未检测到 platforms\android-34，交由 Gradle 自动下载（需要网络）。" -ForegroundColor Yellow
  Write-Host "       若构建报 SDK 缺失，请在 Android Studio SDK Manager 手动安装 Android 14.0 (API 34) 后重试。" -ForegroundColor Yellow
}

# local.properties 记录 SDK 路径；用正斜杠避免 Windows 反斜杠转义问题
$localProps = Join-Path $androidProject 'local.properties'
$sdkDirLine = 'sdk.dir=' + ($androidSdk -replace '\\', '/')
if (-not (Test-Path $localProps) -or ((Get-Content $localProps -Raw) -notmatch 'sdk\.dir=')) {
  Set-Content -Path $localProps -Value $sdkDirLine -Encoding ASCII
  Write-Host "已写入 $localProps ($sdkDirLine)" -ForegroundColor DarkGray
}

Write-Host "Gradle : $gradlePath"
Write-Host "JDK    : $jdkPath"
Write-Host "SDK    : $androidSdk"
Write-Host ""

# 子进程使用 JDK 17（不影响当前会话）
$env:JAVA_HOME = $jdkPath
$env:PATH = (Join-Path $jdkPath 'bin') + ';' + $env:PATH

# ---------- 构建 ----------

$targets = @($moduleMap.Keys)
if ($Only) {
  $targets = @($moduleMap.Keys | Where-Object { $Only -contains $_ -or $Only -contains $moduleMap[$_] })
  if ($targets.Count -eq 0) {
    Write-Host "[错误] -Only 未匹配到任何模块，可选值：" -ForegroundColor Red
    $moduleMap.Keys | ForEach-Object { Write-Host "  $_ ($($moduleMap[$_]))" }
    exit 1
  }
}

Push-Location $androidProject
$failed = @()
$succeeded = @()
try {
  foreach ($module in $targets) {
    $package = $moduleMap[$module]
    Write-Host "==> 构建 $module ($package)" -ForegroundColor Cyan

    # 每个模块单独构建，某个插件失败不影响其它插件
    & $gradlePath ":$module`:assembleRelease" --console=plain
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
Write-Host "全部完成。下一步：HBuilderX -> manifest.json -> App原生插件配置 -> 选择本地插件" -ForegroundColor Green
