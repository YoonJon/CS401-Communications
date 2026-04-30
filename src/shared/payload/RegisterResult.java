package shared.payload;

import shared.enums.RegisterStatus;
import shared.networking.User.UserInfo;

public class RegisterResult implements ResponsePayload {
    private final RegisterStatus result;
    private final UserInfo userInfo;

    public RegisterResult(RegisterStatus r) {
        this(r, null);
    }

    public RegisterResult(RegisterStatus r, UserInfo userInfo) {
        this.result = r;
        this.userInfo = userInfo;
    }

    public RegisterStatus getRegisterStatus() { return result; }
    public UserInfo getUserInfo() { return userInfo; }
}
