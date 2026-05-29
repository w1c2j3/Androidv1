# OCR Service

This service exposes the OCR HTTP contract used by the Spring Boot backend.

## Run

```bash
cd ocr-service
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn ocr_service.main:app --host 0.0.0.0 --port 9000
```

The backend calls:

```http
GET /
GET /health
```

These browser-friendly checks return service metadata. A browser opening the service root should not be used for OCR recognition.

```http
POST /ocr
Content-Type: multipart/form-data
```

Fields:

- `file`: image file
- `traceId`: backend trace id
- `source`: `android_upload` or `feishu_image`
- `hints`: JSON string

## Model Choice

Default profile is `mobile`, which uses the PP-OCRv5 mobile model names when the installed PaddleOCR version supports explicit model selection. It is a practical default for local CPU development and screenshot OCR.

Use `server` when accuracy is more important and the machine has stronger CPU/GPU resources:

```bash
OCR_MODEL_PROFILE=server uvicorn ocr_service.main:app --host 0.0.0.0 --port 9000
```

Useful environment variables:

```text
OCR_LANG=ch
OCR_MODEL_PROFILE=mobile|server
OCR_USE_TEXTLINE_ORIENTATION=true
OCR_USE_DOC_ORIENTATION=false
OCR_USE_DOC_UNWARPING=false
OCR_ENABLE_MKLDNN=false
OCR_MAX_FILE_MB=15
```

## Test

Unit tests do not import PaddleOCR, so they can run without downloading model weights:

```bash
python3 -m unittest discover -s tests
```
