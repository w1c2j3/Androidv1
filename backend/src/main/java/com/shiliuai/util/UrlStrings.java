package com.shiliuai.util;

public final class UrlStrings {
    private UrlStrings() {
    }

    public static String trimTrailingSlash(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isBlank() ? fallback : result;
    }
}
