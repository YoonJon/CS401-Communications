package shared.payload;

public class AdminConversationQuery implements RequestPayload {
    private final String conversationQuery;

    public AdminConversationQuery(String c_id) {
        this.conversationQuery = c_id;
    }

    public String getQuery() { return conversationQuery; }
}
