package shared.networking;

import shared.enums.RequestType;
import shared.payload.RequestPayload;
import java.io.Serializable;

public class Request implements Serializable {
    private final RequestType type;
    private final RequestPayload payload;

    public Request(RequestType t) {
        this(t, null);
    }

    public Request(RequestType t, RequestPayload p) {
        // Sentinel for invalid enum input: treat null type as a null-request.
        if (t == null) {
            this.type = null;
            this.payload = null;
            return;
        }
        this.type = t;
        this.payload = p;
    }

    public RequestType getType() { return type; }
    public RequestPayload getPayload() { return payload; }
}
