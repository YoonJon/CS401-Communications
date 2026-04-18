package shared.payload;

import shared.enums.UserType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import shared.enums.UserType;

/**
 * Client-visible user snapshot (server-side user credentials are never included).
 * <p>
 * {@linkplain #getLastReadMap() Read cursors} (conversation id → last seen sequence) are loaded
 * from the server on login so the client can show unread markers; the client may move those
 * cursors optimistically for UI. The server does not treat this map as a strongly consistent
 * concurrent store.
 */
public class UserInfo implements Payload {
    private final String name;
    private final String userId;
    private final UserType userType;
    private final Map<Long, Long> lastRead;

    public UserInfo() {
        this(null, null, null, new HashMap<>());
    }

    public UserInfo(String name, String userId, UserType userType) {
        this(name, userId, userType, new HashMap<>());
    }

    public UserInfo(String name, String userId, UserType userType, Map<Long, Long> lastRead) {
        this.name = name;
        this.userId = userId;
        this.userType = userType;
        this.lastRead = new HashMap<>(lastRead != null ? lastRead : Map.of());
    }

    public UserInfo(String userId, String name, UserType userType) {
        this.userId = userId;
        this.name = name;
        this.userType = userType;
        this.lastRead = new HashMap<>();
    }

    public String getName() { return name; }
    public String getUserId() { return userId; }
    public UserType getUserType() { return userType; }

    /** Unmodifiable view of conversation id → last read sequence number (for clients). */
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
