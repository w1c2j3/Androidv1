from dataclasses import dataclass
import os


def bool_env(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None or value.strip() == "":
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def int_env(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or value.strip() == "":
        return default
    try:
        return int(value)
    except ValueError:
        return default


@dataclass(frozen=True)
class OcrSettings:
    engine: str = "paddleocr"
    lang: str = "ch"
    model_profile: str = "mobile"
    use_textline_orientation: bool = True
    use_doc_orientation: bool = False
    use_doc_unwarping: bool = False
    enable_mkldnn: bool = False
    max_file_mb: int = 15

    @classmethod
    def from_env(cls) -> "OcrSettings":
        return cls(
            engine=os.getenv("OCR_ENGINE", "paddleocr").strip().lower() or "paddleocr",
            lang=os.getenv("OCR_LANG", "ch").strip() or "ch",
            model_profile=os.getenv("OCR_MODEL_PROFILE", "mobile").strip().lower() or "mobile",
            use_textline_orientation=bool_env("OCR_USE_TEXTLINE_ORIENTATION", True),
            use_doc_orientation=bool_env("OCR_USE_DOC_ORIENTATION", False),
            use_doc_unwarping=bool_env("OCR_USE_DOC_UNWARPING", False),
            enable_mkldnn=bool_env("OCR_ENABLE_MKLDNN", False),
            max_file_mb=max(1, int_env("OCR_MAX_FILE_MB", 15)),
        )

    @property
    def max_file_bytes(self) -> int:
        return self.max_file_mb * 1024 * 1024

    @property
    def detection_model_name(self) -> str:
        if self.model_profile == "server":
            return "PP-OCRv5_server_det"
        return "PP-OCRv5_mobile_det"

    @property
    def recognition_model_name(self) -> str:
        if self.model_profile == "server":
            return "PP-OCRv5_server_rec"
        return "PP-OCRv5_mobile_rec"
