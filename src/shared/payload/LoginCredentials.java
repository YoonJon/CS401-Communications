package shared.payload;

public class LoginCredentials implements RequestPayload {
    private final String loginName;
    private final String password;

    public LoginCredentials(String ln, String p) {
        this.loginName = ln;
        this.password = p;
    }

    public String getLoginName() { return loginName; }
    public String getPassword() { return password; }
}
