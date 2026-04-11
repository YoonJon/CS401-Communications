package shared.payload;

public class RegisterCredentials implements Payload {
    private String userId;
    private String loginName;
    private String password;
    private String name;

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
