package gnu.client.alt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Login using an exported browser cookie file (Netscape cookies.txt or raw Cookie header).
 */
public final class CookieAuth {

    private static final String AUTHORIZE =
            "https://login.live.com/oauth20_authorize.srf"
                    + "?client_id=" + MicrosoftAuth.LAUNCHER_CLIENT_ID
                    + "&response_type=token"
                    + "&redirect_uri=https://login.live.com/oauth20_desktop.srf"
                    + "&scope=service::user.auth.xboxlive.com::MBI_SSL"
                    + "&display=touch"
                    + "&locale=en";

    private CookieAuth() {
    }

    public static MicrosoftAuth.AuthResult loginFromFile(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IOException("Cookie file not found.");
        }
        String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return loginFromCookieText(raw);
    }

    public static MicrosoftAuth.AuthResult loginFromCookieText(String text) throws IOException {
        String cookieHeader = toCookieHeader(text);
        if (cookieHeader.isEmpty()) {
            throw new IOException("No usable login.live.com cookies found.");
        }
        String accessToken = fetchMsAccessToken(cookieHeader);
        if (accessToken.isEmpty()) {
            throw new IOException(
                    "Could not obtain Microsoft token from cookies. Export fresh live.com cookies.");
        }
        return MicrosoftAuth.loginWithMsToken(accessToken, "", false);
    }

    static String toCookieHeader(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        // Already a Cookie header line
        if (!trimmed.contains("\n") && trimmed.contains("=")) {
            return trimmed;
        }
        Map<String, String> cookies = new LinkedHashMap<String, String>();
        String[] lines = trimmed.split("\\r?\\n");
        for (String line : lines) {
            parseCookieLine(line, cookies);
        }
        return joinCookies(cookies);
    }

    private static String fetchMsAccessToken(String cookieHeader) throws IOException {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Cookie", cookieHeader);
        headers.put("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

        String url = AUTHORIZE;
        for (int hop = 0; hop < 12; hop++) {
            AuthHttp.RedirectResult res = AuthHttp.getNoRedirect(url, headers);
            String token = firstNonEmpty(
                    MicrosoftAuth.extractAccessToken(res.location),
                    MicrosoftAuth.extractAccessToken(res.url),
                    MicrosoftAuth.extractAccessToken(res.body));
            if (!token.isEmpty()) {
                return token;
            }

            String code = firstNonEmpty(
                    MicrosoftAuth.extractCode(res.location),
                    MicrosoftAuth.extractCode(res.url));
            if (!code.isEmpty()) {
                return exchangeCodeForMsToken(code);
            }

            if (res.location == null || res.location.isEmpty()) {
                if (res.status >= 200 && res.status < 300) {
                    break;
                }
                throw new IOException("Cookie login stopped at HTTP " + res.status);
            }
            url = resolveRedirect(url, res.location);
        }
        return "";
    }

    private static String exchangeCodeForMsToken(String code) throws IOException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", MicrosoftAuth.LAUNCHER_CLIENT_ID);
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", "https://login.live.com/oauth20_desktop.srf");
        form.put("scope", "service::user.auth.xboxlive.com::MBI_SSL");
        return AuthHttp.requireString(
                AuthHttp.postForm("https://login.live.com/oauth20_token.srf", form),
                "access_token");
    }

    private static void parseCookieLine(String line, Map<String, String> out) {
        if (line == null) {
            return;
        }
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("#")) {
            return;
        }
        // Netscape: domain \t flag \t path \t secure \t expiry \t name \t value
        String[] parts = t.split("\t");
        if (parts.length >= 7) {
            String domain = parts[0].toLowerCase(Locale.ROOT);
            if (!domain.contains("live.com") && !domain.contains("microsoft.com")
                    && !domain.contains("xbox.com") && !domain.contains("microsoftonline.com")) {
                return;
            }
            out.put(parts[5], parts[6]);
            return;
        }
        int eq = t.indexOf('=');
        if (eq > 0) {
            String name = t.substring(0, eq).trim();
            String value = t.substring(eq + 1).trim();
            if (value.endsWith(";")) {
                value = value.substring(0, value.length() - 1).trim();
            }
            if (!name.isEmpty()) {
                out.put(name, value);
            }
        }
    }

    private static String joinCookies(Map<String, String> cookies) {
        if (cookies.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private static String resolveRedirect(String current, String location) {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        try {
            return new java.net.URL(new java.net.URL(current), location).toString();
        } catch (Exception e) {
            return location;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "";
    }
}
