package shared.payload;

public class JoinConversationPayload implements RequestPayload {
    private final String targetConversationId;

    public JoinConversationPayload(String t) {
        this.targetConversationId = t;
    }

    public String getTargetConversationId() { return targetConversationId; }
}
