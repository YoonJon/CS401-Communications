package shared.payload;

import shared.enums.ConversationType;
import java.io.File;
import java.util.ArrayList;

public class Conversation implements ResponsePayload {
    private final String conversationId;
    private final ArrayList<Message> messages;
    private final ArrayList<UserInfo> participants;
    private final ArrayList<UserInfo> historicalParticipants;
    private final ConversationType type;

    public Conversation(String c_id, ConversationType t) {
        this.conversationId = c_id;
        this.type = t;
        this.messages = new ArrayList<>();
        this.participants = new ArrayList<>();
        this.historicalParticipants = new ArrayList<>();
    }

    /**
     * Lightweight view of this conversation: id, participants, type — no message bodies.
     * Produced on demand; lists are copied so later changes to the conversation do not affect the returned snapshot.
     */
    public ConversationMetadata toMetadata() {
        return new ConversationMetadata(
            conversationId,
            new ArrayList<>(participants),
            new ArrayList<>(historicalParticipants),
            type
        );
    }

    public String getConversationId() { return conversationId; }
    public ArrayList<Message> getMessages() { return messages; }
    public ArrayList<UserInfo> getParticipants() { return participants; }
    public ArrayList<UserInfo> getHistoricalParticipants() { return historicalParticipants; }
    public ConversationType getType() { return type; }

    public void append(Message m) { messages.add(m); }

    public static Conversation fromFile(File f) {
        // TODO: deserialize from file
        return null;
    }
}
