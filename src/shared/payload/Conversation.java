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
     * Sequence number allocated from the server's global counter at conversation creation (same allocation path as
     * messages). Used when ordering threads that still have no messages; avoids ties from unrelated streams sharing
     * the same peek value.
     */
    private final long sortSequenceSentinel;

    /**
     * @param conversationId stable numeric id (from server counter)
     * @param participants initial members; copied defensively. If the size is 2, {@link ConversationType#PRIVATE}
     * is used; otherwise {@link ConversationType#GROUP}. The same initial snapshot is stored in
     * {@linkplain #getHistoricalParticipants() historical participants}. Removals from the active roster
     * do not remove entries from historical participants; use {@link #addParticipants(ArrayList)} so new
     * members are recorded in both lists. {@link #getType()} is fixed for the lifetime of this object;
     * adding people to a {@link ConversationType#PRIVATE} thread is done by forking (see server) into a new conversation.
     * @param sortSequenceSentinel next global message sequence issued when this conversation is created (consumes one number from the server counter)
     */
    public Conversation(long conversationId, ArrayList<UserInfo> participants, long sortSequenceSentinel) {
        this.conversationId = conversationId;
        this.messages = new ArrayList<>();
        this.participants = new ArrayList<>(participants != null ? participants : new ArrayList<>());
        this.historicalParticipants = new ArrayList<>(this.participants);
        this.type = this.participants.size() == 2 ? ConversationType.PRIVATE : ConversationType.GROUP;
        this.sortSequenceSentinel = sortSequenceSentinel;
    }

    public long getSortSequenceSentinel() {
        return sortSequenceSentinel;
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

    /**
     * Records {@code user} in {@link #getHistoricalParticipants()} only — does NOT add to the
     * active {@link #getParticipants()} roster. Used by the silent admin "join" flow so the
     * admin's sender id resolves to a name in every client's message renderer without
     * appearing in the participant list. Idempotent; returns {@code true} only when an
     * entry was actually appended.
     */
    public synchronized boolean addToHistoricalOnly(UserInfo user) {
        if (user == null || user.getUserId() == null || historicalParticipants == null) {
            return false;
        }
        for (UserInfo u : historicalParticipants) {
            if (u != null && user.getUserId().equals(u.getUserId())) {
                return false;
            }
        }
        historicalParticipants.add(user);
        return true;
    }

    /**
     * Merges {@code meta} into this conversation: replaces the active {@link #getParticipants()}
     * list with the snapshot, and appends any new entries to {@link #getHistoricalParticipants()}
     * (historical is monotonic — never trim). Used by the client to apply
     * {@link ConversationMetadata} updates received from the server.
     */
    public synchronized void applyMetadata(ConversationMetadata meta) {
        if (meta == null) return;
        participants.clear();
        if (meta.getParticipants() != null) {
            for (UserInfo u : meta.getParticipants()) {
                if (u != null) participants.add(u);
            }
        }
        if (historicalParticipants != null && meta.getHistoricalParticipants() != null) {
            for (UserInfo u : meta.getHistoricalParticipants()) {
                if (u == null || u.getUserId() == null) continue;
                boolean exists = false;
                for (UserInfo h : historicalParticipants) {
                    if (u.getUserId().equals(h.getUserId())) { exists = true; break; }
                }
                if (!exists) historicalParticipants.add(u);
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
