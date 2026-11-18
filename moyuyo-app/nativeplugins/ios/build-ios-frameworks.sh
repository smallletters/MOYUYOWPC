#!/usr/bin/env bash
#
# 编译 MOYUYO iOS 原生插件为静态 framework，并放入 HBuilderX 本地插件目录
#
# 必须在 macOS 上执行（依赖 Xcode Command Line Tools：xcrun / clang / libtool）。
# 前置条件：下载与 HBuilderX 版本一致的 DCloud iOS SDK（iOSSDK.zip）并解压。
#
# 用法：
#   chmod +x build-ios-frameworks.sh
#   ./build-ios-frameworks.sh /path/to/iOSSDK
#   # 或通过环境变量指定
#   DCLOUD_IOS_SDK=/path/to/iOSSDK ./build-ios-frameworks.sh
#
# 产物：
#   nativeplugins/MOYUYO-Push/ios/MOYUYOPush.framework
#   nativeplugins/MOYUYO-Biometric/ios/MOYUYOBiometric.framework
#   nativeplugins/MOYUYO-SecureStorage/ios/MOYUYOSecureStorage.framework
#
# 说明：仅编译真机 arm64（云打包自定义基座只需 arm64）。
#       如需在模拟器调试，可自行追加 x86_64 / arm64-simulator 并 lipo 合并。

set -euo pipefail

MIN_IOS_VERSION="12.0"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 插件映射：插件ID|framework 名|源码目录（相对 nativeplugins/ios）
# framework 名需与源码中 #import "<Name>/<Header>.h" 的实际使用方无关，
# 只要 package.json 里 ios.plugins[].class 与 ObjC 类名一致即可。
PLUGINS=(
  "MOYUYO-Push|MOYUYOPush|MOYUYOPush/MOYUYOPush"
  "MOYUYO-Biometric|MOYUYOBiometric|MOYUYOBiometric/MOYUYOBiometric"
  "MOYUYO-SecureStorage|MOYUYOSecureStorage|MOYUYOSecureStorage/MOYUYOSecureStorage"
)

SDK_ROOT="${1:-${DCLOUD_IOS_SDK:-}}"

if [[ -z "$SDK_ROOT" ]]; then
  echo "[错误] 请传入 DCloud iOS SDK 路径"
  echo "       例：./build-ios-frameworks.sh /Users/you/Downloads/iOSSDK"
  exit 1
fi

if [[ ! -d "$SDK_ROOT" ]]; then
  echo "[错误] SDK 路径不存在：$SDK_ROOT"
  exit 1
fi

if ! command -v xcrun >/dev/null 2>&1; then
  echo "[错误] 未找到 xcrun，请先安装 Xcode Command Line Tools：xcode-select --install"
  exit 1
fi

# ---------- 定位 DCloud 头文件 ----------

# 优先取 SDK 目录内的头文件，避免误用 HBuilder-uniPluginDemo/Pods 里的副本
HEADER_FILE="$(find "$SDK_ROOT" -name 'DCUniModule.h' 2>/dev/null | grep -E '/inc/' | head -1 || true)"
if [[ -z "$HEADER_FILE" ]]; then
  HEADER_FILE="$(find "$SDK_ROOT" -name 'DCUniModule.h' 2>/dev/null | head -1 || true)"
fi

if [[ -z "$HEADER_FILE" ]]; then
  echo "[错误] 在 $SDK_ROOT 下找不到 DCUniModule.h"
  echo "       请确认下载的是 uni-app iOS 离线 SDK，并解压完整"
  exit 1
fi

# 插件源码以 #import "DCUniModule.h" 引用，需要 DCUni 头文件目录
DCUNI_INC_DIR="$(cd "$(dirname "$HEADER_FILE")" && pwd)"
INCLUDE_FLAGS=(-I "$DCUNI_INC_DIR" -I "$(dirname "$DCUNI_INC_DIR")")

# DCUniBasePlugin.h 会引用 WXComponent.h / WXSDKInstance.h，需要 DCUniBase 的 Headers
WX_HEADER_DIR="$(find "$SDK_ROOT" -type d -path '*DCUniBase.framework/Headers' 2>/dev/null | head -1 || true)"
if [[ -n "$WX_HEADER_DIR" ]]; then
  INCLUDE_FLAGS+=(-I "$WX_HEADER_DIR")
