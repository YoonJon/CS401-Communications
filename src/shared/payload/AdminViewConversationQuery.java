package shared.payload;

/**
 * Request from an admin to fetch a full {@link Conversation} (with messages) for read-only
 * viewing. The handler must NOT mutate participant indices — the admin is never recorded as
 * a member, so other users get no signal that the lookup happened.
 */
public class AdminViewConversationQuery implements RequestPayload {
    private final long conversationId;

    public AdminViewConversationQuery(long conversationId) {
        this.conversationId = conversationId;
    }

    public long getConversationId() { return conversationId; }
}
