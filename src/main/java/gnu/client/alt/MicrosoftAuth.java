package gnu.client.alt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Microsoft → Xbox Live → XSTS → Minecraft Services authentication.
 */
public final class MicrosoftAuth {

    /** Official Minecraft Launcher client id (live.com / cookie flows). */
    public static final String LAUNCHER_CLIENT_ID = "00000000402b5328";

    /**
     * Public Azure AD client (current Prism Launcher MSA app).
     * Enabled for personal Microsoft accounts + localhost redirect.
     */
    public static final String AZURE_CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";

    /** @deprecated use {@link #AZURE_CLIENT_ID} */
    public static final String DEVICE_CLIENT_ID = AZURE_CLIENT_ID;

    private static final String AZURE_SCOPE = "XboxLive.SignIn XboxLive.offline_access";

    private static final String AUTHORIZE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String AZURE_TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String LIVE_TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private static final Pattern CODE_PATTERN = Pattern.compile("[?&]code=([^&]+)");
    private static final Pattern ERROR_PATTERN = Pattern.compile("[?&]error=([^&]+)");
    private static final Pattern ERROR_DESC_PATTERN = Pattern.compile("[?&]error_description=([^&]+)");
    private static final Pattern ACCESS_TOKEN_PATTERN =
            Pattern.compile("[#&?]access_token=([^&]+)");
    private static final Pattern REQUEST_LINE =
            Pattern.compile("^GET\\s+([^\\s]+)\\s+HTTP/", Pattern.CASE_INSENSITIVE);

    private MicrosoftAuth() {
    }

