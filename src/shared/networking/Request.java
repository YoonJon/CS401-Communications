package shared.networking;

import shared.enums.RequestType;
import shared.payload.RequestPayload;
import java.io.Serializable;

public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    private final RequestType type;
    private final RequestPayload payload;
    private final String senderId;

    /** Request with no payload (e.g. ping). {@code senderId} may be {@code null} before session bind. */
    public Request(RequestType t, String senderId) {
        this(t, null, senderId);
    }

    public Request(RequestType t, RequestPayload p, String senderId) {
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
