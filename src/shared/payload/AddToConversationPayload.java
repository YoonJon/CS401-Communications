package shared.payload;

import java.util.ArrayList;

import shared.networking.User.UserInfo;

public class AddToConversationPayload implements RequestPayload {
    private final ArrayList<UserInfo> participants;
    private final long targetConversationId;

    public AddToConversationPayload(ArrayList<UserInfo> p, long targetConversationId) {
        this.participants = p;
        this.targetConversationId = targetConversationId;
    }

    public ArrayList<UserInfo> getParticipants() { return participants; }
    public long getTargetConversationId() { return targetConversationId; }
}
