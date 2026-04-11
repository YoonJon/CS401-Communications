package shared.payload;

public class UpdateReadMessages implements Payload {
    private String conversationID;

    public UpdateReadMessages(String c_id) {
        this.conversationID = c_id;
    }

    public String getConversationID() { return conversationID; }
}
