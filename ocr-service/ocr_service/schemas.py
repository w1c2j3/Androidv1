from dataclasses import dataclass, field
from typing import Any


@dataclass
class OcrBlock:
    id: str
    type: str
    text: str
    bbox: list[int]
    confidence: float

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "type": self.type,
            "text": self.text,
            "bbox": self.bbox,
            "confidence": self.confidence,
        }


@dataclass
class OcrResult:
    traceId: str
    imageType: str
    width: int
    height: int
    blocks: list[OcrBlock] = field(default_factory=list)
    plainText: str = ""
    quality: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "traceId": self.traceId,
            "imageType": self.imageType,
            "width": self.width,
            "height": self.height,
            "blocks": [block.to_dict() for block in self.blocks],
            "plainText": self.plainText,
            "quality": self.quality,
        }

