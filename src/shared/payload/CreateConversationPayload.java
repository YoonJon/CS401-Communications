package shared.payload;

import java.util.ArrayList;

public class CreateConversationPayload implements RequestPayload {
    private final ArrayList<UserInfo> participants;

    public CreateConversationPayload(ArrayList<UserInfo> p) {
        this.participants = p;
    }

    public ArrayList<UserInfo> getParticipants() { return participants; }
}
