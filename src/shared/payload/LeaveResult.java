package shared.payload;

public class LeaveResult implements Payload {
    private String c_id;

    public LeaveResult(String c_id) {
        this.c_id = c_id;
    }

    public String getLeftConversationID() { return c_id; }
}
