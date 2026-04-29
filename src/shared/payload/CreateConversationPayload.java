package shared.payload;

import java.util.ArrayList;
import java.util.Collections;

import shared.networking.User.UserInfo;

public class CreateConversationPayload implements RequestPayload {
    private static final long serialVersionUID = 1L;

    private final ArrayList<UserInfo> participants;

    public CreateConversationPayload(ArrayList<UserInfo> p) {
        this.participants = new ArrayList<>(p);
    }

    public java.util.List<UserInfo> getParticipants() { return Collections.unmodifiableList(participants); }
}
