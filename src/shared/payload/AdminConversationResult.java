package shared.payload;

import java.util.ArrayList;

public class AdminConversationResult implements Payload {
    private ArrayList<ConversationMetadata> result;

    public AdminConversationResult(ArrayList<ConversationMetadata> cm) {
        this.result = cm;
    }

    public ArrayList<ConversationMetadata> getConversations() { return result; }
}
