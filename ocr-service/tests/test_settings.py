import os
import unittest
from unittest.mock import patch

from ocr_service.settings import OcrSettings, bool_env, int_env


class SettingsTest(unittest.TestCase):
    def test_bool_env_true_values(self):
        for value in ["1", "true", "YES", "on", "y"]:
            with patch.dict(os.environ, {"X_BOOL": value}):
                self.assertTrue(bool_env("X_BOOL", False))

    def test_bool_env_false_value(self):
        with patch.dict(os.environ, {"X_BOOL": "false"}):
            self.assertFalse(bool_env("X_BOOL", True))

    def test_bool_env_default_when_missing(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertTrue(bool_env("X_BOOL", True))

    def test_int_env_parses_number(self):
        with patch.dict(os.environ, {"X_INT": "32"}):
            self.assertEqual(32, int_env("X_INT", 1))

    def test_int_env_default_on_invalid(self):
        with patch.dict(os.environ, {"X_INT": "bad"}):
            self.assertEqual(7, int_env("X_INT", 7))

    def test_settings_defaults(self):
        settings = OcrSettings()
        self.assertEqual("paddleocr", settings.engine)
        self.assertEqual("ch", settings.lang)
        self.assertEqual("mobile", settings.model_profile)
        self.assertFalse(settings.enable_mkldnn)
        self.assertEqual(15 * 1024 * 1024, settings.max_file_bytes)

    def test_settings_from_env(self):
        env = {
            "OCR_LANG": "en",
            "OCR_ENGINE": "rapidocr",
            "OCR_MODEL_PROFILE": "server",
            "OCR_USE_TEXTLINE_ORIENTATION": "false",
            "OCR_USE_DOC_ORIENTATION": "true",
            "OCR_USE_DOC_UNWARPING": "true",
            "OCR_ENABLE_MKLDNN": "true",
            "OCR_MAX_FILE_MB": "9",
        }
        with patch.dict(os.environ, env, clear=True):
            settings = OcrSettings.from_env()
        self.assertEqual("en", settings.lang)
        self.assertEqual("rapidocr", settings.engine)
        self.assertEqual("server", settings.model_profile)
        self.assertFalse(settings.use_textline_orientation)
        self.assertTrue(settings.use_doc_orientation)
        self.assertTrue(settings.use_doc_unwarping)
        self.assertTrue(settings.enable_mkldnn)
        self.assertEqual(9 * 1024 * 1024, settings.max_file_bytes)

    def test_mobile_model_names(self):
        settings = OcrSettings(model_profile="mobile")
        self.assertEqual("PP-OCRv5_mobile_det", settings.detection_model_name)
        self.assertEqual("PP-OCRv5_mobile_rec", settings.recognition_model_name)

    def test_server_model_names(self):
        settings = OcrSettings(model_profile="server")
        self.assertEqual("PP-OCRv5_server_det", settings.detection_model_name)
        self.assertEqual("PP-OCRv5_server_rec", settings.recognition_model_name)


if __name__ == "__main__":
    unittest.main()