    /**
     * Browser login like typical alt managers: open Microsoft OAuth, user consents to Xbox
     * access, redirect lands on a local http://127.0.0.1 listener.
     */
    public static AuthResult loginWithLocalhostBrowser(BrowserHooks hooks, CancelFlag cancel)
            throws IOException {
        java.net.ServerSocket server = null;
        try {
            server = new java.net.ServerSocket();
            server.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
            server.setSoTimeout(1000);
            int port = server.getLocalPort();
            String redirectUri = "http://127.0.0.1:" + port;
            String authorizeUrl = AUTHORIZE_URL
                    + "?client_id=" + AuthHttp.encode(AZURE_CLIENT_ID)
                    + "&response_type=code"
                    + "&redirect_uri=" + AuthHttp.encode(redirectUri)
                    + "&scope=" + AuthHttp.encode(AZURE_SCOPE)
                    + "&prompt=select_account"
                    + "&response_mode=query";

            if (hooks != null) {
                hooks.onAuthorizeUrl(authorizeUrl);
            }

            String callbackPath = waitForOAuthCallback(server, cancel);
            String error = matchFirst(ERROR_PATTERN, callbackPath);
            if (!error.isEmpty()) {
                String desc = urlDecode(matchFirst(ERROR_DESC_PATTERN, callbackPath)).replace('+', ' ');
                throw new IOException(desc.isEmpty() ? "Microsoft login error: " + error : desc);
            }
            String code = extractCode(callbackPath);
            if (code.isEmpty()) {
                throw new IOException("No authorization code returned from Microsoft.");
            }
            return exchangeAzureAuthCode(code, redirectUri);
        } finally {
            if (server != null) {
                try {
                    server.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static AuthResult exchangeAzureAuthCode(String code, String redirectUri)
            throws IOException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", AZURE_CLIENT_ID);
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", redirectUri);
        form.put("scope", AZURE_SCOPE);
        JsonObject token = AuthHttp.postForm(AZURE_TOKEN_URL, form);
        return loginWithMsToken(
                AuthHttp.requireString(token, "access_token"),
                AuthHttp.optString(token, "refresh_token"),
                true);
    }

    private static String waitForOAuthCallback(java.net.ServerSocket server, CancelFlag cancel)
            throws IOException {
        long deadline = System.currentTimeMillis() + 5 * 60 * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (cancel != null && cancel.isCancelled()) {
                throw new IOException("Browser login cancelled.");
            }
            java.net.Socket socket = null;
            try {
                socket = server.accept();
            } catch (java.net.SocketTimeoutException timeout) {
                continue;
            }
            try {
                socket.setSoTimeout(5000);
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(socket.getInputStream(),
                                java.nio.charset.StandardCharsets.UTF_8));
                String requestLine = reader.readLine();
                // Drain headers
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    // ignore
                }
                if (requestLine == null) {
                    continue;
                }
                Matcher rm = REQUEST_LINE.matcher(requestLine);
                if (!rm.find()) {
                    writeHtml(socket, 400, "Bad request", "Unexpected request.");
                    continue;
                }
                String path = rm.group(1);
                // Ignore favicon / empty probes
                if (path.startsWith("/favicon") || path.equals("/")) {
                    if (!path.contains("code=") && !path.contains("error=")) {
                        writeHtml(socket, 204, "No Content", "");
                        continue;
                    }
                }
                if (path.contains("code=") || path.contains("error=")) {
                    writeHtml(socket, 200, "Login complete",
                            "<html><body style=\"font-family:sans-serif;background:#0c0e16;color:#f5f6fa;"
                                    + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0\">"
                                    + "<div style=\"text-align:center\"><h2>GNU Client</h2>"
                                    + "<p>Login complete. You can close this tab and return to the game.</p>"
                                    + "</div></body></html>");
                    return path;
                }
                writeHtml(socket, 404, "Not found", "Not an OAuth callback.");
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        throw new IOException("Timed out waiting for Microsoft login.");
    }

    private static void writeHtml(java.net.Socket socket, int status, String reason, String body)
            throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        java.io.OutputStream out = socket.getOutputStream();
        out.write(headers.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        if (bytes.length > 0) {
            out.write(bytes);
        }
        out.flush();
    }

    private static String matchFirst(Pattern pattern, String input) {
        if (input == null) {
            return "";
        }
        Matcher m = pattern.matcher(input);
        return m.find() ? m.group(1) : "";
    }

    public static AuthResult loginWithAuthCode(String codeOrRedirectUrl) throws IOException {
        String code = extractCode(codeOrRedirectUrl);
        if (code.isEmpty()) {
            throw new IOException("No authorization code found.");
        }
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", LAUNCHER_CLIENT_ID);
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", "https://login.live.com/oauth20_desktop.srf");
        form.put("scope", "service::user.auth.xboxlive.com::MBI_SSL");
        JsonObject token = AuthHttp.postForm(LIVE_TOKEN_URL, form);
        return loginWithMsToken(
                AuthHttp.requireString(token, "access_token"),
                AuthHttp.optString(token, "refresh_token"),
                false);
    }

    /** Refresh a device-code (Azure AD) Microsoft session. */
    public static AuthResult loginWithAzureRefreshToken(String refreshToken) throws IOException {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new IOException("Missing refresh token.");
        }
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", AZURE_CLIENT_ID);
        form.put("refresh_token", refreshToken.trim());
        form.put("grant_type", "refresh_token");
        form.put("scope", AZURE_SCOPE);
        JsonObject token = AuthHttp.postForm(AZURE_TOKEN_URL, form);
        String refresh = AuthHttp.optString(token, "refresh_token");
        if (refresh.isEmpty()) {
            refresh = refreshToken.trim();
        }
        return loginWithMsToken(AuthHttp.requireString(token, "access_token"), refresh, true);
    }

    /** Refresh a live.com / launcher-style session (cookie / legacy redirect). */
    public static AuthResult loginWithLiveRefreshToken(String refreshToken) throws IOException {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new IOException("Missing refresh token.");
        }
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", LAUNCHER_CLIENT_ID);
        form.put("refresh_token", refreshToken.trim());
        form.put("grant_type", "refresh_token");
        form.put("redirect_uri", "https://login.live.com/oauth20_desktop.srf");
        form.put("scope", "service::user.auth.xboxlive.com::MBI_SSL");
        JsonObject token = AuthHttp.postForm(LIVE_TOKEN_URL, form);
        String refresh = AuthHttp.optString(token, "refresh_token");
        if (refresh.isEmpty()) {
            refresh = refreshToken.trim();
        }
        return loginWithMsToken(AuthHttp.requireString(token, "access_token"), refresh, false);
    }

    /** @deprecated use {@link #loginWithAzureRefreshToken} or {@link #loginWithLiveRefreshToken} */
    public static AuthResult loginWithRefreshToken(String refreshToken) throws IOException {
        return loginWithAzureRefreshToken(refreshToken);
    }

