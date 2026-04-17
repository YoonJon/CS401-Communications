package shared.payload;

import shared.enums.UserType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
    private final Map<String, Long> lastRead;

    public UserInfo() {
        this(null, null, null, new HashMap<>());
    }

    public UserInfo(String name, String userId, UserType userType) {
        this(name, userId, userType, new HashMap<>());
    }

    public UserInfo(String name, String userId, UserType userType, Map<String, Long> lastRead) {
        this.name = name;
        this.userId = userId;
        this.userType = userType;
        this.lastRead = new HashMap<>(lastRead != null ? lastRead : Map.of());
    }

    public String getName() { return name; }
    public String getUserId() { return userId; }
    public UserType getUserType() { return userType; }

    /** Unmodifiable view of conversation id → last read sequence number (for clients). */
    public Map<String, Long> getLastReadMap() {
        return Collections.unmodifiableMap(lastRead);
    }

    public long getLastRead(String c_id) {
        return lastRead.getOrDefault(c_id, 0L);
    }

    public void setLastRead(String c_id, long sequenceNumber) {
        lastRead.put(c_id, sequenceNumber);
    }
}
