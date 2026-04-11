package shared.payload;

import java.util.ArrayList;

public class AddToConversationPayload implements Payload {
    private ArrayList<UserInfo> participants;
    private String targetConversationId;

    public AddToConversationPayload(ArrayList<UserInfo> p, String t) {
        this.participants = p;
        this.targetConversationId = t;
    }

    public ArrayList<UserInfo> getParticipants() { return participants; }
    public String getTargetConversationId() { return targetConversationId; }
}
