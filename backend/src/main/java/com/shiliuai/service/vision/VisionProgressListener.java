package com.shiliuai.service.vision;

@FunctionalInterface
public interface VisionProgressListener {
    void onProgress(String traceId, String stage, int progress, String message);

    static VisionProgressListener noop() {
        return (traceId, stage, progress, message) -> {
        };
    }
}
