package shared.payload;

public class RawMessage implements RequestPayload {
    private final String text;
    private final long targetConversationId;

    public RawMessage(String t, long targetConversationId) {
        this.text = t;
        this.targetConversationId = targetConversationId;
    }

    public String getText() { return text; }
    public long getTargetConversationId() { return targetConversationId; }
}
