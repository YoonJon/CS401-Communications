package shared.payload;

public class JoinConversationPayload implements Payload {
    private String targetConversationId;

    public JoinConversationPayload(String t) {
        this.targetConversationId = t;
    }

    public String getTargetConversationId() { return targetConversationId; }
}
