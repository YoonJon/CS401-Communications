package shared.networking;

import shared.enums.ResponseType;
import shared.payload.Payload;
import java.io.Serializable;

public class Response implements Serializable {
    private ResponseType type;
    private Payload payload;

    public Response(ResponseType t, Payload p) {
        this.type = t;
        this.payload = p;
    }

    public ResponseType getType() { return type; }
    public Payload getPayload() { return payload; }
}
