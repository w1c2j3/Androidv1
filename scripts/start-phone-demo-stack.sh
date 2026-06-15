#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.local"
TMP_DIR="$ROOT_DIR/.tmp"
LOG_DIR="$TMP_DIR/logs"
CLOUDFLARED_LOCAL="$TMP_DIR/bin/cloudflared"

BACKEND_PORT="${SERVER_PORT:-8000}"
OCR_PORT_VALUE="${OCR_PORT:-9000}"
LLM_BASE_URL_DEFAULT="https://next-token.cc"
LLM_MODEL_DEFAULT="gpt-4.1-nano"

BACKEND_PID=""
OCR_PID=""
TUNNEL_PID=""
TAIL_PID=""
EXTERNAL_LLM_API_KEY="${SHILIU_LLM_API_KEY:-}"

mkdir -p "$LOG_DIR" "$TMP_DIR/bin"
touch "$ENV_FILE"
chmod 600 "$ENV_FILE"

log() {
  printf '[shiliu-demo] %s\n' "$*" >&2
}

upsert_env() {
  local key="$1"
  local value="$2"
  local tmp="${ENV_FILE}.tmp.$$"
  touch "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  if grep -q "^${key}=" "$ENV_FILE"; then
    awk -v key="$key" -v value="$value" '
      BEGIN { prefix = key "=" }
      index($0, prefix) == 1 { $0 = prefix value }
      { print }
    ' "$ENV_FILE" >"$tmp"
  else
    cp "$ENV_FILE" "$tmp"
    printf '%s=%s\n' "$key" "$value" >>"$tmp"
  fi
  mv "$tmp" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
}

load_env() {
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}

generate_token() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 24
    return
  fi
  tr -dc 'A-Za-z0-9' </dev/urandom | head -c 48
}

json_escape() {
  sed 's/\\/\\\\/g; s/"/\\"/g' <<<"$1"
}

extract_json_string() {
  local key="$1"
  sed -n "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -n 1
}

cleanup() {
  if [[ -n "$TAIL_PID" ]] && kill -0 "$TAIL_PID" >/dev/null 2>&1; then
    kill "$TAIL_PID" >/dev/null 2>&1 || true
  fi
  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
    kill "$BACKEND_PID" >/dev/null 2>&1 || true
  fi
  if [[ -n "$OCR_PID" ]] && kill -0 "$OCR_PID" >/dev/null 2>&1; then
    kill "$OCR_PID" >/dev/null 2>&1 || true
  fi
  if [[ -n "$TUNNEL_PID" ]] && kill -0 "$TUNNEL_PID" >/dev/null 2>&1; then
    kill "$TUNNEL_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

resolve_codex_binary() {
  if [[ -n "${SHILIU_CODEX_BINARY:-}" && -x "${SHILIU_CODEX_BINARY:-}" ]]; then
    printf '%s\n' "$SHILIU_CODEX_BINARY"
    return
  fi
  if command -v codex >/dev/null 2>&1; then
    command -v codex
    return
  fi
  if [[ -x "/home/chase/.npm-global/bin/codex" ]]; then
    printf '%s\n' "/home/chase/.npm-global/bin/codex"
    return
  fi
  printf '%s\n' "codex"
}

ensure_cloudflared() {
  if [[ -n "${CLOUDFLARED_CMD:-}" && -x "$CLOUDFLARED_CMD" ]]; then
    printf '%s\n' "$CLOUDFLARED_CMD"
    return
  fi
  if command -v cloudflared >/dev/null 2>&1; then
    command -v cloudflared
    return
  fi
  if [[ -x "$CLOUDFLARED_LOCAL" ]]; then
    printf '%s\n' "$CLOUDFLARED_LOCAL"
    return
  fi

  local os arch url
  os="$(uname -s)"
  arch="$(uname -m)"
  case "$os:$arch" in
    Linux:x86_64|Linux:amd64)
      url="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64"
      ;;
    Linux:aarch64|Linux:arm64)
      url="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64"
      ;;
    *)
      printf 'Unsupported cloudflared auto-download target: %s %s\n' "$os" "$arch" >&2
      printf 'Install cloudflared or set CLOUDFLARED_CMD to its absolute path.\n' >&2
      exit 1
      ;;
  esac

  log "cloudflared not found; downloading local binary to $CLOUDFLARED_LOCAL"
  curl -fL --retry 3 --connect-timeout 15 -o "$CLOUDFLARED_LOCAL" "$url"
  chmod +x "$CLOUDFLARED_LOCAL"
  printf '%s\n' "$CLOUDFLARED_LOCAL"
}

