#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/android-env.sh"
cd "$TUNER_PROJECT_ROOT"
if [ ! -f .signing/release.properties ] || [ ! -f .signing/tuner-release.jks ]; then
  printf '缺少原发布签名，请先恢复 .signing 备份；不要重新生成签名。\n'
  exit 1
fi
./gradlew assembleRelease
TUNER_DEVICES="$(adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1}')"
TUNER_DEVICE_COUNT="$(printf '%s\n' "$TUNER_DEVICES" | awk 'NF {n++} END {print n+0}')"
if [ "$TUNER_DEVICE_COUNT" != 1 ]; then
  printf '请连接一台安卓手机，开启 USB 调试并允许此 Mac。当前设备：\n'
  adb devices -l
  exit 1
fi
TUNER_APK=app/build/outputs/apk/release/app-release.apk
if [ ! -f "$TUNER_APK" ]; then
  printf '未找到签名安装包，请先按 README 配置本地发布签名。\n'
  exit 1
fi
adb -s "$TUNER_DEVICES" install -r "$TUNER_APK"
adb -s "$TUNER_DEVICES" shell am start -n com.xianzhun.tuner/.MainActivity
