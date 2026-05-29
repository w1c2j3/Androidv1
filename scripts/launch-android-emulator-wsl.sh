#!/usr/bin/env bash
set -euo pipefail

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/mnt/c/Users/${USER}/AppData/Local/Android/Sdk}}"
EMULATOR="$SDK_DIR/emulator/emulator.exe"
AVD_NAME="${1:-Medium_Phone_API_36.1}"

if [[ ! -x "$EMULATOR" ]]; then
  echo "emulator.exe not found: $EMULATOR"
  exit 1
fi

AVAILABLE="$("$EMULATOR" -list-avds | tr -d '\r')"
if ! grep -qx "$AVD_NAME" <<<"$AVAILABLE"; then
  echo "AVD not found: $AVD_NAME"
  echo "Available AVDs:"
  echo "$AVAILABLE"
  exit 1
fi

cmd.exe /c start "" "$(wslpath -w "$EMULATOR")" -avd "$AVD_NAME"
echo "Launching emulator: $AVD_NAME"
