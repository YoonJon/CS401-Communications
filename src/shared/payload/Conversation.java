package shared.payload;

import shared.enums.ConversationType;
import java.io.File;
import java.util.ArrayList;

public class Conversation implements Payload {
    private String conversationId;
    private ArrayList<Message> messages;
    private ArrayList<UserInfo> participants;
    private ArrayList<UserInfo> historicalParticipants;
    private ConversationType type;
    private ConversationMetadata metadata;

    public Conversation(String c_id, ConversationType t) {
        this.conversationId = c_id;
        this.type = t;
        this.messages = new ArrayList<>();
        this.participants = new ArrayList<>();
        this.historicalParticipants = new ArrayList<>();
    }

    public String getConversationId() { return conversationId; }
    public ArrayList<Message> getMessages() { return messages; }
    public ArrayList<UserInfo> getParticipants() { return participants; }
    public ArrayList<UserInfo> getHistoricalParticipants() { return historicalParticipants; }
    public ConversationType getType() { return type; }

    public void setConversationMetadata(ConversationMetadata cm) { this.metadata = cm; }
    public ConversationMetadata getConversationMetadata() { return metadata; }

    public void append(Message m) { messages.add(m); }

    public static Conversation fromFile(File f) {
        // TODO: deserialize from file
        return null;
    }
}
