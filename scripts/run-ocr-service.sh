#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/../ocr-service"

mkdir -p .tmp .pip-cache .uv-cache .home .cache .cache/paddle .cache/paddlex
export HOME="$PWD/.home"
export TMPDIR="$PWD/.tmp"
export XDG_CACHE_HOME="$PWD/.cache"
export PIP_CACHE_DIR="$PWD/.pip-cache"
export UV_CACHE_DIR="$PWD/.uv-cache"
export PADDLE_HOME="$PWD/.cache/paddle"
export PADDLEX_HOME="$PWD/.cache/paddlex"
export FLAGS_use_mkldnn="${FLAGS_use_mkldnn:-0}"
export FLAGS_use_onednn="${FLAGS_use_onednn:-0}"

if [ ! -d ".venv" ]; then
  if command -v uv >/dev/null 2>&1; then
    uv venv .venv --python python3
  else
    python3 -m venv .venv
  fi
fi

export OSTYPE="${OSTYPE:-linux-gnu}"
. .venv/bin/activate
if command -v uv >/dev/null 2>&1; then
  uv pip install -r requirements.txt
else
  pip install -r requirements.txt
fi
exec uvicorn ocr_service.main:app --host 0.0.0.0 --port "${OCR_PORT:-9000}"
