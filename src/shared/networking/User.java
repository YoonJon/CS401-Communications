package shared.networking;

import shared.enums.UserType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Server-persisted account. Lives in {@code shared.networking} so it can own {@link UserInfo},
 * the client-visible snapshot type used on the wire inside payloads.
 * <p>
 * <b>Registration:</b> the server derives {@link UserType} from {@code authorized_admins.txt} (same
 * list as in memory) and passes it into the public constructor; that value is persisted on this object.
 * <p>
 * <b>Wire snapshots:</b> build {@link UserInfo} only via {@link #toUserInfo()}, which copies
 * persisted fields including {@link #userType}. Rehydration uses {@link #fromFile}.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String name;
    private String loginName;
    private String password;
    /** Persisted; set at registration from server-derived admin eligibility. */
    private UserType userType;
    /**
     * Best-effort read cursors (conversation id → last seen message sequence). Used so
     * {@link #toUserInfo()} can supply unread markers on login; clients may update their own UI
     * optimistically without waiting for the server. No concurrent-update guarantees.
     */
    private Map<Long, Long> lastRead;

    /**
     * @param userType derived by the server at registration from {@code authorizedAdminIds}
     *                 (e.g. ADMIN if user id is listed in {@code authorized_admins.txt}).
     */
    public User(String id, String n, String ln, String p, UserType userType) {
        this.userId = id;
        this.name = n;
        this.loginName = ln;
        this.password = p;
        this.userType = Objects.requireNonNull(userType);
        this.lastRead = new HashMap<>();
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getLoginName() { return loginName; }
    public String getPassword() { return password; }
    public UserType getUserType() { return userType; }

    /** Snapshot of this persisted user for protocol payloads. */
    public UserInfo toUserInfo() {
        return new UserInfo(userId, name, userType, new HashMap<>(lastRead));
    }

    public long getLastRead(long conversationId) {
        return lastRead.getOrDefault(conversationId, 0L);
    }

    public void setLastRead(long conversationId, long sequenceNumber) {
        lastRead.put(conversationId, sequenceNumber);
    }

    /** Drops read-state for a conversation (e.g. after leaving). */
    public void removeConversationFromLastReadMap(long conversationId) {
        lastRead.remove(conversationId);
    }

    /** Rehydrate a persisted account from a {@code .user} file. */
    public static User fromFile(File f) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            return (User) in.readObject();
        }
    }

    /**
     * Client-visible user snapshot (credentials never included).
     * <p>
     * Prefer {@link User#toUserInfo()} for live accounts.
     */
    public static class UserInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String userId;
        private final String name;
        private final UserType userType;
        private final Map<Long, Long> lastRead;

        private UserInfo(String userId, String name, UserType userType, Map<Long, Long> lastRead) {
            this.userId = userId;
            this.name = name;
            this.userType = userType;
            this.lastRead = new HashMap<>(lastRead != null ? lastRead : Map.of());
        }

        public String getName() { return name; }
        public String getUserId() { return userId; }
        public UserType getUserType() { return userType; }

        public Map<Long, Long> getLastReadMap() {
            return Collections.unmodifiableMap(lastRead);
        }

        public long getLastRead(long conversationId) {
            return lastRead.getOrDefault(conversationId, 0L);
        }

        public void setLastRead(long conversationId, long sequenceNumber) {
            lastRead.put(conversationId, sequenceNumber);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserInfo)) return false;
            UserInfo other = (UserInfo) o;
            return Objects.equals(userId, other.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId);
        }

        public String toString(){return name+" ("+userId+")";}
    }
}
