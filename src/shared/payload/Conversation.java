package shared.payload;

import shared.networking.User.UserInfo;
import shared.enums.ConversationType;
import java.io.*;
import java.util.ArrayList;


public class Conversation implements ResponsePayload {
    private static final long serialVersionUID = 1L;

    private final long conversationId;
    private final ArrayList<Message> messages;
    private final ArrayList<UserInfo> participants;
    private final ArrayList<UserInfo> historicalParticipants;
    private final ConversationType type;

    /**
     * @param conversationId stable numeric id (from server counter)
     * @param participants initial members; copied defensively. If the size is 2, {@link ConversationType#PRIVATE}
     * is used; otherwise {@link ConversationType#GROUP}. The same initial snapshot is stored in
     * {@linkplain #getHistoricalParticipants() historical participants}. Removals from the active roster
     * do not remove entries from historical participants; use {@link #addParticipants(ArrayList)} so new
     * members are recorded in both lists. {@link #getType()} is fixed for the lifetime of this object;
     * adding people to a {@link ConversationType#PRIVATE} thread is done by forking (see server) into a new conversation.
     */
    public Conversation(long conversationId, ArrayList<UserInfo> participants) {
        this.conversationId = conversationId;
        this.messages = new ArrayList<>();
        this.participants = new ArrayList<>(participants != null ? participants : new ArrayList<>());
        this.historicalParticipants = new ArrayList<>(this.participants);
        this.type = this.participants.size() == 2 ? ConversationType.PRIVATE : ConversationType.GROUP;
    }

    /**
     * Appends users who are not already in the active roster. Each new member is added to
     * {@link #getParticipants()} and to {@link #getHistoricalParticipants()}. Does not change
     * {@link #getType()} ({@link ConversationType#PRIVATE} threads are forked on the server instead of growing in place).
     */
    public synchronized void addParticipants(ArrayList<UserInfo> toAdd) {
        if (toAdd == null || toAdd.isEmpty()) {
            return;
        }
        for (UserInfo u : toAdd) {
            if (u == null || u.getUserId() == null) {
                continue;
            }
            if (indexOfParticipant(u.getUserId()) >= 0) {
                continue;
            }
            participants.add(u);
            if (historicalParticipants != null) {
                historicalParticipants.add(u);
            }
        }
    }

    private int indexOfParticipant(String userId) {
        for (int i = 0; i < participants.size(); i++) {
            if (userId.equals(participants.get(i).getUserId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Removes {@code userId} from the active {@link #getParticipants()} roster only.
     * {@link #getHistoricalParticipants()} is unchanged.
     */
    public synchronized void removeParticipant(String userId) {
        if (userId == null) {
            return;
        }
        int i = indexOfParticipant(userId);
        if (i >= 0) {
            participants.remove(i);
        }
    }

    /**
     * Lightweight view of this conversation: id, participants, type — no message bodies.
     * Produced on demand; lists are copied so later changes to the conversation do not affect the returned snapshot.
     */
    public synchronized ConversationMetadata toMetadata() {
        return new ConversationMetadata(
            conversationId,
            new ArrayList<>(participants),
            getHistoricalParticipants(),
            type
        );
    }

    public long getConversationId() { return conversationId; }
    public synchronized ArrayList<Message> getMessages() { return new ArrayList<>(messages); }
    public synchronized ArrayList<UserInfo> getParticipants() { return new ArrayList<>(participants); }
    public synchronized ArrayList<UserInfo> getHistoricalParticipants() {
        return historicalParticipants != null ? new ArrayList<>(historicalParticipants) : new ArrayList<>(participants);
    }
    public ConversationType getType() { return type; }

    @Override
    public synchronized String toString() {
        String participantsText = participants.toString();
        if (participantsText.length() >= 2
                && participantsText.charAt(0) == '['
                && participantsText.charAt(participantsText.length() - 1) == ']') {
            participantsText = participantsText.substring(1, participantsText.length() - 1);
        }
        return participantsText;
    }

    public synchronized void append(Message m) { messages.add(m); }

    public static Conversation fromFile(File f) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            return (Conversation) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
