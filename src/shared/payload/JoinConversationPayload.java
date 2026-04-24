package shared.payload;

public class JoinConversationPayload implements RequestPayload {
    private final long targetConversationId;

    public JoinConversationPayload(long targetConversationId) {
        this.targetConversationId = targetConversationId;
    }

    public long getTargetConversationId() { return targetConversationId; }
}
