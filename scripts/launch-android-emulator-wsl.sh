#!/usr/bin/env bash
set -euo pipefail

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/mnt/c/Users/${USER}/AppData/Local/Android/Sdk}}"
EMULATOR="$SDK_DIR/emulator/emulator.exe"
AVD_NAME="${1:-Medium_Phone_API_36.1}"
WINDOW_POSITION="${2:-${EMULATOR_WINDOW_POS:-${SDL_VIDEO_WINDOW_POS:-}}}"
WINDOW_X="${EMULATOR_WINDOW_X:-}"
WINDOW_Y="${EMULATOR_WINDOW_Y:-}"
POSITION_MODE="${EMULATOR_WINDOW_POSITION:-bottom}"
WINDOW_BOTTOM_OFFSET="${EMULATOR_WINDOW_BOTTOM_OFFSET:-700}"

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

if [[ "$POSITION_MODE" == "bottom" ]]; then
  if ! command -v powershell.exe >/dev/null 2>&1; then
    echo "powershell.exe not found; cannot auto place window to bottom."
    echo "Please set EMULATOR_WINDOW_POSITION=off and use EMULATOR_WINDOW_POS instead."
    exit 1
  fi

  WORKAREA_HEIGHT="$(powershell.exe -NoProfile -Command 'Add-Type -AssemblyName System.Windows.Forms; $wa=[System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea; Write-Output $wa.Height' | tr -d '\r')"
  if [[ ! "$WORKAREA_HEIGHT" =~ ^[0-9]+$ ]]; then
    echo "Failed to read Windows work area height; please set EMULATOR_WINDOW_POS manually."
    exit 1
  fi

  if ! [[ "$WINDOW_BOTTOM_OFFSET" =~ ^[0-9]+$ ]]; then
    WINDOW_BOTTOM_OFFSET=700
  fi

  if [[ -z "$WINDOW_X" ]]; then
    WINDOW_X=0
  fi

  WINDOW_Y="$((WORKAREA_HEIGHT - WINDOW_BOTTOM_OFFSET))"
  if (( WINDOW_Y < 0 )); then
    WINDOW_Y=0
  fi
  WINDOW_POSITION="${WINDOW_X},${WINDOW_Y}"
fi

if [[ -n "$WINDOW_X" || -n "$WINDOW_Y" ]]; then
  if [[ -z "$WINDOW_X" ]]; then
    WINDOW_X=0
  fi
  if [[ -z "$WINDOW_Y" ]]; then
    WINDOW_Y=0
  fi
  WINDOW_POSITION="${WINDOW_X},${WINDOW_Y}"
fi

if [[ -n "$WINDOW_POSITION" ]]; then
  WINDOW_POSITION="${WINDOW_POSITION//$'\r'/}"
  WINDOW_POSITION="${WINDOW_POSITION//[[:space:]]/}"
  export SDL_VIDEO_CENTERED=0
  export SDL_VIDEO_WINDOW_POS="$WINDOW_POSITION"
  echo "Using emulator window position: $SDL_VIDEO_WINDOW_POS"
fi

cmd.exe /c start "" "$(wslpath -w "$EMULATOR")" -avd "$AVD_NAME"
echo "Launching emulator: $AVD_NAME"
