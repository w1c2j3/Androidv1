import unittest

from ocr_service.http_utils import parse_hints, safe_suffix


class HttpUtilsTest(unittest.TestCase):
    def test_parse_hints_valid_object(self):
        self.assertEqual({"language": "zh-CN"}, parse_hints('{"language":"zh-CN"}'))

    def test_parse_hints_invalid_json_returns_empty(self):
        self.assertEqual({}, parse_hints("{bad"))

    def test_parse_hints_array_returns_empty(self):
        self.assertEqual({}, parse_hints("[1,2]"))

    def test_parse_hints_blank_returns_empty(self):
        self.assertEqual({}, parse_hints(" "))

    def test_safe_suffix_keeps_image_suffix(self):
        self.assertEqual(".png", safe_suffix("screen.PNG"))

    def test_safe_suffix_blocks_long_suffix(self):
        self.assertEqual(".bin", safe_suffix("screen.verylongsuffix"))

    def test_safe_suffix_blocks_missing_name(self):
        self.assertEqual(".bin", safe_suffix(None))

    def test_safe_suffix_blocks_weird_suffix(self):
        self.assertEqual(".bin", safe_suffix("screen.pn*g"))


if __name__ == "__main__":
    unittest.main()

