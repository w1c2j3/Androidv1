package com.shiliuai.dto;

import java.util.Map;

public class ExtractRequest {
    public String traceId;
    public String scene;
    public String plainText;
    public OcrResult ocrResult;
    public Map<String, Object> options;
}
