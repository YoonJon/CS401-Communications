package shared.payload;

import java.util.Date;

/**
 * A conversation message. The {@linkplain #getSequenceNumber() sequence number} is assigned by the server
 * and is immutable for the lifetime of the object.
 */
public class Message implements ResponsePayload {
    private final String text;
    private final long sequenceNumber;
    private final Date timestamp;
    private final String senderId;
    private final String conversationId;

    public Message(String text, long sequenceNumber, Date timestamp, String senderId, String conversationId) {
        this.text = text;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.senderId = senderId;
        this.conversationId = conversationId;
    }

    public String getText() { return text; }
    public String getConversationId() { return conversationId; }
    public String getSenderId() { return senderId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public Date getTimestamp() { return timestamp; }
}
