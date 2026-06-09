from pathlib import Path
from typing import Any
import json


def parse_hints(value: str) -> dict[str, Any]:
    if not value or not value.strip():
        return {}
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def safe_suffix(filename: str | None) -> str:
    if not filename:
        return ".bin"
    suffix = Path(filename).suffix.lower()
    if suffix and len(suffix) <= 8 and suffix.replace(".", "").isalnum():
        return suffix
    return ".bin"
