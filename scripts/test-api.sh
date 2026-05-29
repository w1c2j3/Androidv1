#!/usr/bin/env sh
set -eu

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="${TOKEN:-dev-admin-token}"

echo "== health =="
curl -sS "$BASE_URL/api/v1/health"
echo

echo "== register bot =="
if [ -z "${FEISHU_APP_ID:-}" ] || [ -z "${FEISHU_APP_SECRET:-}" ] || [ -z "${FEISHU_VERIFICATION_TOKEN:-}" ]; then
  echo "skip: set FEISHU_APP_ID, FEISHU_APP_SECRET and FEISHU_VERIFICATION_TOKEN to test bot registration"
  exit 0
fi

curl -sS -X POST "$BASE_URL/api/v1/bots/register" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"botName\":\"视流助手-测试\",\"appId\":\"$FEISHU_APP_ID\",\"appSecret\":\"$FEISHU_APP_SECRET\",\"verificationToken\":\"$FEISHU_VERIFICATION_TOKEN\",\"encryptKey\":\"${FEISHU_ENCRYPT_KEY:-}\",\"tenantName\":\"${FEISHU_TENANT_NAME:-测试企业}\"}"
echo
