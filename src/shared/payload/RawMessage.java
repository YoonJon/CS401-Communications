package shared.payload;

public class RawMessage implements RequestPayload {
    private final String text;
    private final String targetConversationId;

    public RawMessage(String t, String c_id) {
        this.text = t;
        this.targetConversationId = c_id;
    }

    public String getText() { return text; }
    public String getTargetConversationId() { return targetConversationId; }
}
