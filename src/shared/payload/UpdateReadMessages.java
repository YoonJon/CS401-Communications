package shared.payload;

public class UpdateReadMessages implements RequestPayload {
    private final String conversationID;
    /** Last message sequence number the user has seen for this conversation. */
    private final long lastSeenSequenceNumber;

    public UpdateReadMessages(String conversationID, long lastSeenSequenceNumber) {
        this.conversationID = conversationID;
        this.lastSeenSequenceNumber = lastSeenSequenceNumber;
    }

    public String getConversationID() { return conversationID; }

    public long getLastSeenSequenceNumber() { return lastSeenSequenceNumber; }
}
