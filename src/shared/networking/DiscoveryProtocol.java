package shared.networking;

/**
 * Wire-format constants for the LAN auto-discovery handshake.
 *
 * Protocol:
 *   Client broadcasts UDP packet with payload = REQUEST_MAGIC.
 *   Server replies with payload = RESPONSE_PREFIX + " " + ip + " " + tcpPort.
 *
 * Magic strings are versioned so future protocol changes can coexist without
 * silently misparsing older packets on the LAN.
 */
public final class DiscoveryProtocol {

    public static final int DISCOVERY_PORT = 8081;

    public static final String REQUEST_MAGIC = "CS401_DISCOVER_v1";
    public static final String RESPONSE_PREFIX = "CS401_SERVER_v1";

    public static final int RESPONSE_TIMEOUT_MS = 500;
    public static final int MAX_ATTEMPTS = 3;

    public static final int MAX_PACKET_BYTES = 256;

    private DiscoveryProtocol() {}
}
