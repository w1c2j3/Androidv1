from pathlib import Path
from typing import Any


def image_size(path: str | Path) -> tuple[int, int]:
    try:
        from PIL import Image

        with Image.open(path) as image:
            return image.size
    except Exception:
        return 0, 0


def image_quality(path: str | Path) -> dict[str, Any]:
    try:
        from PIL import Image, ImageStat

        with Image.open(path) as image:
            grayscale = image.convert("L")
            stat = ImageStat.Stat(grayscale)
            brightness = round((stat.mean[0] / 255.0), 4)
            return {
                "brightness": brightness,
                "blur": 0.0,
                "rotationFixed": False,
            }
    except Exception:
        return {
            "brightness": 0.0,
            "blur": 0.0,
            "rotationFixed": False,
        }

