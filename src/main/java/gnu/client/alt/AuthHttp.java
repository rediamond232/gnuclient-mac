package gnu.client.alt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Minimal Java 8 HTTP helpers for Microsoft / Xbox / Minecraft auth.
 */
final class AuthHttp {

    private static final int TIMEOUT_MS = 20000;
    private static final JsonParser PARSER = new JsonParser();

    private AuthHttp() {
    }

    static JsonObject postJson(String url, String jsonBody) throws IOException {
        return postJson(url, jsonBody, null);
    }

    static JsonObject postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = open(url, "POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        applyHeaders(conn, headers);
        writeBody(conn, jsonBody == null ? "" : jsonBody);
        return readJson(conn);
    }

    static JsonObject postForm(String url, Map<String, String> form) throws IOException {
        return postForm(url, form, null);
    }

    static JsonObject postForm(String url, Map<String, String> form, Map<String, String> headers)
            throws IOException {
        HttpURLConnection conn = open(url, "POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");
        applyHeaders(conn, headers);
        writeBody(conn, encodeForm(form));
        return readJson(conn);
    }

    /** Like {@link #postForm} but returns JSON for HTTP error bodies (device-code poll). */
    static JsonObject postFormLenient(String url, Map<String, String> form) throws IOException {
        HttpURLConnection conn = open(url, "POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");
        writeBody(conn, encodeForm(form));
        String body = readBody(conn);
        if (body == null || body.isEmpty()) {
            throw new IOException("Empty response (" + conn.getResponseCode() + ") from " + conn.getURL());
        }
        JsonElement el = PARSER.parse(body);
        if (!el.isJsonObject()) {
            throw new IOException("Expected JSON object: " + body);
        }
        return el.getAsJsonObject();
    }

    static JsonObject getJson(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = open(url, "GET");
        conn.setRequestProperty("Accept", "application/json");
        applyHeaders(conn, headers);
        return readJson(conn);
    }

    /**
     * GET that does not follow redirects. Returns status, location, body, and final URL.
     */
    static RedirectResult getNoRedirect(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = open(url, "GET");
        conn.setInstanceFollowRedirects(false);
        applyHeaders(conn, headers);
        int code = conn.getResponseCode();
        String location = conn.getHeaderField("Location");
        String body = readBody(conn);
        return new RedirectResult(code, location, body, conn.getURL().toString());
    }

    static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    static String encodeForm(Map<String, String> form) {
        if (form == null || form.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }
        return sb.toString();
    }

    static String requireString(JsonObject obj, String key) throws IOException {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            throw new IOException("Missing field: " + key);
        }
        return obj.get(key).getAsString();
    }

    static String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        JsonElement el = obj.get(key);
        return el.isJsonPrimitive() ? el.getAsString() : "";
    }

    private static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        return conn;
    }

    private static void applyHeaders(HttpURLConnection conn, Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
    }

    private static void writeBody(HttpURLConnection conn, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Length", Integer.toString(bytes.length));
        OutputStream out = null;
        try {
            out = conn.getOutputStream();
            out.write(bytes);
            out.flush();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static JsonObject readJson(HttpURLConnection conn) throws IOException {
        String body = readBody(conn);
        int code = conn.getResponseCode();
        if (body == null || body.isEmpty()) {
            throw new IOException("Empty response (" + code + ") from " + conn.getURL());
        }
        JsonElement el = PARSER.parse(body);
        if (!el.isJsonObject()) {
            throw new IOException("Expected JSON object (" + code + "): " + truncate(body));
        }
        JsonObject obj = el.getAsJsonObject();
        if (code >= 400) {
            String err = optString(obj, "error");
            String desc = optString(obj, "error_description");
            String msg = optString(obj, "Message");
            if (msg.isEmpty()) {
                msg = optString(obj, "errorMessage");
            }
            String detail = !desc.isEmpty() ? desc : (!msg.isEmpty() ? msg : truncate(body));
            throw new IOException((err.isEmpty() ? "HTTP " + code : err) + ": " + detail);
        }
        return obj;
    }

    private static String readBody(HttpURLConnection conn) throws IOException {
        InputStream in = null;
        try {
            int code = conn.getResponseCode();
            in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                in = conn.getErrorStream();
            }
            if (in == null) {
                return "";
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) >= 0) {
                buf.write(chunk, 0, n);
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 180 ? s : s.substring(0, 180) + "…";
    }

    static final class RedirectResult {
        final int status;
        final String location;
        final String body;
        final String url;

        RedirectResult(int status, String location, String body, String url) {
            this.status = status;
            this.location = location;
            this.body = body;
            this.url = url;
        }
    }
}
