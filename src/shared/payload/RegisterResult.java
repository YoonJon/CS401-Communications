package shared.payload;

import shared.enums.RegisterStatus;

public class RegisterResult implements Payload {
    private RegisterStatus result;

    public RegisterResult(RegisterStatus r) {
        this.result = r;
    }

    public RegisterStatus getRegisterStatus() { return result; }
}
