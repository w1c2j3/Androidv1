#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.local"
TUNNEL_URL="${1:-${SHILIU_MOBILE_BACKEND_URL:-}}"

if [[ -z "$TUNNEL_URL" ]]; then
  echo "Usage: $0 https://<current-quick-tunnel>.trycloudflare.com" >&2
  exit 1
fi

if [[ ! "$TUNNEL_URL" =~ ^https://[^/]+\.trycloudflare\.com/?$ ]]; then
  echo "Expected a Cloudflare Quick Tunnel root URL, for example: https://xxx.trycloudflare.com" >&2
  exit 1
fi

TUNNEL_URL="${TUNNEL_URL%/}"
touch "$ENV_FILE"
chmod 600 "$ENV_FILE"

if grep -q "^SHILIU_MOBILE_BACKEND_URL=" "$ENV_FILE"; then
  sed -i "s|^SHILIU_MOBILE_BACKEND_URL=.*|SHILIU_MOBILE_BACKEND_URL=$TUNNEL_URL|" "$ENV_FILE"
else
  printf "\nSHILIU_MOBILE_BACKEND_URL=%s\n" "$TUNNEL_URL" >> "$ENV_FILE"
fi

exec "$ROOT_DIR/scripts/build-demo-apk.sh"
