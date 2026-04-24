package shared.payload;

public class AdminConversationQuery implements RequestPayload {
    private final String userId;

    public AdminConversationQuery(String userId) {
        this.userId = userId;
    }

    public String getUserId() { return userId; }
}
