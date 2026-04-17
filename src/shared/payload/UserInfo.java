package shared.payload;

import java.util.HashMap;
import java.util.Map;
import shared.enums.UserType;

public class UserInfo implements Payload {
    private String name;
    private String userId;
    private UserType userType;
    private Map<String, Long> lastRead;

    public UserInfo() {
        this.lastRead = new HashMap<>();
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

    public long getLastRead(String c_id) {
        return lastRead.getOrDefault(c_id, 0L);
    }

    public void setLastRead(String c_id, long sequenceNumber) {
        lastRead.put(c_id, sequenceNumber);
    }
}
