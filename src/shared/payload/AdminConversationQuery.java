package shared.payload;

public class AdminConversationQuery implements RequestPayload {
    private final long conversationId;

    public AdminConversationQuery(long conversationId) {
        this.conversationId = conversationId;
    }

    public long getConversationId() { return conversationId; }
}
