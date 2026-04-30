import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;

public class IP_Finder {
    /**
     * Returns the first non-loopback IPv4 address on an active network interface.
     * Returns null when no usable address is found.
     */
    public static String findPrimaryLocalIPv4() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) {
            return null;
        }

        for (NetworkInterface netIf : Collections.list(interfaces)) {
            if (!netIf.isUp() || netIf.isLoopback()) {
                continue;
            }

            for (InetAddress address : Collections.list(netIf.getInetAddresses())) {
                if (address.isLoopbackAddress() || !(address instanceof Inet4Address)) {
                    continue;
                }
                return address.getHostAddress();
            }
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            boolean foundAddress = false;
            String primary = findPrimaryLocalIPv4();
            if (primary != null) {
                System.out.println("Primary local IPv4: " + primary);
            } else {
                System.out.println("Primary local IPv4: not found");
            }
            System.out.println();
            System.out.println("Local IPv4 addresses by network interface:");
            System.out.println("-------------------------------------------");

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                System.out.println("No network interfaces found.");
                return;
            }
            for (NetworkInterface netIf : Collections.list(interfaces)) {
                // Skip loopback/down interfaces to keep output focused on usable local IPs.
                if (!netIf.isUp() || netIf.isLoopback()) {
                    continue;
                }

                boolean printedHeader = false;
                for (InetAddress address : Collections.list(netIf.getInetAddresses())) {
                    if (address.isLoopbackAddress() || !(address instanceof Inet4Address)) {
                        continue;
                    }

                    if (!printedHeader) {
                        String displayName = netIf.getDisplayName() != null ? netIf.getDisplayName() : "Unknown";
                        System.out.println();
                        System.out.println("Interface: " + netIf.getName() + " (" + displayName + ")");
                        printedHeader = true;
                    }

                    System.out.println("  - IPv4: " + address.getHostAddress());
                    foundAddress = true;
                }
            }

            if (!foundAddress) {
                System.out.println("No non-loopback local IP addresses found.");
            }
        } catch (SocketException e) {
            System.err.println("Failed to read network interfaces: " + e.getMessage());
        }
    }
}
