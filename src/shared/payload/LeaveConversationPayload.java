package shared.payload;

public class LeaveConversationPayload implements RequestPayload {
    private final String targetConversationId;

    public LeaveConversationPayload(String t) {
        this.targetConversationId = t;
    }

    public String getTargetConversationId() { return targetConversationId; }
}
