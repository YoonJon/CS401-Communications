package shared.payload;

import shared.enums.ConversationType;
import java.util.ArrayList;

public class ConversationMetadata implements ResponsePayload {
    private final long conversationId;
    private final ArrayList<UserInfo> participants;
    private final ArrayList<UserInfo> historicalParticipants;
    private final ConversationType type;

    public ConversationMetadata(long c_id, ArrayList<UserInfo> p, ArrayList<UserInfo> hp, ConversationType t) {
        this.conversationId = c_id;
        this.participants = p;
        this.historicalParticipants = hp;
        this.type = t;
    }

    public long getConversationId() { return conversationId; }
    public ArrayList<UserInfo> getParticipants() { return participants; }
    public ArrayList<UserInfo> getHistoricalParticipants() { return historicalParticipants; }
    public ConversationType getType() { return type; }
}
