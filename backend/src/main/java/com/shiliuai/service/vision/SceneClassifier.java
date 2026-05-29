package com.shiliuai.service.vision;

import com.shiliuai.dto.OcrResult;
import org.springframework.stereotype.Service;

@Service
public class SceneClassifier {
    public String classify(OcrResult ocrResult, String sceneHint) {
        if (sceneHint != null && !sceneHint.isBlank() && !"auto".equalsIgnoreCase(sceneHint)) {
            return switch (sceneHint) {
                case "chat" -> "chat_screenshot";
                case "table" -> "spreadsheet_screenshot";
                case "whiteboard" -> "whiteboard_photo";
                case "document" -> "document_screenshot";
                default -> sceneHint;
            };
        }
        String text = ocrResult.plainText == null ? "" : ocrResult.plainText;
        if (text.contains("http://") || text.contains("https://")) {
            return "chat_screenshot";
        }
        if (text.contains("合计") || text.contains("表格") || text.contains("金额")) {
            return "spreadsheet_screenshot";
        }
        if (text.contains("异常") || text.contains("error") || text.contains("Exception")) {
            return "app_error_screenshot";
        }
        return ocrResult.imageType == null ? "unknown" : ocrResult.imageType;
    }
}
