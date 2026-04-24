package shared.payload;

public class LeaveConversationPayload implements RequestPayload {
    private final long targetConversationId;

    public LeaveConversationPayload(long targetConversationId) {
        this.targetConversationId = targetConversationId;
    }

    public long getTargetConversationId() { return targetConversationId; }
}
