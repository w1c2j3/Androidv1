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
    engine: str = ""
    engineVersion: str = ""
    modelProfile: str = ""
    lang: str = ""
    latencyMs: int = 0
    averageConfidence: float = 0.0
    minConfidence: float = 0.0
    blocks: list[OcrBlock] = field(default_factory=list)
    plainText: str = ""
    quality: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "traceId": self.traceId,
            "imageType": self.imageType,
            "engine": self.engine,
            "engineVersion": self.engineVersion,
            "modelProfile": self.modelProfile,
            "lang": self.lang,
            "latencyMs": self.latencyMs,
            "averageConfidence": self.averageConfidence,
            "minConfidence": self.minConfidence,
            "width": self.width,
            "height": self.height,
            "blocks": [block.to_dict() for block in self.blocks],
            "plainText": self.plainText,
            "quality": self.quality,
        }
