#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/mnt/c/Users/${USER}/AppData/Local/Android/Sdk}}"
ADB="$SDK_DIR/platform-tools/adb.exe"
APK="$ROOT_DIR/android/app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -x "$ADB" ]]; then
  echo "adb.exe not found: $ADB"
  exit 1
fi

if [[ ! -f "$APK" ]]; then
  "$ROOT_DIR/scripts/build-android-wsl.sh"
fi

DEVICE_COUNT="$("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [[ "$DEVICE_COUNT" -eq 0 ]]; then
  echo "No Android emulator/device is connected."
  echo "Open Android Studio > Device Manager > start Medium_Phone_API_36.1, then rerun this script."
  exit 1
fi

"$ADB" install -r "$APK"
echo "Installed: $APK"
