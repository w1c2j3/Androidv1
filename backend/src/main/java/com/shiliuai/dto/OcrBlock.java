package com.shiliuai.dto;

public class OcrBlock {
    public String id;
    public String type;
    public String text;
    public int[] bbox;
    public double confidence;

    public OcrBlock() {
    }

    public OcrBlock(String id, String type, String text, int[] bbox, double confidence) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.bbox = bbox;
        this.confidence = confidence;
    }
}
