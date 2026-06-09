#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/../ocr-service"

if [ -x ".venv/bin/python" ]; then
  PYTHON_BIN=".venv/bin/python"
else
  PYTHON_BIN="${PYTHON_BIN:-python3}"
fi

PYTHONPATH=. "$PYTHON_BIN" -m unittest discover -s tests
