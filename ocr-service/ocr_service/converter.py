from collections.abc import Iterable
from pathlib import Path
from typing import Any

from .image_utils import image_quality, image_size
from .schemas import OcrBlock, OcrResult


def build_ocr_result(
    trace_id: str,
    image_path: str | Path,
    raw_output: Any,
    hints: dict[str, Any] | None = None,
) -> OcrResult:
    width, height = image_size(image_path)
    blocks = parse_blocks(raw_output)
    plain_text = "\n".join(block.text for block in blocks if block.text.strip())
    confidences = [block.confidence for block in blocks]
    return OcrResult(
        traceId=trace_id,
        imageType=classify_image_type(hints, plain_text),
        width=width,
        height=height,
        averageConfidence=round(sum(confidences) / len(confidences), 6) if confidences else 0.0,
        minConfidence=round(min(confidences), 6) if confidences else 0.0,
        blocks=blocks,
        plainText=plain_text,
        quality=image_quality(image_path),
    )


def classify_image_type(hints: dict[str, Any] | None, plain_text: str) -> str:
    expected = ""
    if hints:
        expected = str(hints.get("expectedScene") or hints.get("sceneHint") or "").strip()
    if expected and expected not in {"auto", "unknown"}:
        return expected
    if "http://" in plain_text or "https://" in plain_text:
        return "link_or_qrcode"
    if "\t" in plain_text or "|" in plain_text:
        return "spreadsheet_screenshot"
    return "chat_screenshot" if plain_text.strip() else "unknown"


def parse_blocks(raw_output: Any) -> list[OcrBlock]:
    normalized = normalize_raw(raw_output)
    if normalized is None:
        return []
    if isinstance(normalized, dict):
        return _parse_dict_blocks(normalized)
    if isinstance(normalized, list):
        return _parse_list_blocks(normalized)
    return []


def normalize_raw(raw_output: Any) -> Any:
    if raw_output is None:
        return None
    if hasattr(raw_output, "json"):
        value = raw_output.json
        if callable(value):
            value = value()
        return normalize_raw(value)
    if hasattr(raw_output, "to_dict"):
        return normalize_raw(raw_output.to_dict())
    if isinstance(raw_output, tuple):
        return list(raw_output)
    if isinstance(raw_output, list):
        return [normalize_raw(item) for item in raw_output]
    if isinstance(raw_output, dict):
        return {key: normalize_raw(value) for key, value in raw_output.items()}
    return raw_output


def _parse_dict_blocks(data: dict[str, Any]) -> list[OcrBlock]:
    if isinstance(data.get("data"), dict):
        return _parse_dict_blocks(data["data"])
    if isinstance(data.get("res"), dict):
        return _parse_dict_blocks(data["res"])
    if isinstance(data.get("blocks"), list):
        return [_block_from_dict(item, index) for index, item in enumerate(data["blocks"], start=1)]

    texts = first_list(data, ["rec_texts", "texts", "text"])
    scores = first_list(data, ["rec_scores", "scores", "confidence"])
    boxes = first_list(data, ["rec_polys", "dt_polys", "polys", "rec_boxes", "boxes", "bbox"])
    if not texts:
        return []

    blocks: list[OcrBlock] = []
    for index, text in enumerate(texts, start=1):
        clean_text = normalize_text(text)
        if not clean_text:
            continue
        box = boxes[index - 1] if index - 1 < len(boxes) else []
        score = scores[index - 1] if index - 1 < len(scores) else 0.0
        blocks.append(
            OcrBlock(
                id=f"b{len(blocks) + 1}",
                type="text_line",
                text=clean_text,
                bbox=polygon_to_bbox(box),
                confidence=normalize_confidence(score),
            )
        )
    return blocks


def _parse_list_blocks(items: list[Any]) -> list[OcrBlock]:
    flattened = flatten_paddle_items(items)
    blocks: list[OcrBlock] = []
    for item in flattened:
        if isinstance(item, dict):
            dict_blocks = _parse_dict_blocks(item)
            if dict_blocks:
                blocks.extend(reindex_blocks(dict_blocks, len(blocks) + 1))
                continue
        block = parse_paddle_line(item, len(blocks) + 1)
        if block is not None and block.text.strip():
            blocks.append(block)
    return blocks


def flatten_paddle_items(items: list[Any]) -> list[Any]:
    if not items:
        return []
    if all(is_paddle_line(item) for item in items):
        return items
    flattened: list[Any] = []
    for item in items:
        if isinstance(item, list):
            flattened.extend(flatten_paddle_items(item))
        elif item is not None:
            flattened.append(item)
    return flattened


