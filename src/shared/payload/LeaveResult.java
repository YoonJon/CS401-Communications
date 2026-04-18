package shared.payload;

public class LeaveResult implements ResponsePayload {
    private final long conversationId;

    public LeaveResult(long conversationId) {
        this.conversationId = conversationId;
    }

    public long getLeftConversationID() { return conversationId; }
}
