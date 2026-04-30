import server.ServerController;

import java.net.SocketException;

/**
 * Resolves a local IPv4 via IP_Finder, prints it, and starts ServerController.
 *
 * Usage:
 *   java -jar utils/server-with-ip-launcher.jar
 */
public class ServerWithIPLauncher {
    public static void main(String[] args) {
        String bindIPv4;

        try {
            String localIp = IP_Finder.findPrimaryLocalIPv4();
            if (localIp == null) {
                System.out.println("No non-loopback IPv4 found.");
            } else {
                System.out.println("Resolved local IPv4: " + localIp);
            }
            bindIPv4 = localIp;
        } catch (SocketException e) {
            System.out.println("Could not resolve local IPv4: " + e.getMessage());
            bindIPv4 = "0.0.0.0";
        }

        if (bindIPv4 == null || bindIPv4.isBlank()) {
            bindIPv4 = "0.0.0.0";
        }

        System.setProperty("server.bind.ip", bindIPv4);
        ServerController.main(new String[0]);
    }
}