wait_http() {
  local name="$1"
  local url="$2"
  local header="${3:-}"
  local attempts="${4:-90}"
  local delay="${5:-1}"
  for _ in $(seq 1 "$attempts"); do
    if [[ -n "$header" ]]; then
      if curl -fsS -m 5 -H "$header" "$url" >/dev/null 2>&1; then
        log "$name is healthy: $url"
        return 0
      fi
    else
      if curl -fsS -m 5 "$url" >/dev/null 2>&1; then
        log "$name is healthy: $url"
        return 0
      fi
    fi
    sleep "$delay"
  done
  printf '%s did not become healthy: %s\n' "$name" "$url" >&2
  return 1
}

provider_smoke() {
  if [[ -z "${SHILIU_LLM_API_KEY:-}" ]]; then
    log "LLM smoke skipped: SHILIU_LLM_API_KEY is empty"
    return 0
  fi
  local body output status timing content
  body=$(printf '{"model":"%s","messages":[{"role":"user","content":"只回复OK"}],"temperature":0,"max_tokens":8}' "$SHILIU_LLM_MODEL")
  output=$(curl -sS -m 30 -w ' HTTP_STATUS:%{http_code} TIME:%{time_total}' \
    -H "Authorization: Bearer $SHILIU_LLM_API_KEY" \
    -H "Content-Type: application/json" \
    -d "$body" \
    "${SHILIU_LLM_API_BASE_URL%/}/v1/chat/completions" || true)
  status="$(grep -o 'HTTP_STATUS:[0-9]*' <<<"$output" | tail -n 1 | cut -d: -f2)"
  timing="$(grep -o 'TIME:[0-9.]*' <<<"$output" | tail -n 1 | cut -d: -f2)"
  content="$(sed -n 's/.*"content"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' <<<"$output" | head -n 1)"
  if [[ "$status" == "200" ]]; then
    log "LLM smoke OK: model=$SHILIU_LLM_MODEL time=${timing}s content=${content:-<empty>}"
  else
    printf 'LLM smoke failed: model=%s status=%s time=%ss\n' "$SHILIU_LLM_MODEL" "${status:-unknown}" "${timing:-unknown}" >&2
    printf '%s\n' "$output" | sed -E 's/(sk-[A-Za-z0-9]+)/sk-***/g' >&2
    return 1
  fi
}

register_bot_if_needed() {
  local readiness register_body register_response bot_id
  readiness="$(curl -fsS -m 10 \
    -H "Authorization: Bearer $SHILIU_ADMIN_TOKEN" \
    "http://localhost:$BACKEND_PORT/api/v1/setup/readiness" || true)"
  if grep -q '"botRegistered"[[:space:]]*:[[:space:]]*true' <<<"$readiness"; then
    bot_id="$(extract_json_string botId <<<"$readiness")"
    if [[ -n "$bot_id" ]]; then
      upsert_env SHILIU_FEISHU_BOTS_0_BOT_ID "$bot_id"
      load_env
    fi
    return 0
  fi

  if [[ -z "${SHILIU_FEISHU_APP_ID:-}" || -z "${SHILIU_FEISHU_APP_SECRET:-}" || -z "${SHILIU_FEISHU_BOTS_0_VERIFICATION_TOKEN:-}" ]]; then
    log "Feishu bot auto-register skipped: app id/secret/verification token not complete"
    return 0
  fi

  log "Feishu bot is not registered in local DB; trying POST /api/v1/bots/register"
  register_body=$(printf '{"botName":"%s","appId":"%s","appSecret":"%s","verificationToken":"%s","encryptKey":"%s","tenantName":"%s"}' \
    "$(json_escape "${SHILIU_FEISHU_BOTS_0_BOT_NAME:-test}")" \
    "$(json_escape "$SHILIU_FEISHU_APP_ID")" \
    "$(json_escape "$SHILIU_FEISHU_APP_SECRET")" \
    "$(json_escape "$SHILIU_FEISHU_BOTS_0_VERIFICATION_TOKEN")" \
    "$(json_escape "${SHILIU_FEISHU_BOTS_0_ENCRYPT_KEY:-}")" \
    "$(json_escape "${SHILIU_FEISHU_TENANT_NAME:-default}")")
  register_response="$(curl -fsS -m 30 -X POST \
    -H "Authorization: Bearer $SHILIU_ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$register_body" \
    "http://localhost:$BACKEND_PORT/api/v1/bots/register" || true)"
  bot_id="$(extract_json_string botId <<<"$register_response")"
  if [[ -n "$bot_id" ]]; then
    upsert_env SHILIU_FEISHU_BOTS_0_BOT_ID "$bot_id"
    load_env
    log "Feishu bot registered: $bot_id"
  else
    log "Feishu bot auto-register did not return botId; continuing with env bot id if present"
  fi
}

