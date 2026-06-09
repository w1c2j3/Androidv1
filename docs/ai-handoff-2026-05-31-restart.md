# AI Handoff: 2026-05-31 Restart State

This file is for the next AI session after restarting the local computer / WSL / server.
Read this file first, then read `AGENTS.md` and `docs/rules.md`.

## Current State

- Time recorded: 2026-05-31 18:12 CST.
- Repository: `/home/chase/GitHub/shiliu-ai-v1`.
- Shell commands in this repo must be prefixed with `rtk` because `AGENTS.md` points to `/home/chase/.codex/RTK.md`.
- All test services have been stopped.
- Confirmed closed ports: `8080`, `8081`, `9000`, `18081`, `18082`, `28280`, `19200`, `20241`.
- The previous Quick Tunnel URL is invalid and must not be reused.
- The workspace is dirty and has many untracked files. Do not revert user changes.
- Do not write real Feishu App Secret, LLM API keys, or Admin Token into repo files.

## Last Verified Results

- OCR unit tests passed: 47/47.
- Backend tests passed: 19/19.
- Android build passed.
- Quick Tunnel public E2E passed after correcting auth header and request shape: 44 checks, 0 failures.
- Real PNG OCR upload worked through public tunnel:
  - OCR text blocks: 525.
  - Task candidates extracted: 19.

## Important Runtime Facts

- Admin auth header is:

```text
Authorization: Bearer <admin-token>
```

- Do not use `X-Shiliu-Admin-Token`; that caused 401.
- Agent digest request must send real text as top-level `contextText`, not nested `context.text`.
- Readiness field is `ocrHealthy`, not `ocrOk`.
- `/api/v1/workbench/overview` does not include readiness; use `/api/v1/setup/readiness`.
- The last successful E2E used `SHILIU_LLM_ENABLED=false`; OCR, OpenAlex paper search, task save, memory save, queue status, project scan were real.
- No fake data is allowed. If a real data source is missing, return a clear `needs_data_source` / similar state instead of fabricated results.

## Implemented Features To Preserve

- Backend:
  - Admin token auth for `/api/v1/**`.
  - Health endpoint: `GET /api/v1/health`.
  - Queue status endpoint: `GET /api/v1/setup/queues`.
  - Readiness endpoint: `GET /api/v1/setup/readiness`.
  - Agent runs:
    - Project review scans real allowed project path.
    - Paper collection uses real paper source fallback, currently OpenAlex worked.
    - Digest works from real `contextText`.
    - Task creation works.
    - Memory creation works.
  - Vision upload, OCR result polling, and saving tasks from trace.
  - Demo protection / bounded queues / finite HTTP timeouts.
- Android:
  - Single `MainActivity` app.
  - Settings save backend URL, Admin Token, project path.
  - API client sends `Authorization: Bearer <token>`.
  - OCR dispatch actions call real Agent / task / memory endpoints.
  - Queue status is shown in overview/settings/log areas.
- OCR service:
  - PaddleOCR service starts via `scripts/run-ocr-service.sh`.

## Restart Recipe

1. Confirm no old listeners:

```bash
rtk ss -ltnp | rg ':8080|:8081|:9000|:18081|:18082|:28280|:19200|:20241' || true
```

2. Start OCR in one terminal/session:

```bash
OCR_PORT=19200 rtk ./scripts/run-ocr-service.sh
```

3. For phone / Feishu testing, start backend with Quick Tunnel in another terminal/session:

```bash
mkdir -p .tmp
openssl rand -hex 24 > .tmp/e2e-admin-token
PATH="$PWD/.tmp/bin:$PATH" \
SERVER_PORT=28280 \
SPRING_DATASOURCE_URL='jdbc:h2:file:./data/e2e-tunnel-28280' \
SHILIU_ADMIN_TOKEN="$(cat .tmp/e2e-admin-token)" \
SHILIU_OCR_HTTP_ENDPOINT='http://127.0.0.1:19200/ocr' \
SHILIU_AGENT_ALLOWED_PROJECT_ROOTS='/home/chase/GitHub/shiliu-ai-v1' \
SHILIU_LLM_ENABLED=false \
rtk ./scripts/run-remote-mobile-backend.sh
```

4. The script prints:

```text
Backend URL : https://<new-random>.trycloudflare.com
Admin Token : <generated-token>
```

Use those exact values in Android settings.

5. Feishu callback URLs after registering a bot:

```text
https://<new-random>.trycloudflare.com/feishu/events/{botId}
https://<new-random>.trycloudflare.com/feishu/card-callback/{botId}
```

## Quick Checks After Restart

```bash
rtk ./scripts/test-ocr-service.sh
cd backend && rtk ./mvnw test
```

For Android build:

```bash
rtk ./scripts/build-android-wsl.sh
```

For API smoke test, check:

- `GET /api/v1/health` without auth.
- `GET /api/v1/setup/queues` with Bearer token.
- `GET /api/v1/setup/readiness` with Bearer token.
- `POST /api/v1/agent/runs` with top-level `contextText`.
- `POST /api/v1/vision/upload`, then poll `/api/v1/vision/results/{traceId}`.
- `POST /api/v1/tasks/from-trace/{traceId}`.
- `POST /feishu/events/{botId}` with Feishu challenge payload.

## Next Priorities

1. Restart local machine / WSL / server.
2. Reopen repo and read this file first.
3. Start OCR, then backend + Quick Tunnel.
4. Fill Android settings with the new tunnel URL and generated Admin Token.
5. Register or re-register Feishu bot using the user's current Feishu credentials, supplied out of band.
6. Run the smoke checks above.
7. If testing model replies, restart backend with real LLM env vars and `SHILIU_LLM_ENABLED=true`; do not commit those values.
8. After demo/testing, stop all services and delete ephemeral `.tmp/e2e-admin-token`.

## Cleanup Reminder

If the user asks to clear testing materials:

- Stop OCR, backend, and cloudflared.
- Verify the ports listed above are closed.
- Delete ephemeral local token files under `.tmp/`.
- Do not delete source code or user design files unless explicitly requested.
