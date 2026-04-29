package shared.networking;

import shared.enums.ResponseType;
import shared.payload.ResponsePayload;
import java.io.Serializable;

public class Response implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ResponseType type;
    private final ResponsePayload payload;

    public Response(ResponseType t) {
        this(t, null);
    }

    public Response(ResponseType t, ResponsePayload p) {
        this.type = t;
        this.payload = p;
    }

    public ResponseType getType() { return type; }
    public ResponsePayload getPayload() { return payload; }
}
