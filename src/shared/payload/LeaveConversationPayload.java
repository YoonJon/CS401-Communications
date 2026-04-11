package shared.payload;

public class LeaveConversationPayload implements Payload {
    private String targetConversationId;

    public LeaveConversationPayload(String t) {
        this.targetConversationId = t;
    }

    public String getTargetConversationId() { return targetConversationId; }
}
