package shared.payload;

public class UpdateReadMessages implements RequestPayload {
    private final long conversationID;
    /** Last message sequence number the user has seen for this conversation. */
    private final long lastSeenSequenceNumber;

    public UpdateReadMessages(long conversationID, long lastSeenSequenceNumber) {
        this.conversationID = conversationID;
        this.lastSeenSequenceNumber = lastSeenSequenceNumber;
    }

    public long getConversationID() { return conversationID; }

    public long getLastSeenSequenceNumber() { return lastSeenSequenceNumber; }
}
