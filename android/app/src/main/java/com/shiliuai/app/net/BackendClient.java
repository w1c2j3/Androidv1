package com.shiliuai.app.net;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.shiliuai.app.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 后端 HTTP 客户端。
 *
 * - 抽掉所有 UI 关心点，纯 IO。
 * - 所有方法都抛 {@link IOException}，由 {@link Poller} / 屏幕负责调度和展示。
 * - multipart 文件名按 RFC 5987 编码，修复非 ASCII 名称损坏（旧版 Bug 3）。
 */
public final class BackendClient {

    private final String baseUrl;
    private final String adminToken;

    public BackendClient(String baseUrl, String adminToken) {
        this.baseUrl = sanitize(baseUrl);
        this.adminToken = adminToken == null ? "" : adminToken;
    }

    public static BackendClient fromBuildConfig() {
        return new BackendClient(
                BuildConfig.SHILIU_DEFAULT_BACKEND_URL,
                BuildConfig.SHILIU_DEFAULT_ADMIN_TOKEN);
    }

    public String baseUrl() { return baseUrl; }

    public String request(String method, String path, String jsonBody) throws IOException {
        return request(method, path, jsonBody, 30_000);
    }

    public String request(String method, String path, String jsonBody, int readTimeoutMillis) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(readTimeoutMillis);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", "application/json");
            applyAuth(conn);
            if (jsonBody != null) {
                byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Content-Length", String.valueOf(payload.length));
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(payload);
                }
            }
            return readBody(conn);
        } catch (IOException e) {
            String detail = e.getMessage() == null ? e.toString() : e.getMessage();
            throw new IOException("请求失败：" + method + " " + path
                    + "\n后端：" + baseUrl + "\n原因：" + detail, e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public String uploadMultipart(Context c, String path, Uri uri) throws IOException {
        byte[] bytes = readAll(c, uri);
        String displayName = readDisplayName(c, uri);
        // 用 ContentResolver 的真实 MIME，HEIC/PNG/WebP 都能保留正确类型；
        // 老版本写死 image/jpeg 让后端 Files.probeContentType 错位。fallback 才回 image/jpeg。
        String mime = resolveMime(c, uri, displayName);

        String boundary = "----ShiliuAndroid" + System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(120_000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            applyAuth(conn);
            conn.setDoOutput(true);

            try (OutputStream out = conn.getOutputStream()) {
                writeHeader(out, "--" + boundary + "\r\n");
                writeHeader(out, "Content-Disposition: form-data; name=\"file\"; "
                        + encodeFilenameRfc5987(displayName) + "\r\n");
                writeHeader(out, "Content-Type: " + mime + "\r\n\r\n");
                out.write(bytes);
                writeHeader(out, "\r\n--" + boundary + "--\r\n");
            }
            return readBody(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String resolveMime(Context c, Uri uri, String displayName) {
        try {
            String fromResolver = c.getContentResolver().getType(uri);
            if (fromResolver != null && fromResolver.startsWith("image/")) {
                return fromResolver;
            }
        } catch (Exception ignored) { }
        // 文件名后缀兜底
        String lower = displayName == null ? "" : displayName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".heic") || lower.endsWith(".heif")) return "image/heic";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }

    private void applyAuth(HttpURLConnection conn) {
        if (!adminToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + adminToken);
        }
    }

    private static String readBody(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = stream == null ? "" : readString(stream);
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + "\n" + body);
        }
        return body;
    }

    private static byte[] readAll(Context c, Uri uri) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("无法读取图片内容");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    private static String readString(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** RFC 5987 文件名编码：兼顾 ASCII fallback 和 UTF-8 真实名。修复旧版 Bug 3。 */
    private static String encodeFilenameRfc5987(String name) {
        String safe = name == null ? "image.jpg" : name.replace("\"", "").replace("\r", "").replace("\n", "");
        String asciiFallback = safe.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded;
        try {
            encoded = URLEncoder.encode(safe, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            encoded = asciiFallback;
        }
        return "filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
    }

    /** ISO-8859-1，因为 multipart header 不允许 UTF-8。 */
    private static void writeHeader(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String readDisplayName(Context c, Uri uri) {
        try (Cursor cursor = c.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) { }
        return "image.jpg";
    }

    private static String sanitize(String base) {
        String b = (base == null || base.isEmpty()) ? "http://10.0.2.2:8000" : base;
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }
}
