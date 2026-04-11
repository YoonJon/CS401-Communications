package server;

import shared.enums.UserType;
import shared.payload.UserInfo;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class User {
    private String userId;
    private String name;
    private String loginName;
    private String password;
    private UserType userType;
    private Map<String, Long> lastRead;

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

    public UserInfo getUserInfo() {
        // TODO: build and return UserInfo from this User's fields
        return null;
    }

    public long getLastRead(String c_id) {
        return lastRead.getOrDefault(c_id, 0L);
    }

    public void setLastRead(String c_id, long sequenceNumber) {
        lastRead.put(c_id, sequenceNumber);
    }

    public static User fromFile(File f) {
        // TODO: deserialize from file
        return null;
    }
}
