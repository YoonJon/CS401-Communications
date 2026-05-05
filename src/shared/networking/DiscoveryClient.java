package shared.networking;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * LAN auto-discovery for clients. Broadcasts a UDP probe and waits for the
 * server's reply. Returns "ip:port" on success, null on timeout.
 *
 * Strategy:
 * - Send to limited broadcast (255.255.255.255) AND each interface's directed
 *   broadcast, since some Wi-Fi APs filter limited broadcast.
 * - Retry up to MAX_ATTEMPTS times; total budget ~1.6s with the default
 *   500ms timeout per attempt.
 * - Accept the first valid RESPONSE_PREFIX packet; ignore any other traffic
 *   on the discovery port.
 */
public final class DiscoveryClient {

    private DiscoveryClient() {}

    /**
     * @return "host:port" if a server replied within the retry budget, else null.
     */
    public static String discover() {
        byte[] requestBytes = DiscoveryProtocol.REQUEST_MAGIC
                .getBytes(StandardCharsets.US_ASCII);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(DiscoveryProtocol.RESPONSE_TIMEOUT_MS);

            List<InetAddress> targets = broadcastTargets();

            for (int attempt = 0; attempt < DiscoveryProtocol.MAX_ATTEMPTS; attempt++) {
                for (InetAddress target : targets) {
                    try {
                        DatagramPacket out = new DatagramPacket(
                                requestBytes, requestBytes.length,
                                target, DiscoveryProtocol.DISCOVERY_PORT);
                        socket.send(out);
                    } catch (Exception sendErr) {
                        // Some interfaces (VPN, virtual) reject broadcast; skip and try the rest.
                    }
                }

                String hit = awaitReply(socket);
                if (hit != null) {
                    return hit;
                }
            }
        } catch (Exception e) {
            System.err.println("[DiscoveryClient] discovery failed: " + e.getMessage());
        }

        return null;
    }

    private static String awaitReply(DatagramSocket socket) {
        byte[] buffer = new byte[DiscoveryProtocol.MAX_PACKET_BYTES];
        long deadline = System.currentTimeMillis() + DiscoveryProtocol.RESPONSE_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (SocketTimeoutException timeout) {
                return null;
            } catch (Exception e) {
                return null;
            }

            String payload = new String(
                    packet.getData(), packet.getOffset(), packet.getLength(),
                    StandardCharsets.US_ASCII).trim();

            if (!payload.startsWith(DiscoveryProtocol.RESPONSE_PREFIX + " ")) {
                continue;
            }

            String[] parts = payload.split("\\s+");
            if (parts.length < 3) {
                continue;
            }
            try {
                int port = Integer.parseInt(parts[2]);
                return parts[1] + ":" + port;
            } catch (NumberFormatException nfe) {
                // Malformed reply; keep waiting for a valid one within the deadline.
            }
        }
        return null;
    }

    private static List<InetAddress> broadcastTargets() {
        List<InetAddress> result = new ArrayList<>();
        try {
            result.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {}

        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            if (nics == null) return result;
            for (NetworkInterface nic : Collections.list(nics)) {
                if (!nic.isUp() || nic.isLoopback()) continue;
                for (InterfaceAddress ia : nic.getInterfaceAddresses()) {
                    InetAddress bcast = ia.getBroadcast();
                    if (bcast != null) {
                        result.add(bcast);
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to the limited broadcast already in the list.
        }
        return result;
    }
}
