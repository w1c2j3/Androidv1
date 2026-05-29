package com.shiliuai.app;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class ShiliuApiClient {
    private final String baseUrl;
    private final String adminToken;

    ShiliuApiClient(String baseUrl, String adminToken) {
        this.baseUrl = trimEnd(isBlank(baseUrl) ? "http://10.0.2.2:8080" : baseUrl.trim());
        this.adminToken = adminToken == null ? "" : adminToken.trim();
    }

    JSONObject get(String path) throws Exception {
        HttpURLConnection connection = open(path, "GET");
        return readJson(connection);
    }

    JSONObject postJson(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = open(path, "POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readJson(connection);
    }

    JSONObject patchJson(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = open(path, "PATCH");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readJson(connection);
    }

    JSONObject uploadImage(Context context, Uri uri) throws Exception {
        String boundary = "----shiliu-" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection connection = open("/api/v1/vision/upload?source=android_upload&sceneHint=auto", "POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setDoOutput(true);

        try (OutputStream output = connection.getOutputStream()) {
            writeAscii(output, "--" + boundary + "\r\n");
            writeAscii(output, "Content-Disposition: form-data; name=\"file\"; filename=\"android-upload.jpg\"\r\n");
            writeAscii(output, "Content-Type: application/octet-stream\r\n\r\n");
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("无法读取选择的图片");
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
            writeAscii(output, "\r\n--" + boundary + "--\r\n");
        }
        return readJson(connection);
    }

    private HttpURLConnection open(String path, String method) throws IOException {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + normalizedPath).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        if (!isBlank(adminToken)) {
            connection.setRequestProperty("Authorization", "Bearer " + adminToken);
        }
        return connection;
    }

    private static JSONObject readJson(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = readAll(stream);
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + body);
        }
        return isBlank(body) ? new JSONObject() : new JSONObject(body);
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private static void writeAscii(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String trimEnd(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
