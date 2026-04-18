package shared.payload;

import shared.enums.UserType;
import java.util.HashMap;
import java.util.Map;

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
        this.lastRead = lastRead;
    }

    public String getName() { return name; }
    public String getUserId() { return userId; }
    public UserType getUserType() { return userType; }

    public long getLastRead(String c_id) {
        return lastRead.getOrDefault(c_id, 0L);
    }

    public void setLastRead(String c_id, long sequenceNumber) {
        lastRead.put(c_id, sequenceNumber);
    }
}
