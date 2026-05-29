package com.shiliuai.dto;

import java.util.Map;

public class OcrRequest {
    public String traceId;
    public String imageFileId;
    public String imagePath;
    public String source;
    public Map<String, Object> hints;
}
