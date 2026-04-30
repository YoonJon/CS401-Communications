package shared.payload;

import shared.networking.User.UserInfo;

public class UserCreationPayload implements ResponsePayload {
    private final UserInfo userInfo;

    public UserCreationPayload(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }
}
