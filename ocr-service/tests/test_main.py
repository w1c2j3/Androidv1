import unittest

from fastapi.testclient import TestClient

from ocr_service.main import app


class MainHttpTest(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)

    def test_root_returns_service_endpoints(self):
        response = self.client.get("/")
        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("ok", body["status"])
        self.assertEqual("shiliu-ocr-service", body["service"])
        self.assertEqual("/health", body["endpoints"]["health"])
        self.assertEqual("/ocr", body["endpoints"]["ocr"])

    def test_health_returns_model_profile(self):
        response = self.client.get("/health")
        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("ok", body["status"])
        self.assertIn("modelProfile", body)
        self.assertIn("lang", body)


if __name__ == "__main__":
    unittest.main()
