#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
TMP_DIR="$ROOT_DIR/.tmp"
PORT="${SERVER_PORT:-8080}"
ADMIN_TOKEN="${SHILIU_ADMIN_TOKEN:-}"
ALLOWED_ROOTS="${SHILIU_AGENT_ALLOWED_PROJECT_ROOTS:-/home/chase/GitHub/shiliu-ai-v1}"
TUNNEL_LOG="$TMP_DIR/cloudflared-mobile.log"
CLOUDFLARED_CMD="${CLOUDFLARED_CMD:-}"

if [[ -z "$ADMIN_TOKEN" || "$ADMIN_TOKEN" == "dev-admin-token" ]]; then
  cat >&2 <<'EOF'
Refusing to expose the backend with the default Admin Token.

Set a strong token first, for example:
  export SHILIU_ADMIN_TOKEN="$(openssl rand -hex 24)"

Then run:
  ./scripts/run-remote-mobile-backend.sh
EOF
  exit 1
fi

if [[ -z "$CLOUDFLARED_CMD" ]]; then
  if command -v cloudflared >/dev/null 2>&1; then
    CLOUDFLARED_CMD="cloudflared"
  elif [[ -x "$ROOT_DIR/.tmp/bin/cloudflared" ]]; then
    CLOUDFLARED_CMD="$ROOT_DIR/.tmp/bin/cloudflared"
  fi
fi

if [[ -z "$CLOUDFLARED_CMD" ]]; then
  cat >&2 <<'EOF'
cloudflared is required for the quick HTTPS tunnel.

Install:
  https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/

Then run this script again.
EOF
  exit 1
fi

mkdir -p "$TMP_DIR"
rm -f "$TUNNEL_LOG"

"$CLOUDFLARED_CMD" tunnel --url "http://localhost:$PORT" >"$TUNNEL_LOG" 2>&1 &
TUNNEL_PID="$!"

cleanup() {
  if kill -0 "$TUNNEL_PID" >/dev/null 2>&1; then
    kill "$TUNNEL_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

PUBLIC_URL=""
for _ in $(seq 1 60); do
  PUBLIC_URL="$(grep -Eo 'https://[-a-zA-Z0-9]+\.trycloudflare\.com' "$TUNNEL_LOG" | head -n 1 || true)"
  if [[ -n "$PUBLIC_URL" ]]; then
    break
  fi
  if ! kill -0 "$TUNNEL_PID" >/dev/null 2>&1; then
    cat "$TUNNEL_LOG" >&2 || true
    echo "cloudflared exited before creating a tunnel." >&2
    exit 1
  fi
  sleep 1
done

if [[ -z "$PUBLIC_URL" ]]; then
  cat "$TUNNEL_LOG" >&2 || true
  echo "Timed out waiting for Cloudflare tunnel URL." >&2
  exit 1
fi

cat <<EOF
Remote mobile backend is starting.

Android settings:
  Backend URL : $PUBLIC_URL
  Admin Token : $ADMIN_TOKEN

Feishu callback base:
  SHILIU_PUBLIC_BASE_URL=$PUBLIC_URL

Agent allowed project roots:
  SHILIU_AGENT_ALLOWED_PROJECT_ROOTS=$ALLOWED_ROOTS

Tunnel log:
  $TUNNEL_LOG
EOF

cd "$BACKEND_DIR"
export SERVER_PORT="$PORT"
export SHILIU_PUBLIC_BASE_URL="$PUBLIC_URL"
export SHILIU_AGENT_ALLOWED_PROJECT_ROOTS="$ALLOWED_ROOTS"
exec ./mvnw spring-boot:run
ENV_FILE="${ROOT_DIR}/.env.local"

if [ -f "${ENV_FILE}" ]; then
  set -a
  # shellcheck disable=SC1090
  . "${ENV_FILE}"
  set +a
fi

