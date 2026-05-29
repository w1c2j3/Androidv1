import uvicorn

from .settings import OcrSettings


def main() -> None:
    settings = OcrSettings.from_env()
    uvicorn.run("ocr_service.main:app", host="0.0.0.0", port=9000, reload=False)


if __name__ == "__main__":
    main()

