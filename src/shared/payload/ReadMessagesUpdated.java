package shared.payload;

import shared.networking.User.UserInfo;

public class ReadMessagesUpdated implements ResponsePayload {
    private final UserInfo updatedUserInfo;

    public ReadMessagesUpdated(UserInfo updatedUserInfo) {
        this.updatedUserInfo = updatedUserInfo;
    }

    public UserInfo getUpdatedUserInfo() {
        return updatedUserInfo;
    }
}
