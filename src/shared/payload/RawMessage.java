package shared.payload;

public class RawMessage implements Payload {
    private String text;
    private String targetConversationId;

    public RawMessage(String t, String c_id) {
        this.text = t;
        this.targetConversationId = c_id;
    }

    public String getText() { return text; }
    public String getTargetConversationId() { return targetConversationId; }
}
