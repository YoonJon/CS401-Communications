package shared.networking;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Server-side UDP responder that lets clients on the same Wi-Fi auto-discover
 * the TCP server's IP and port.
 *
 * Lifecycle:
 *   new DiscoveryResponder(advertisedIp, tcpPort).start();
 *   Runtime.getRuntime().addShutdownHook(new Thread(responder::close));
 *
 * Design notes:
 * - Daemon thread, so it never blocks JVM shutdown.
 * - BindException on the discovery port is logged and ignored — the TCP server
 *   keeps running. This means a duplicate launcher can't crash the primary.
 * - Reply payload is parsed by DiscoveryClient; format must match
 *   DiscoveryProtocol.RESPONSE_PREFIX + " " + ip + " " + tcpPort.
 */
public final class DiscoveryResponder {

    private final String advertisedIp;
    private final int advertisedTcpPort;

    private volatile DatagramSocket socket;
    private volatile Thread thread;

    public DiscoveryResponder(String advertisedIp, int advertisedTcpPort) {
        this.advertisedIp = advertisedIp;
        this.advertisedTcpPort = advertisedTcpPort;
    }

    public void start() {
        try {
            socket = new DatagramSocket(DiscoveryProtocol.DISCOVERY_PORT);
        } catch (SocketException e) {
            System.err.println("[DiscoveryResponder] could not bind UDP "
                    + DiscoveryProtocol.DISCOVERY_PORT + " — auto-discovery disabled ("
                    + e.getMessage() + ")");
            return;
        }

        thread = new Thread(this::loop, "DiscoveryResponder");
        thread.setDaemon(true);
        thread.start();

        System.out.println("[DiscoveryResponder] listening on UDP "
                + DiscoveryProtocol.DISCOVERY_PORT + ", advertising "
                + advertisedIp + ":" + advertisedTcpPort);
    }

    private void loop() {
        byte[] buffer = new byte[DiscoveryProtocol.MAX_PACKET_BYTES];
        String reply = DiscoveryProtocol.RESPONSE_PREFIX + " "
                + advertisedIp + " " + advertisedTcpPort;
        byte[] replyBytes = reply.getBytes(StandardCharsets.US_ASCII);

        while (!socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (Exception e) {
                // Socket closed via close() — exit quietly.
                return;
            }

            String payload = new String(
                    packet.getData(), packet.getOffset(), packet.getLength(),
                    StandardCharsets.US_ASCII).trim();
            if (!DiscoveryProtocol.REQUEST_MAGIC.equals(payload)) {
                continue;
            }

            DatagramPacket response = new DatagramPacket(
                    replyBytes, replyBytes.length,
                    packet.getAddress(), packet.getPort());
            try {
                socket.send(response);
            } catch (Exception e) {
                // Best-effort reply; ignore transient send failures.
            }
        }
    }

    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * Resolves the IP a client should use to reach this server's TCP port,
     * given the bind IP the server actually bound to.
     *
     * - Specific IPv4 (e.g. 192.168.1.42) → returned as-is.
     * - localhost / 127.x.x.x → "127.0.0.1" (single-machine demos still work;
     *   LAN clients can't reach a localhost-bound TCP server anyway).
     * - 0.0.0.0 / null / blank → first non-loopback IPv4 found via NetworkInterface.
     *
     * Returns null if no usable IP could be resolved (e.g. no network).
     */
    public static String findAdvertisableIp(String bindIp) {
        if (bindIp == null || bindIp.isBlank() || bindIp.equals("0.0.0.0")) {
            return firstNonLoopbackIPv4();
        }
        if (bindIp.equalsIgnoreCase("localhost") || bindIp.startsWith("127.")) {
            return "127.0.0.1";
        }
        return bindIp;
    }

    private static String firstNonLoopbackIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return null;
            for (NetworkInterface nic : Collections.list(interfaces)) {
                if (!nic.isUp() || nic.isLoopback()) continue;
                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    if (addr.isLoopbackAddress() || !(addr instanceof Inet4Address)) continue;
                    return addr.getHostAddress();
                }
            }
        } catch (SocketException e) {
            // Fall through to null — caller logs and skips discovery.
        }
        return null;
    }
}
