package shared.payload;

public class UpdateReadMessages implements RequestPayload {
    private final String conversationID;

    public UpdateReadMessages(String c_id) {
        this.conversationID = c_id;
    }

    public String getConversationID() { return conversationID; }
}
