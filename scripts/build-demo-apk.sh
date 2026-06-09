#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.local"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

missing=()
[[ -n "${SHILIU_MOBILE_BACKEND_URL:-}" ]] || missing+=(SHILIU_MOBILE_BACKEND_URL)
[[ -n "${SHILIU_ADMIN_TOKEN:-}" ]] || missing+=(SHILIU_ADMIN_TOKEN)
[[ -n "${SHILIU_FEISHU_BOTS_0_BOT_ID:-}" ]] || missing+=(SHILIU_FEISHU_BOTS_0_BOT_ID)
[[ -n "${SHILIU_FEISHU_BOTS_0_BOT_NAME:-}" ]] || missing+=(SHILIU_FEISHU_BOTS_0_BOT_NAME)
[[ -n "${SHILIU_FEISHU_BOTS_0_VERIFICATION_TOKEN:-}" ]] || missing+=(SHILIU_FEISHU_BOTS_0_VERIFICATION_TOKEN)

if (( ${#missing[@]} > 0 )); then
  printf "Missing required APK embedded config:\n" >&2
  printf "  %s\n" "${missing[@]}" >&2
  printf "\nSet them in .env.local after Cloudflare Quick Tunnel prints the current URL.\n" >&2
  exit 1
fi

exec "$ROOT_DIR/scripts/build-android-wsl.sh" :app:clean :app:assembleDebug