def is_paddle_line(item: Any) -> bool:
    return (
        isinstance(item, (list, tuple))
        and len(item) >= 2
        and looks_like_box(item[0])
        and looks_like_text_score(item[1])
    )


def parse_paddle_line(item: Any, index: int) -> OcrBlock | None:
    if isinstance(item, dict):
        return _block_from_dict(item, index)
    if not is_paddle_line(item):
        return None
    text, confidence = parse_text_score(item[1])
    if not text:
        return None
    return OcrBlock(
        id=f"b{index}",
        type="text_line",
        text=text,
        bbox=polygon_to_bbox(item[0]),
        confidence=confidence,
    )


def _block_from_dict(data: dict[str, Any], index: int) -> OcrBlock:
    text = normalize_text(data.get("text") or data.get("plainText") or data.get("value") or "")
    bbox = data.get("bbox") or data.get("box") or data.get("poly") or data.get("points") or []
    return OcrBlock(
        id=str(data.get("id") or f"b{index}"),
        type=str(data.get("type") or "text_line"),
        text=text,
        bbox=polygon_to_bbox(bbox),
        confidence=normalize_confidence(data.get("confidence", data.get("score", 0.0))),
    )


def reindex_blocks(blocks: list[OcrBlock], start: int) -> list[OcrBlock]:
    indexed: list[OcrBlock] = []
    for offset, block in enumerate(blocks):
        indexed.append(
            OcrBlock(
                id=f"b{start + offset}",
                type=block.type,
                text=block.text,
                bbox=block.bbox,
                confidence=block.confidence,
            )
        )
    return indexed


def parse_text_score(value: Any) -> tuple[str, float]:
    if isinstance(value, dict):
        return normalize_text(value.get("text", "")), normalize_confidence(value.get("confidence", value.get("score", 0.0)))
    if isinstance(value, (list, tuple)) and value:
        text = normalize_text(value[0])
        score = value[1] if len(value) > 1 else 0.0
        return text, normalize_confidence(score)
    return normalize_text(value), 0.0


def polygon_to_bbox(points: Any) -> list[int]:
    if points is None:
        return [0, 0, 0, 0]
    if isinstance(points, dict):
        if all(key in points for key in ("x1", "y1", "x2", "y2")):
            return [to_int(points["x1"]), to_int(points["y1"]), to_int(points["x2"]), to_int(points["y2"])]
        points = points.get("points") or points.get("bbox") or []
    if isinstance(points, (list, tuple)) and len(points) == 4 and all(is_number(item) for item in points):
        return [to_int(item) for item in points]

    pairs: list[tuple[float, float]] = []
    if isinstance(points, Iterable) and not isinstance(points, (str, bytes)):
        for point in points:
            if isinstance(point, dict) and "x" in point and "y" in point:
                pairs.append((float(point["x"]), float(point["y"])))
            elif isinstance(point, (list, tuple)) and len(point) >= 2 and is_number(point[0]) and is_number(point[1]):
                pairs.append((float(point[0]), float(point[1])))
    if not pairs:
        return [0, 0, 0, 0]
    xs = [point[0] for point in pairs]
    ys = [point[1] for point in pairs]
    return [to_int(min(xs)), to_int(min(ys)), to_int(max(xs)), to_int(max(ys))]


def first_list(data: dict[str, Any], keys: list[str]) -> list[Any]:
    for key in keys:
        value = data.get(key)
        if isinstance(value, list):
            return value
        if value is not None and not isinstance(value, (str, bytes)):
            try:
                return list(value)
            except TypeError:
                continue
    return []


def looks_like_box(value: Any) -> bool:
    if isinstance(value, dict):
        return bool({"bbox", "points", "x1", "y1", "x2", "y2"} & set(value.keys()))
    return isinstance(value, (list, tuple)) and len(value) >= 4


def looks_like_text_score(value: Any) -> bool:
    if isinstance(value, dict):
        return "text" in value or "value" in value
    return isinstance(value, (list, tuple)) and bool(value) or isinstance(value, str)


def normalize_text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def normalize_confidence(value: Any) -> float:
    try:
        confidence = float(value)
    except (TypeError, ValueError):
        return 0.0
    if confidence < 0.0:
        return 0.0
    if confidence > 1.0:
        return 1.0
    return round(confidence, 6)


def is_number(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def to_int(value: Any) -> int:
    try:
        return int(round(float(value)))
    except (TypeError, ValueError):
        return 0