    /**
     * @param azureStyle when true, prefixes the Xbox RpsTicket with {@code d=} (Azure AD tokens).
     */
    public static AuthResult loginWithMsToken(String msAccessToken, String refreshToken,
            boolean azureStyle) throws IOException {
        if (msAccessToken == null || msAccessToken.isEmpty()) {
            throw new IOException("Missing Microsoft access token.");
        }
        String rps = azureStyle ? "d=" + msAccessToken : msAccessToken;
        XblSession xbl = authenticateXbox(rps);
        return loginMinecraft(xbl, refreshToken == null ? "" : refreshToken);
    }

    public static String extractCode(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        Matcher m = CODE_PATTERN.matcher(trimmed);
        if (m.find()) {
            return urlDecode(m.group(1));
        }
        if (trimmed.matches("[\\w.-]+") && trimmed.length() > 20) {
            return trimmed;
        }
        return "";
    }

    public static String extractAccessToken(String url) {
        if (url == null) {
            return "";
        }
        Matcher m = ACCESS_TOKEN_PATTERN.matcher(url);
        if (m.find()) {
            return urlDecode(m.group(1));
        }
        return "";
    }

    private static AuthResult loginMinecraft(XblSession xbl, String refreshToken) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + xbl.uhs + ";" + xbl.xstsToken);
        JsonObject mc = AuthHttp.postJson(MC_LOGIN_URL, body.toString());
        String mcToken = AuthHttp.requireString(mc, "access_token");

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + mcToken);
        JsonObject profile = AuthHttp.getJson(MC_PROFILE_URL, headers);
        String name = AuthHttp.requireString(profile, "name");
        String id = AuthHttp.requireString(profile, "id");
        return new AuthResult(name, dashUuid(id), mcToken, refreshToken);
    }

    private static XblSession authenticateXbox(String rpsTicket) throws IOException {
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", rpsTicket);
        JsonObject xblBody = new JsonObject();
        xblBody.add("Properties", xblProps);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");
        JsonObject xbl = AuthHttp.postJson(XBL_URL, xblBody.toString());
        String xblToken = AuthHttp.requireString(xbl, "Token");
        String uhs = extractUhs(xbl);

        JsonObject xstsProps = new JsonObject();
        JsonArray userTokens = new JsonArray();
        userTokens.add(new JsonPrimitive(xblToken));
        xstsProps.add("UserTokens", userTokens);
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProps);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        JsonObject xsts = AuthHttp.postJson(XSTS_URL, xstsBody.toString());

        if (xsts.has("XErr") && !xsts.get("XErr").isJsonNull()) {
            long err = xsts.get("XErr").getAsLong();
            throw new IOException(xstsErrorMessage(err));
        }
        String xstsToken = AuthHttp.requireString(xsts, "Token");
        if (uhs.isEmpty()) {
            uhs = extractUhs(xsts);
        }
        if (uhs.isEmpty()) {
            throw new IOException("Xbox user hash missing.");
        }
        return new XblSession(uhs, xstsToken);
    }

    private static String extractUhs(JsonObject xboxResponse) {
        try {
            return xboxResponse.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui")
                    .get(0).getAsJsonObject()
                    .get("uhs").getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String xstsErrorMessage(long err) {
        if (err == 2148916233L) {
            return "Microsoft account has no Xbox profile. Open xbox.com once, then retry.";
        }
        if (err == 2148916235L) {
            return "Xbox Live is unavailable in this region.";
        }
        if (err == 2148916238L) {
            return "Child account — add it to a Microsoft family first.";
        }
        return "Xbox authentication failed (XErr " + err + ").";
    }

    static String dashUuid(String raw) {
        if (raw == null) {
            return "";
        }
        String u = raw.replace("-", "");
        if (u.length() != 32) {
            return raw;
        }
        return u.substring(0, 8) + "-" + u.substring(8, 12) + "-" + u.substring(12, 16)
                + "-" + u.substring(16, 20) + "-" + u.substring(20);
    }

    static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    public interface BrowserHooks {
        void onAuthorizeUrl(String authorizeUrl);
    }

    public interface CancelFlag {
        boolean isCancelled();
    }

    public static final class AuthResult {
        public final String username;
        public final String uuid;
        public final String accessToken;
        public final String refreshToken;

        public AuthResult(String username, String uuid, String accessToken, String refreshToken) {
            this.username = username;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken == null ? "" : refreshToken;
        }
    }

    private static final class XblSession {
        final String uhs;
        final String xstsToken;

        XblSession(String uhs, String xstsToken) {
            this.uhs = uhs;
            this.xstsToken = xstsToken;
        }
    }
}
