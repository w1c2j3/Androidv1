import tempfile
import unittest
from pathlib import Path

from ocr_service.converter import (
    build_ocr_result,
    classify_image_type,
    flatten_paddle_items,
    normalize_confidence,
    normalize_raw,
    parse_blocks,
    parse_paddle_line,
    parse_text_score,
    polygon_to_bbox,
)


class ConverterTest(unittest.TestCase):
    def test_polygon_to_bbox_from_quadrilateral(self):
        points = [[120.2, 410.7], [920.1, 410.2], [920.5, 468.4], [120.0, 468.9]]
        self.assertEqual([120, 410, 920, 469], polygon_to_bbox(points))

    def test_polygon_to_bbox_from_flat_box(self):
        self.assertEqual([1, 2, 3, 4], polygon_to_bbox([1, 2, 3, 4]))

    def test_polygon_to_bbox_from_dict_box(self):
        self.assertEqual([10, 20, 30, 40], polygon_to_bbox({"x1": 10, "y1": 20, "x2": 30, "y2": 40}))

    def test_polygon_to_bbox_from_dict_points(self):
        self.assertEqual([1, 2, 8, 9], polygon_to_bbox({"points": [{"x": 1, "y": 9}, {"x": 8, "y": 2}]}))

    def test_polygon_to_bbox_invalid_returns_zero_box(self):
        self.assertEqual([0, 0, 0, 0], polygon_to_bbox("bad"))

    def test_normalize_confidence_clamps_low(self):
        self.assertEqual(0.0, normalize_confidence(-1))

    def test_normalize_confidence_clamps_high(self):
        self.assertEqual(1.0, normalize_confidence(3))

    def test_normalize_confidence_handles_invalid(self):
        self.assertEqual(0.0, normalize_confidence("bad"))

    def test_parse_text_score_from_tuple(self):
        self.assertEqual(("hello", 0.87), parse_text_score((" hello ", 0.87)))

    def test_parse_text_score_from_dict(self):
        self.assertEqual(("hello", 0.91), parse_text_score({"text": "hello", "score": 0.91}))

    def test_parse_classic_paddle_single_page(self):
        raw = [
            [
                [[[0, 0], [10, 0], [10, 10], [0, 10]], ("第一行", 0.95)],
                [[[0, 12], [10, 12], [10, 20], [0, 20]], ("第二行", 0.93)],
            ]
        ]
        blocks = parse_blocks(raw)
        self.assertEqual(["第一行", "第二行"], [block.text for block in blocks])
        self.assertEqual([0, 0, 10, 10], blocks[0].bbox)

    def test_parse_classic_paddle_multiple_pages(self):
        raw = [
            [[[[0, 0], [10, 0], [10, 10], [0, 10]], ("第一页", 0.95)]],
            [[[[0, 0], [20, 0], [20, 10], [0, 10]], ("第二页", 0.96)]],
        ]
        blocks = parse_blocks(raw)
        self.assertEqual(["第一页", "第二页"], [block.text for block in blocks])
        self.assertEqual(["b1", "b2"], [block.id for block in blocks])

    def test_parse_dict_rec_texts_and_polys(self):
        raw = {
            "rec_texts": ["A", "B"],
            "rec_scores": [0.8, 0.9],
            "rec_polys": [
                [[0, 0], [10, 0], [10, 10], [0, 10]],
                [[0, 20], [10, 20], [10, 30], [0, 30]],
            ],
        }
        blocks = parse_blocks(raw)
        self.assertEqual(["A", "B"], [block.text for block in blocks])
        self.assertEqual([0, 20, 10, 30], blocks[1].bbox)

    def test_parse_dict_with_res_wrapper(self):
        raw = {"res": {"rec_texts": ["包装"], "rec_scores": [0.7], "rec_boxes": [[1, 2, 3, 4]]}}
        blocks = parse_blocks(raw)
        self.assertEqual("包装", blocks[0].text)
        self.assertEqual([1, 2, 3, 4], blocks[0].bbox)

    def test_parse_dict_with_data_wrapper(self):
        raw = {"data": {"blocks": [{"id": "x", "text": "正文", "bbox": [1, 2, 3, 4], "confidence": 0.6}]}}
        blocks = parse_blocks(raw)
        self.assertEqual("正文", blocks[0].text)
        self.assertEqual("x", blocks[0].id)

    def test_parse_list_of_result_dicts(self):
        raw = [{"res": {"rec_texts": ["A"], "rec_scores": [0.8], "rec_boxes": [[1, 2, 3, 4]]}}]
        blocks = parse_blocks(raw)
        self.assertEqual("A", blocks[0].text)
        self.assertEqual("b1", blocks[0].id)

    def test_parse_blocks_skips_blank_lines(self):
        raw = {"rec_texts": ["", "有效"], "rec_scores": [0.8, 0.9], "rec_boxes": [[], [1, 2, 3, 4]]}
        blocks = parse_blocks(raw)
        self.assertEqual(["有效"], [block.text for block in blocks])

    def test_parse_paddle_line_returns_none_for_bad_shape(self):
        self.assertIsNone(parse_paddle_line(["bad"], 1))

    def test_flatten_paddle_items_keeps_line_order(self):
        line1 = [[[0, 0], [1, 0], [1, 1], [0, 1]], ("A", 0.8)]
        line2 = [[[0, 2], [1, 2], [1, 3], [0, 3]], ("B", 0.9)]
        self.assertEqual([line1, line2], flatten_paddle_items([[line1], [line2]]))

    def test_normalize_raw_reads_json_property(self):
        class Result:
            json = {"res": {"rec_texts": ["A"]}}

        self.assertEqual({"res": {"rec_texts": ["A"]}}, normalize_raw(Result()))

    def test_normalize_raw_reads_json_method(self):
        class Result:
            def json(self):
                return {"res": {"rec_texts": ["A"]}}

        self.assertEqual({"res": {"rec_texts": ["A"]}}, normalize_raw(Result()))

    def test_build_result_joins_plain_text(self):
        raw = {"rec_texts": ["第一行", "第二行"], "rec_scores": [0.8, 0.9], "rec_boxes": [[1, 2, 3, 4], [5, 6, 7, 8]]}
        with tempfile.NamedTemporaryFile(suffix=".png") as image:
            result = build_ocr_result("trace_1", image.name, raw, {"expectedScene": "auto"})
        self.assertEqual("第一行\n第二行", result.plainText)
        self.assertEqual("trace_1", result.traceId)

    def test_build_result_uses_hint_scene(self):
        with tempfile.NamedTemporaryFile(suffix=".png") as image:
            result = build_ocr_result("trace_1", image.name, {}, {"expectedScene": "document_screenshot"})
        self.assertEqual("document_screenshot", result.imageType)

    def test_classify_link_text(self):
        self.assertEqual("link_or_qrcode", classify_image_type({"expectedScene": "auto"}, "https://example.com"))

    def test_classify_empty_text(self):
        self.assertEqual("unknown", classify_image_type({}, ""))


if __name__ == "__main__":
    unittest.main()

