package shared.payload;

import java.util.ArrayList;

public class AddToConversationPayload implements RequestPayload {
    private final ArrayList<UserInfo> participants;
    private final String targetConversationId;

    public AddToConversationPayload(ArrayList<UserInfo> p, String t) {
        this.participants = p;
        this.targetConversationId = t;
    }

    public ArrayList<UserInfo> getParticipants() { return participants; }
    public String getTargetConversationId() { return targetConversationId; }
}
