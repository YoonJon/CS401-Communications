package shared.payload;

import shared.enums.ConversationType;
import java.io.File;
import java.util.ArrayList;

public class Conversation implements ResponsePayload {
    private final long conversationId;
    private final ArrayList<Message> messages;
    private final ArrayList<UserInfo> participants;
    private final ArrayList<UserInfo> historicalParticipants;
    private final ConversationType type;

    /**
     * @param conversationId stable numeric id (from server counter)
     * @param participants initial members; copied defensively. If the size is 2, {@link ConversationType#PRIVATE}
     * is used; otherwise {@link ConversationType#GROUP}. The same initial snapshot is stored in
     * {@linkplain #getHistoricalParticipants() historical participants} (add/remove of participants is not
     * implemented yet; when it is, new additions should be reflected in historical participants, removals should not).
     */
    public Conversation(long conversationId, ArrayList<UserInfo> participants) {
        this.conversationId = conversationId;
        this.messages = new ArrayList<>();
        this.participants = new ArrayList<>(participants != null ? participants : new ArrayList<>());
        this.historicalParticipants = new ArrayList<>(this.participants);
        this.type = this.participants.size() == 2 ? ConversationType.PRIVATE : ConversationType.GROUP;
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

    public long getConversationId() { return conversationId; }
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
