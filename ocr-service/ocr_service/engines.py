from pathlib import Path
from typing import Any, Protocol

from .converter import build_ocr_result
from .schemas import OcrResult
from .settings import OcrSettings


class OcrEngine(Protocol):
    def recognize(self, image_path: str | Path, trace_id: str, source: str = "", hints: dict[str, Any] | None = None) -> OcrResult:
        ...


class PaddleOcrEngine:
    def __init__(self, settings: OcrSettings | None = None) -> None:
        self.settings = settings or OcrSettings.from_env()
        self._ocr: Any | None = None

    def recognize(self, image_path: str | Path, trace_id: str, source: str = "", hints: dict[str, Any] | None = None) -> OcrResult:
        raw_output = self._predict(image_path)
        return build_ocr_result(trace_id=trace_id, image_path=image_path, raw_output=raw_output, hints=hints)

    def _predict(self, image_path: str | Path) -> Any:
        ocr = self._get_ocr()
        path = str(image_path)
        if hasattr(ocr, "predict"):
            return ocr.predict(input=path)
        return ocr.ocr(path, cls=self.settings.use_textline_orientation)

    def _get_ocr(self) -> Any:
        if self._ocr is not None:
            return self._ocr

        try:
            from paddleocr import PaddleOCR
        except ImportError as exception:
            raise RuntimeError("PaddleOCR 未安装，请执行 pip install -r ocr-service/requirements.txt") from exception

        errors: list[str] = []
        for kwargs in self._constructor_candidates():
            try:
                self._ocr = PaddleOCR(**kwargs)
                return self._ocr
            except TypeError as exception:
                errors.append(str(exception))

        try:
            self._ocr = PaddleOCR()
            return self._ocr
        except Exception as exception:
            detail = "; ".join(errors) if errors else str(exception)
            raise RuntimeError("初始化 PaddleOCR 失败：" + detail) from exception

    def _constructor_candidates(self) -> list[dict[str, Any]]:
        common = {
            "lang": self.settings.lang,
            "use_doc_orientation_classify": self.settings.use_doc_orientation,
            "use_doc_unwarping": self.settings.use_doc_unwarping,
            "use_textline_orientation": self.settings.use_textline_orientation,
            "enable_mkldnn": self.settings.enable_mkldnn,
        }
        return [
            {
                **common,
                "ocr_version": "PP-OCRv5",
                "text_detection_model_name": self.settings.detection_model_name,
                "text_recognition_model_name": self.settings.recognition_model_name,
            },
            {
                **common,
                "ocr_version": "PP-OCRv5",
            },
            {
                "use_angle_cls": self.settings.use_textline_orientation,
                "lang": self.settings.lang,
            },
        ]


class StaticOcrEngine:
    def __init__(self, raw_output: Any) -> None:
        self.raw_output = raw_output

    def recognize(self, image_path: str | Path, trace_id: str, source: str = "", hints: dict[str, Any] | None = None) -> OcrResult:
        return build_ocr_result(trace_id=trace_id, image_path=image_path, raw_output=self.raw_output, hints=hints)
