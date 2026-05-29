from pathlib import Path
import tempfile
from typing import Any

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from . import __version__
from .engines import OcrEngine, PaddleOcrEngine
from .http_utils import parse_hints, safe_suffix
from .settings import OcrSettings

app = FastAPI(title="Shiliu AI OCR Service", version=__version__)
settings = OcrSettings.from_env()
_engine: OcrEngine | None = None


def get_engine() -> OcrEngine:
    global _engine
    if _engine is None:
        _engine = PaddleOcrEngine(settings)
    return _engine


def set_engine_for_test(engine: OcrEngine | None) -> None:
    global _engine
    _engine = engine


@app.get("/")
def root() -> dict[str, Any]:
    return {
        "status": "ok",
        "service": "shiliu-ocr-service",
        "version": __version__,
        "endpoints": {
            "health": "/health",
            "ocr": "/ocr",
            "docs": "/docs",
        },
        "message": "OCR 服务已启动。浏览器检查请访问 /health；图片识别请由后端 multipart 调用 /ocr。",
    }


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "service": "shiliu-ocr-service",
        "version": __version__,
        "modelProfile": settings.model_profile,
        "lang": settings.lang,
    }


@app.post("/ocr")
async def recognize(
    file: UploadFile = File(...),
    traceId: str = Form(...),
    source: str = Form(""),
    hints: str = Form("{}"),
) -> dict[str, Any]:
    parsed_hints = parse_hints(hints)
    suffix = safe_suffix(file.filename)
    with tempfile.NamedTemporaryFile(prefix="shiliu_ocr_", suffix=suffix, delete=False) as tmp:
        path = Path(tmp.name)
        total = 0
        while True:
            chunk = await file.read(1024 * 1024)
            if not chunk:
                break
            total += len(chunk)
            if total > settings.max_file_bytes:
                path.unlink(missing_ok=True)
                raise HTTPException(status_code=413, detail="图片超过 OCR 服务大小限制")
            tmp.write(chunk)

    try:
        result = get_engine().recognize(path, trace_id=traceId, source=source, hints=parsed_hints)
        return result.to_dict()
    except HTTPException:
        raise
    except Exception as exception:
        raise HTTPException(status_code=500, detail=str(exception)) from exception
    finally:
        path.unlink(missing_ok=True)
