package shared.payload;

import java.util.Date;

public class Message implements Payload {
    private String text;
    private long sequenceNumber;
    private Date timestamp;
    private String senderId;
    private String conversationId;

    public Message(String t, long sn, Date time, String senderID, String c_id) {
        this.text = t;
        this.sequenceNumber = sn;
        this.timestamp = time;
        this.senderId = senderID;
        this.conversationId = c_id;
    }

    public String getText() { return text; }
    public String getConversationId() { return conversationId; }
    public String getSenderId() { return senderId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public Date getTimestamp() { return timestamp; }
}