print_final_config() {
  local apk="$ROOT_DIR/android/app/build/outputs/apk/debug/app-debug.apk"
  cat <<EOF

================ Shiliu Phone Demo Ready ================

Local services:
  Backend:       http://localhost:$BACKEND_PORT
  OCR:           http://localhost:$OCR_PORT_VALUE
  Public HTTPS:  $PUBLIC_URL
  APK:           $apk

Android embedded config:
  Backend URL:   $SHILIU_MOBILE_BACKEND_URL
  Admin Token:   $SHILIU_ADMIN_TOKEN
  Project Path:  ${SHILIU_PROJECT_PATH:-$ROOT_DIR}

Feishu Open Platform values:
  Event request URL:
    $PUBLIC_URL/feishu/events/${SHILIU_FEISHU_BOTS_0_BOT_ID:-<botId>}
  Card callback URL:
    $PUBLIC_URL/feishu/card-callback/${SHILIU_FEISHU_BOTS_0_BOT_ID:-<botId>}
  Verification Token:
    ${SHILIU_FEISHU_BOTS_0_VERIFICATION_TOKEN:-<missing>}
  Encrypt Key:
    ${SHILIU_FEISHU_BOTS_0_ENCRYPT_KEY:-<empty>}

LLM:
  Base URL:      $SHILIU_LLM_API_BASE_URL
  Model:         $SHILIU_LLM_MODEL
  API key:       <stored in .env.local>

Codex:
  Binary:        $SHILIU_CODEX_BINARY
  Working Dir:   $SHILIU_CODEX_WORKING_DIR
  Allowed Roots: $SHILIU_CODEX_ALLOWED_WORKING_ROOTS
  CODEX_HOME:    ${SHILIU_CODEX_HOME:-<inherited>}

Logs:
  Backend:       $BACKEND_LOG
  OCR:           $OCR_LOG
  Tunnel:        $TUNNEL_LOG

Keep this terminal open. Press Ctrl-C to stop backend, OCR and tunnel.
=========================================================

EOF
}

load_env

CODEX_BINARY="$(resolve_codex_binary)"
CODEX_HOME_VALUE="${SHILIU_CODEX_HOME:-}"
if [[ -z "$CODEX_HOME_VALUE" && -d "/home/chase/.codex" ]]; then
  CODEX_HOME_VALUE="/home/chase/.codex"
fi

if [[ -z "${SHILIU_ADMIN_TOKEN:-}" || "${SHILIU_ADMIN_TOKEN:-}" == "dev-admin-token" || "${SHILIU_ADMIN_TOKEN:-}" == change-me* ]]; then
  upsert_env SHILIU_ADMIN_TOKEN "$(generate_token)"
fi

