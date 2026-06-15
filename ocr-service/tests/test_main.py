import unittest

from ocr_service.main import health, root


class MainHttpTest(unittest.TestCase):
    def test_root_returns_service_endpoints(self):
        body = root()
        self.assertEqual("ok", body["status"])
        self.assertEqual("shiliu-ocr-service", body["service"])
        self.assertEqual("/health", body["endpoints"]["health"])
        self.assertEqual("/ocr", body["endpoints"]["ocr"])

    def test_health_returns_model_profile(self):
        body = health()
        self.assertEqual("ok", body["status"])
        self.assertEqual("paddleocr", body["engine"])
        self.assertTrue(body["localModel"])
        self.assertIn("modelProfile", body)
        self.assertIn("lang", body)


if __name__ == "__main__":
    unittest.main()
