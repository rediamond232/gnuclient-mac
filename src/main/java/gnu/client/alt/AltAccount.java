package gnu.client.alt;

/**
 * Saved account entry. Tokens are persisted for Microsoft/cookie alts.
 */
public final class AltAccount {

    private String id;
    private String username;
    private String uuid;
    private String accessToken;
    private String refreshToken;
    private AltType type;
    private long addedAt;

    public AltAccount() {
    }

    public AltAccount(String id, String username, String uuid, String accessToken,
            String refreshToken, AltType type) {
        this.id = id;
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.type = type;
        this.addedAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username == null ? "" : username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUuid() {
        return uuid == null ? "" : uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getAccessToken() {
        return accessToken == null ? "" : accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken == null ? "" : refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public AltType getType() {
        return type == null ? AltType.CRACKED : type;
    }

    public void setType(AltType type) {
        this.type = type;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(long addedAt) {
        this.addedAt = addedAt;
    }

    public String typeLabel() {
        switch (getType()) {
            case MICROSOFT:
                return "Microsoft";
            case COOKIE:
                return "Cookie";
            case CRACKED:
            default:
                return "Cracked";
        }
    }
}
