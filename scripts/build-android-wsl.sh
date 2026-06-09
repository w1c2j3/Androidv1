#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
GRADLE_HOME="$ANDROID_DIR/.gradle-user-home"
ANDROID_USER_DIR="$ANDROID_DIR/.android-user-home"
ANDROID_TMP_DIR="$ANDROID_DIR/.tmp"
ENV_FILE="$ROOT_DIR/.env.local"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME:-}/bin/jlink" ]]; then
  if [[ -x "/usr/lib/jvm/java-17-openjdk-amd64/bin/jlink" ]]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

if [[ ! -x "${JAVA_HOME:-}/bin/jlink" ]]; then
  echo "A full JDK with jlink is required. Install OpenJDK 17 JDK or set JAVA_HOME."
  exit 1
fi

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_DIR" ]]; then
  CANDIDATE="/mnt/c/Users/${USER}/AppData/Local/Android/Sdk"
  if [[ -d "$CANDIDATE/platforms" ]]; then
    SDK_DIR="$CANDIDATE"
  fi
fi

if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR/platforms" ]]; then
  echo "Android SDK not found."
  echo "Set ANDROID_SDK_ROOT or install Android Studio SDK."
  exit 1
fi

mkdir -p "$GRADLE_HOME" "$ANDROID_USER_DIR" "$ANDROID_TMP_DIR"
printf 'sdk.dir=%s\n' "$SDK_DIR" > "$ANDROID_DIR/local.properties"
export ANDROID_USER_HOME="$ANDROID_USER_DIR"
export TMPDIR="$ANDROID_TMP_DIR"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.io.tmpdir=$ANDROID_TMP_DIR"
export GRADLE_OPTS="${GRADLE_OPTS:-} -Duser.home=$ANDROID_USER_DIR -Djava.io.tmpdir=$ANDROID_TMP_DIR"

PROXY_URL="${https_proxy:-${HTTPS_PROXY:-${http_proxy:-${HTTP_PROXY:-}}}}"
if [[ -n "$PROXY_URL" ]]; then
  PROXY_ADDR="${PROXY_URL#*://}"
  PROXY_ADDR="${PROXY_ADDR%%/*}"
  PROXY_HOST="${PROXY_ADDR%%:*}"
  PROXY_PORT="${PROXY_ADDR##*:}"
  if [[ -n "$PROXY_HOST" && "$PROXY_PORT" != "$PROXY_HOST" ]]; then
    export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttp.proxyHost=$PROXY_HOST -Dhttp.proxyPort=$PROXY_PORT -Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT"
  fi
fi

cd "$ANDROID_DIR"
if [[ "$#" -eq 0 ]]; then
  set -- :app:assembleDebug
fi

GRADLE_USER_HOME="$GRADLE_HOME" gradle --no-daemon --console=plain "$@"
