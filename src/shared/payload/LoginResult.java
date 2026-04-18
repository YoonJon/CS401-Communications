package shared.payload;

import shared.enums.LoginStatus;
import java.util.ArrayList;

public class LoginResult implements ResponsePayload {
    private final LoginStatus result;
    private final UserInfo userInfo;
    private final ArrayList<Conversation> conversationList;

    public LoginResult(LoginStatus r, ArrayList<Conversation> cl) {
        this(r, null, cl);
    }

    public LoginResult(LoginStatus r, UserInfo ui, ArrayList<Conversation> cl) {
        this.result = r;
        this.userInfo = ui;
        this.conversationList = cl;
    }

    public LoginStatus getLoginStatus() { return result; }
    public UserInfo getUserInfo() { return userInfo; }
    public ArrayList<Conversation> getConversationList() { return conversationList; }
}
