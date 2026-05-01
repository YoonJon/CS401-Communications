package shared.payload;

import shared.networking.User.UserInfo;
import shared.enums.ConversationType;
import java.util.ArrayList;
import java.util.Comparator;

public class ConversationMetadata implements ResponsePayload {
    private static final long serialVersionUID = 1L;

    private static final Comparator<UserInfo> BY_NAME_THEN_ID =
        Comparator.comparing(UserInfo::getName, String.CASE_INSENSITIVE_ORDER)
                  .thenComparing(UserInfo::getUserId);

    private final long conversationId;
    private final ArrayList<UserInfo> participants;
    private final ArrayList<UserInfo> historicalParticipants;
    private final ConversationType type;
    private final String lastMessagePreview;
    private final long lastMessageTimestampMillis;
    private final int unreadCount;
    private final String displayName;

    /** Base constructor (no preview/unread metadata). */
    public ConversationMetadata(long c_id, ArrayList<UserInfo> p, ArrayList<UserInfo> hp, ConversationType t) {
        this(c_id, p, hp, t, null, 0L, 0, null);
    }

    /** Full constructor including last-message preview and unread count. */
    public ConversationMetadata(long c_id, ArrayList<UserInfo> p, ArrayList<UserInfo> hp, ConversationType t,
                                String lastMessagePreview, long lastMessageTimestampMillis,
                                int unreadCount, String displayName) {
        this.conversationId = c_id;
        this.participants = p != null ? new ArrayList<>(p) : new ArrayList<>();
        this.participants.sort(BY_NAME_THEN_ID);
        this.historicalParticipants = hp != null ? new ArrayList<>(hp) : new ArrayList<>();
        this.historicalParticipants.sort(BY_NAME_THEN_ID);
        this.type = t;
        this.lastMessagePreview = lastMessagePreview;
        this.lastMessageTimestampMillis = lastMessageTimestampMillis;
        this.unreadCount = unreadCount;
        this.displayName = displayName;
    }

    public long getConversationId() { return conversationId; }
    public ArrayList<UserInfo> getParticipants() { return new ArrayList<>(participants); }
    public ArrayList<UserInfo> getHistoricalParticipants() { return new ArrayList<>(historicalParticipants); }
    public ConversationType getType() { return type; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public long getLastMessageTimestampMillis() { return lastMessageTimestampMillis; }
    public int getUnreadCount() { return unreadCount; }
    public String getDisplayName() { return displayName; }

    @Override
    public String toString() {
        String participantsText = participants.toString();
        if (participantsText.length() >= 2
                && participantsText.charAt(0) == '['
                && participantsText.charAt(participantsText.length() - 1) == ']') {
            participantsText = participantsText.substring(1, participantsText.length() - 1);
        }
        return participantsText;
    }
}
