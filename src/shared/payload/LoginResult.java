package shared.payload;

import shared.enums.LoginStatus;
import java.util.ArrayList;

public class LoginResult implements Payload {
    private LoginStatus result;
    private UserInfo userInfo;
    private ArrayList<Conversation> conversationList;

    public LoginResult(LoginStatus r, ArrayList<Conversation> cl) {
        this.result = r;
        this.conversationList = cl;
    }

    /** Constructor that also carries the authenticated user's info. */
    public LoginResult(LoginStatus r, UserInfo ui, ArrayList<Conversation> cl) {
        this.result = r;
        this.userInfo = ui;
        this.conversationList = cl;
    }

    public LoginStatus getLoginStatus() { return result; }
    public UserInfo getUserInfo() { return userInfo; }
    public ArrayList<Conversation> getConversationList() { return conversationList; }
}
