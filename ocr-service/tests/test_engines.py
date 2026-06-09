import tempfile
import unittest

from ocr_service.engines import PaddleOcrEngine, StaticOcrEngine
from ocr_service.settings import OcrSettings


class EnginesTest(unittest.TestCase):
    def test_static_engine_returns_canonical_result(self):
        raw = {"rec_texts": ["任务"], "rec_scores": [0.88], "rec_boxes": [[1, 2, 3, 4]]}
        engine = StaticOcrEngine(raw)
        with tempfile.NamedTemporaryFile(suffix=".png") as image:
            result = engine.recognize(image.name, "trace_1")
        self.assertEqual("任务", result.plainText)
        self.assertEqual("trace_1", result.traceId)

    def test_paddle_constructor_candidates_include_profile_models(self):
        engine = PaddleOcrEngine(OcrSettings(model_profile="server"))
        candidates = engine._constructor_candidates()
        self.assertEqual("PP-OCRv5_server_det", candidates[0]["text_detection_model_name"])
        self.assertEqual("PP-OCRv5_server_rec", candidates[0]["text_recognition_model_name"])

    def test_paddle_constructor_candidates_include_legacy_fallback(self):
        engine = PaddleOcrEngine(OcrSettings(lang="ch", use_textline_orientation=True))
        candidates = engine._constructor_candidates()
        self.assertIn({"use_angle_cls": True, "lang": "ch"}, candidates)


if __name__ == "__main__":
    unittest.main()

