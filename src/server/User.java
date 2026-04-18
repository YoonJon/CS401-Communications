package server;

import shared.enums.UserType;
import shared.payload.UserInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

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

    public UserInfo getUserInfo() {
        return new UserInfo(name, userId, userType, new HashMap<>(lastRead));
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
}
