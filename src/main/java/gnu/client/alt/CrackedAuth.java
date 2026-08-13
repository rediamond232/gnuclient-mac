package gnu.client.alt;

import java.util.UUID;

/**
 * Offline / cracked username session.
 */
public final class CrackedAuth {

    private CrackedAuth() {
    }

    public static MicrosoftAuth.AuthResult create(String username) {
        String name = sanitize(username);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (name.length() > 16) {
            throw new IllegalArgumentException("Username must be 16 characters or fewer.");
        }
        UUID uuid = MicrosoftAuth.offlineUuid(name);
        return new MicrosoftAuth.AuthResult(name, uuid.toString(), "0", "");
    }

    public static String sanitize(String username) {
        if (username == null) {
            return "";
        }
        String t = username.trim();
        // Allow typical cracked names; strip control chars
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 33 && c <= 126) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
