package shared.payload;

public class RegisterCredentials implements RequestPayload {
    private final String userId;
    private final String loginName;
    private final String password;
    private final String name;

    public RegisterCredentials(String id, String ln, String p, String n) {
        this.userId = id;
        this.loginName = ln;
        this.password = p;
        this.name = n;
    }

    public String getUserId() { return userId; }
    public String getLoginName() { return loginName; }
    public String getPassword() { return password; }
    public String getName() { return name; }
}
