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

/**
 * Server-persisted account. Lives in {@code shared.networking} so it can own {@link UserInfo},
 * the client-visible snapshot type used on the wire inside payloads.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String name;
    private String loginName;
    private String password;
    private UserType userType;
    /**
     * Best-effort read cursors (conversation id → last seen message sequence). Used so
     * {@link #getUserInfo()} can supply unread markers on login; clients may update their own UI
     * optimistically without waiting for the server. No concurrent-update guarantees.
     */
    private Map<Long, Long> lastRead;

    public User(String id, String n, String ln, String p) {
        this.userId = id;
        this.name = n;
        this.loginName = ln;
        this.password = p;
        this.userType = UserType.USER;
        this.lastRead = new HashMap<>();
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getLoginName() { return loginName; }
    public String getPassword() { return password; }
    public UserType getUserType() { return userType; }

    /** Client snapshot of this account (no credentials). */
    public UserInfo getUserInfo() {
        return userInfo(userId, name, userType, new HashMap<>(lastRead));
    }

    /** Snapshot with empty read cursors (e.g. protocol fixtures). */
    public static UserInfo userInfo(String userId, String name, UserType userType) {
        return userInfo(userId, name, userType, new HashMap<>());
    }

    /** Snapshot; {@code lastRead} is copied defensively. */
    public static UserInfo userInfo(String userId, String name, UserType userType, Map<Long, Long> lastRead) {
        return new UserInfo(userId, name, userType, lastRead);
    }

    public long getLastRead(long conversationId) {
        return lastRead.getOrDefault(conversationId, 0L);
    }

    public void setLastRead(long conversationId, long sequenceNumber) {
        lastRead.put(conversationId, sequenceNumber);
    }

    public static User fromFile(File f) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            return (User) in.readObject();
        }
    }

    /**
     * Client-visible user snapshot (credentials never included).
     * <p>
     * Create only via {@link User#getUserInfo()}, {@link User#userInfo(String, String, UserType)},
     * {@link User#userInfo(String, String, UserType, Map)}, or Java deserialization.
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
    }
}
