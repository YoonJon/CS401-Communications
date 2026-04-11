package shared.networking;

import shared.enums.RequestType;
import shared.payload.Payload;
import java.io.Serializable;

public class Request implements Serializable {
    private RequestType type;
    private Payload payload;

    public Request(RequestType t, Payload p) {
        this.type = t;
        this.payload = p;
    }

    public RequestType getType() { return type; }
    public Payload getPayload() { return payload; }
}
