package gnu.client.alt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import gnu.client.common.GnuLog;
import gnu.client.mixin.impl.accessors.IAccessorMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persists alts and applies {@link Session} on the client.
 */
public final class AltManager {

    private static final AltManager INSTANCE = new AltManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonParser PARSER = new JsonParser();

    private final List<AltAccount> accounts = new CopyOnWriteArrayList<AltAccount>();
    private String activeId = "";
    private Path path;
    private boolean loaded;

    private AltManager() {
    }

    public static AltManager instance() {
        return INSTANCE;
    }

    public synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        path = resolvePath();
        load();
        loaded = true;
    }

    public List<AltAccount> accounts() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<AltAccount>(accounts));
    }

    public String getActiveId() {
        ensureLoaded();
        return activeId == null ? "" : activeId;
    }

    public AltAccount getActive() {
        ensureLoaded();
        for (AltAccount a : accounts) {
            if (a.getId() != null && a.getId().equals(activeId)) {
                return a;
            }
        }
        return null;
    }

    public String currentUsername() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getSession() != null) {
            return mc.getSession().getUsername();
        }
        return "?";
    }

    public AltAccount addCracked(String username) throws IOException {
        MicrosoftAuth.AuthResult result = CrackedAuth.create(username);
        return addAndLogin(result, AltType.CRACKED);
    }

    public AltAccount addMicrosoftFromRedirect(String redirectOrCode) throws IOException {
        MicrosoftAuth.AuthResult result = MicrosoftAuth.loginWithAuthCode(redirectOrCode);
        return addAndLogin(result, AltType.MICROSOFT);
    }

    public AltAccount addMicrosoftBrowser(MicrosoftAuth.BrowserHooks hooks,
            MicrosoftAuth.CancelFlag cancel) throws IOException {
        MicrosoftAuth.AuthResult result = MicrosoftAuth.loginWithLocalhostBrowser(hooks, cancel);
        return addAndLogin(result, AltType.MICROSOFT);
    }

    public AltAccount addFromCookieFile(Path cookieFile) throws IOException {
        MicrosoftAuth.AuthResult result = CookieAuth.loginFromFile(cookieFile);
        return addAndLogin(result, AltType.COOKIE);
    }

    public AltAccount addFromCookieText(String cookieText) throws IOException {
        MicrosoftAuth.AuthResult result = CookieAuth.loginFromCookieText(cookieText);
        return addAndLogin(result, AltType.COOKIE);
    }

    public void login(AltAccount account) throws IOException {
        if (account == null) {
            throw new IOException("No account selected.");
        }
        ensureLoaded();
        if (account.getType() == AltType.MICROSOFT || account.getType() == AltType.COOKIE) {
            if (!account.getRefreshToken().isEmpty()) {
                try {
                    MicrosoftAuth.AuthResult refreshed;
                    if (account.getType() == AltType.MICROSOFT) {
                        refreshed = MicrosoftAuth.loginWithAzureRefreshToken(account.getRefreshToken());
                    } else {
                        refreshed = MicrosoftAuth.loginWithLiveRefreshToken(account.getRefreshToken());
                    }
                    account.setUsername(refreshed.username);
                    account.setUuid(refreshed.uuid);
                    account.setAccessToken(refreshed.accessToken);
                    if (!refreshed.refreshToken.isEmpty()) {
                        account.setRefreshToken(refreshed.refreshToken);
                    }
                    save();
                } catch (IOException refreshFailed) {
                    // Fall through and try existing access token
                    GnuLog.log("Alt refresh failed for " + account.getUsername()
                            + ": " + refreshFailed.getMessage());
                }
            }
        }
        applySession(account);
        activeId = account.getId();
        save();
    }

    public void remove(AltAccount account) {
        if (account == null) {
            return;
        }
        ensureLoaded();
        accounts.remove(account);
        if (account.getId() != null && account.getId().equals(activeId)) {
            activeId = "";
        }
        save();
    }

    private AltAccount addAndLogin(MicrosoftAuth.AuthResult result, AltType type) throws IOException {
        ensureLoaded();
        AltAccount existing = findByUuidOrName(result.uuid, result.username);
        if (existing != null) {
            existing.setUsername(result.username);
            existing.setUuid(result.uuid);
            existing.setAccessToken(result.accessToken);
            if (!result.refreshToken.isEmpty()) {
                existing.setRefreshToken(result.refreshToken);
            }
            existing.setType(type);
            applySession(existing);
            activeId = existing.getId();
            save();
            return existing;
        }
        AltAccount account = new AltAccount(
                UUID.randomUUID().toString(),
                result.username,
                result.uuid,
                result.accessToken,
                result.refreshToken,
                type);
        accounts.add(account);
        applySession(account);
        activeId = account.getId();
        save();
        return account;
    }

    private AltAccount findByUuidOrName(String uuid, String username) {
        for (AltAccount a : accounts) {
            if (uuid != null && !uuid.isEmpty() && uuid.equalsIgnoreCase(a.getUuid())) {
                return a;
            }
            if (username != null && username.equalsIgnoreCase(a.getUsername())
                    && a.getType() == AltType.CRACKED) {
                return a;
            }
        }
        return null;
    }

    private void applySession(AltAccount account) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        String token = account.getAccessToken();
        if (token == null || token.isEmpty()) {
            token = "0";
        }
        String type = account.getType() == AltType.CRACKED ? "legacy" : "mojang";
        Session session = new Session(account.getUsername(), account.getUuid(), token, type);
        ((IAccessorMinecraft) mc).setSession(session);
    }

    private Path resolvePath() {
        Minecraft mc = Minecraft.getMinecraft();
        Path base;
        if (mc != null && mc.mcDataDir != null) {
            base = mc.mcDataDir.toPath();
        } else {
            base = java.nio.file.Paths.get(".");
        }
        return base.resolve("config").resolve("gnuclient").resolve("alts.json");
    }

    private void load() {
        accounts.clear();
        activeId = "";
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        Reader reader = null;
        try {
            reader = Files.newBufferedReader(path);
            JsonElement rootEl = PARSER.parse(reader);
            if (!rootEl.isJsonObject()) {
                return;
            }
            JsonObject root = rootEl.getAsJsonObject();
            activeId = root.has("activeId") && !root.get("activeId").isJsonNull()
                    ? root.get("activeId").getAsString() : "";
            if (!root.has("accounts") || !root.get("accounts").isJsonArray()) {
                return;
            }
            JsonArray arr = root.getAsJsonArray("accounts");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                AltAccount a = GSON.fromJson(el, AltAccount.class);
                if (a != null && a.getUsername() != null && !a.getUsername().isEmpty()) {
                    if (a.getId() == null || a.getId().isEmpty()) {
                        a.setId(UUID.randomUUID().toString());
                    }
                    accounts.add(a);
                }
            }
        } catch (JsonSyntaxException | IOException e) {
            GnuLog.logError("Failed to load alts.json", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void save() {
        if (path == null) {
            path = resolvePath();
        }
        Writer writer = null;
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject root = new JsonObject();
            root.addProperty("activeId", activeId == null ? "" : activeId);
            JsonArray arr = new JsonArray();
            for (AltAccount a : accounts) {
                arr.add(GSON.toJsonTree(a));
            }
            root.add("accounts", arr);
            writer = Files.newBufferedWriter(path);
            GSON.toJson(root, writer);
        } catch (IOException e) {
            GnuLog.logError("Failed to save alts.json", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