else
  echo "[错误] 找不到 DCUniBase.framework/Headers，请确认 SDK/Libs 完整"
  exit 1
fi

echo "DCloud 头文件：$HEADER_FILE"
echo "DCloud include：${INCLUDE_FLAGS[*]}"

SDK_PATH="$(xcrun --sdk iphoneos --show-sdk-path)"
echo "iOS SDK：$SDK_PATH"
echo ""

BUILD_ROOT="$ROOT_DIR/.build"
rm -rf "$BUILD_ROOT"
mkdir -p "$BUILD_ROOT"

sourceCount=0
for entry in "${PLUGINS[@]}"; do
  IFS='|' read -r PLUGIN_ID FW_NAME SRC_REL <<< "$entry"

  SRC_DIR="$ROOT_DIR/$SRC_REL"
  if [[ ! -d "$SRC_DIR" ]]; then
    echo "[错误] 源码目录不存在：$SRC_DIR"
    exit 1
  fi

  echo "==> 编译 $PLUGIN_ID（$FW_NAME.framework）"

  BUILD_DIR="$BUILD_ROOT/$PLUGIN_ID"
  FW_DIR="$BUILD_DIR/$FW_NAME.framework"
  OBJ_DIR="$BUILD_DIR/obj"
  mkdir -p "$OBJ_DIR" "$FW_DIR/Headers"

  # 逐个编译 .m，-fobjc-arc 与现有源码保持一致；静态库不参与最终链接，
  # 因此 DCUniBaseModule 的符号留到 App 链接 DCloud SDK 时解析
  for src in "$SRC_DIR"/*.m; do
    if [[ ! -f "$src" ]]; then
      echo "[错误] $SRC_DIR 下没有 .m 源码"
      exit 1
    fi
    echo "    clang $(basename "$src")"
    xcrun --sdk iphoneos clang \
      -target "arm64-apple-ios$MIN_IOS_VERSION" \
      -isysroot "$SDK_PATH" \
      -fobjc-arc \
      -O2 \
      -Wno-unused-function \
      "${INCLUDE_FLAGS[@]}" \
      -I "$SRC_DIR" \
      -c "$src" \
      -o "$OBJ_DIR/$(basename "${src%.m}").o"
  done

  # 打包静态库并组装 framework 结构
  xcrun libtool -static -o "$FW_DIR/$FW_NAME" "$OBJ_DIR"/*.o
  cp "$SRC_DIR"/*.h "$FW_DIR/Headers/"

  cat > "$FW_DIR/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key>
  <string>en</string>
  <key>CFBundleExecutable</key>
  <string>$FW_NAME</string>
  <key>CFBundleIdentifier</key>
  <string>com.moyuyo.plugin.$FW_NAME</string>
  <key>CFBundleInfoDictionaryVersion</key>
  <string>6.0</string>
  <key>CFBundleName</key>
  <string>$FW_NAME</string>
  <key>CFBundlePackageType</key>
  <string>FMWK</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0.0</string>
  <key>CFBundleVersion</key>
  <string>1</string>
  <key>MinimumOSVersion</key>
  <string>$MIN_IOS_VERSION</string>
</dict>
</plist>
PLIST

  # 拷贝到 HBuilderX 本地插件目录：nativeplugins/<插件ID>/ios/
  DEST_DIR="$ROOT_DIR/../$PLUGIN_ID/ios"
  mkdir -p "$DEST_DIR"
  rm -rf "$DEST_DIR/$FW_NAME.framework"
  cp -R "$FW_DIR" "$DEST_DIR/"

  echo "    -> $DEST_DIR/$FW_NAME.framework"
  xcrun lipo -info "$DEST_DIR/$FW_NAME.framework/$FW_NAME" | sed 's/^/    /' || true
  sourceCount=$((sourceCount + 1))
done

rm -rf "$BUILD_ROOT"

echo ""
echo "==== 完成：$sourceCount 个 iOS 静态 framework 已就位 ===="
echo "下一步："
echo "  1. 在 HBuilderX 中确认 manifest.json -> App原生插件配置 勾选这 3 个本地插件"
echo "  2. 制作 iOS 自定义调试基座前，需准备 Apple 开发证书（.p12）与描述文件（.mobileprovision）"
