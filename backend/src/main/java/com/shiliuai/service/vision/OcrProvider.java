package com.shiliuai.service.vision;

import com.shiliuai.dto.OcrRequest;
import com.shiliuai.dto.OcrResult;

public interface OcrProvider {
    OcrResult recognize(OcrRequest request);
}
