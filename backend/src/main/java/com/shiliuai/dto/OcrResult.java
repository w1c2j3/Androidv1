package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OcrResult {
    public String traceId;
    public String imageType;
    public int width;
    public int height;
    public List<OcrBlock> blocks = new ArrayList<>();
    public String plainText;
    public Map<String, Object> quality = new LinkedHashMap<>();
}