upsert_env SERVER_PORT "$BACKEND_PORT"
upsert_env BACKEND_PORT "$BACKEND_PORT"
upsert_env OCR_PORT "$OCR_PORT_VALUE"
upsert_env SHILIU_OCR_HTTP_ENDPOINT "http://localhost:$OCR_PORT_VALUE/ocr"
upsert_env SHILIU_LLM_ENABLED "true"
upsert_env SHILIU_LLM_API_BASE_URL "$LLM_BASE_URL_DEFAULT"
upsert_env SHILIU_LLM_MODEL "$LLM_MODEL_DEFAULT"
if [[ -n "$EXTERNAL_LLM_API_KEY" ]]; then
  upsert_env SHILIU_LLM_API_KEY "$EXTERNAL_LLM_API_KEY"
fi
upsert_env SHILIU_CODEX_ENABLED "true"
upsert_env SHILIU_CODEX_BINARY "$CODEX_BINARY"
upsert_env SHILIU_CODEX_WORKING_DIR "$ROOT_DIR"
upsert_env SHILIU_CODEX_ALLOWED_WORKING_ROOTS "$ROOT_DIR"
upsert_env SHILIU_CODEX_TIMEOUT_SECONDS "${SHILIU_CODEX_TIMEOUT_SECONDS:-300}"
upsert_env SHILIU_CODEX_HOME "$CODEX_HOME_VALUE"
upsert_env SHILIU_AGENT_ALLOWED_PROJECT_ROOTS "$ROOT_DIR"
upsert_env SHILIU_PROJECT_PATH "$ROOT_DIR"

load_env

if [[ -z "${SHILIU_LLM_API_KEY:-}" ]]; then
  cat >&2 <<EOF
SHILIU_LLM_API_KEY is missing in $ENV_FILE.
Add the NewAPI key to .env.local, then run this script again.
EOF
  exit 1
fi

if [[ -z "${SHILIU_FEISHU_BOTS_0_BOT_NAME:-}" ]]; then
  upsert_env SHILIU_FEISHU_BOTS_0_BOT_NAME "test"
  load_env
fi

provider_smoke

CLOUDFLARED_CMD_RESOLVED="$(ensure_cloudflared)"
TUNNEL_LOG="$LOG_DIR/cloudflared-phone-demo.log"
OCR_LOG="$LOG_DIR/ocr-phone-demo.log"
BACKEND_LOG="$LOG_DIR/backend-phone-demo.log"
rm -f "$TUNNEL_LOG" "$OCR_LOG" "$BACKEND_LOG"

log "starting OCR on port $OCR_PORT_VALUE"
OCR_PORT="$OCR_PORT_VALUE" "$ROOT_DIR/scripts/run-ocr-service.sh" >"$OCR_LOG" 2>&1 &
OCR_PID="$!"
wait_http "OCR" "http://localhost:$OCR_PORT_VALUE/health" "" 120 1

log "starting Cloudflare Quick Tunnel to http://localhost:$BACKEND_PORT"
"$CLOUDFLARED_CMD_RESOLVED" tunnel --url "http://localhost:$BACKEND_PORT" >"$TUNNEL_LOG" 2>&1 &
TUNNEL_PID="$!"

PUBLIC_URL=""
for _ in $(seq 1 90); do
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
log "public URL is $PUBLIC_URL"

upsert_env SHILIU_PUBLIC_BASE_URL "$PUBLIC_URL"
upsert_env SHILIU_MOBILE_BACKEND_URL "$PUBLIC_URL"
load_env

log "starting backend on port $BACKEND_PORT"
"$ROOT_DIR/scripts/run-backend.sh" >"$BACKEND_LOG" 2>&1 &
BACKEND_PID="$!"
wait_http "Backend" "http://localhost:$BACKEND_PORT/api/v1/health" "" 180 1
wait_http "Backend readiness" "http://localhost:$BACKEND_PORT/api/v1/setup/readiness" "Authorization: Bearer $SHILIU_ADMIN_TOKEN" 60 1

register_bot_if_needed

log "building Android APK with embedded public URL"
"$ROOT_DIR/scripts/build-demo-apk.sh"
load_env

print_final_config

tail -n 60 -F "$BACKEND_LOG" "$OCR_LOG" "$TUNNEL_LOG" &
TAIL_PID="$!"

wait "$BACKEND_PID"
