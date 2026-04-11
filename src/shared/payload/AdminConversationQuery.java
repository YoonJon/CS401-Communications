package shared.payload;

public class AdminConversationQuery implements Payload {
    private String conversationQuery;

    public AdminConversationQuery(String c_id) {
        this.conversationQuery = c_id;
    }

    public String getQuery() { return conversationQuery; }
}
