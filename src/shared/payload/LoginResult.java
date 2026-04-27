package shared.payload;

import java.util.ArrayList;

import shared.enums.LoginStatus;
import shared.networking.User.UserInfo;

public class LoginResult implements ResponsePayload {
    private final LoginStatus result;
    private final UserInfo userInfo;
    private final ArrayList<Conversation> conversationList;
    private final ArrayList<UserInfo> directoryUserInfoList;

    /** Failed (or otherwise non-success) login: no user or conversation payload. */
    public LoginResult(LoginStatus r) {
        this(r, null, null, null);
    }

    /**
     * Successful login payload with a precomputed user directory snapshot.
     * The directory list is copied at construction time so later caller-side mutations
     * do not affect the payload object.
     */
    public LoginResult(LoginStatus r, UserInfo ui, ArrayList<Conversation> cl, ArrayList<UserInfo> directoryUsers) {
        this.result = r;
        this.userInfo = ui;
        this.conversationList = cl;
        this.directoryUserInfoList = directoryUsers == null ? new ArrayList<>() : new ArrayList<>(directoryUsers);
    }

    public LoginStatus getLoginStatus() { return result; }
    public UserInfo getUserInfo() { return userInfo; }
    public ArrayList<Conversation> getConversationList() { return conversationList; }
    public ArrayList<UserInfo> getDirectoryUserInfoList() { return new ArrayList<>(directoryUserInfoList); }
}
