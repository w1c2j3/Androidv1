package com.shiliuai.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OcrResult {
    public String traceId;
    public String imageType;
    public String engine;
    public String engineVersion;
    public String modelProfile;
    public String lang;
    public Long latencyMs;
    public Double averageConfidence;
    public Double minConfidence;
    public int width;
    public int height;
    public List<OcrBlock> blocks = new ArrayList<>();
    public String plainText;
    public Map<String, Object> quality = new LinkedHashMap<>();
}
