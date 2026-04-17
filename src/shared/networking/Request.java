package shared.networking;

import shared.enums.RequestType;
import shared.payload.RequestPayload;
import java.io.Serializable;

public class Request implements Serializable {
    private final RequestType type;
    private final RequestPayload payload;
    private final String senderId;

    public Request(RequestType t) {
        this(t, null, null);
    }

    public Request(RequestType t, String senderId) {
        this(t, null, senderId);
    }

    public Request(RequestType t, RequestPayload p) {
        this(t, p, null);
    }

    public Request(RequestType t, RequestPayload p, String senderId) {
        // Sentinel for invalid enum input: treat null type as a null-request.
        if (t == null) {
            this.type = null;
            this.payload = null;
            this.senderId = null;
            return;
        }
        this.type = t;
        this.payload = p;
        this.senderId = senderId;
    }

    public RequestType getType() { return type; }
    public RequestPayload getPayload() { return payload; }
    public String getSenderId() { return senderId; }
}
