import server.ServerController;

import java.util.Locale;
import java.util.Scanner;

import java.net.SocketException;

/**
 * Resolves a local IPv4 via IP_Finder, prints it, and starts ServerController.
 *
 * Usage:
 *   java -jar utils/server-with-ip-launcher.jar
 */
public class ServerWithIPLauncher {
    public static void main(String[] args) {
        String dataRoot = "data";
        String seedMode = null; // bottomOnly | withHeroes
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a == null) continue;
            if (a.startsWith("--dataRoot=")) {
                dataRoot = a.substring("--dataRoot=".length());
            } else if (a.equals("--dataRoot") && i + 1 < args.length) {
                dataRoot = args[++i];
            } else if (a.startsWith("--mode=")) {
                seedMode = a.substring("--mode=".length());
            } else if (a.equals("--mode") && i + 1 < args.length) {
                seedMode = args[++i];
            } else if (a.equals("1")) {
                seedMode = "bottomOnly";
            } else if (a.equals("2")) {
                seedMode = "withHeroes";
            }
        }

        if (seedMode == null) {
            // Interactive selection for demos; accepts: 1 or 2, or bottomOnly/withHeroes tokens.
            System.out.println("Select seeding runmode:");
            System.out.println("  1) bottomOnly   (seed only bottom-N users)");
            System.out.println("  2) withHeroes   (seed bottom-N users + premade Jon/Quan/Harumi accounts)");
            System.out.print("Enter 1 or 2 (or bottomOnly/withHeroes): ");
            Scanner sc = new Scanner(System.in);
            String line = sc.nextLine();
            seedMode = line == null ? "" : line.trim();
            if (seedMode.equals("1")) seedMode = "bottomOnly";
            if (seedMode.equals("2")) seedMode = "withHeroes";
        }

        seedMode = seedMode.toLowerCase(Locale.ROOT);
        if (seedMode.equals("1")) seedMode = "bottomonly";
        if (seedMode.equals("2")) seedMode = "withheroes";
        if (!seedMode.equals("bottomonly") && !seedMode.equals("withheroes")) {
            // Defensive: SeedSerializedData validates these, but keep error message readable here too.
            System.out.println("Unrecognized mode '" + seedMode + "'. Expected bottomOnly or withHeroes.");
        }

        // Reseed before starting server.
        try {
            SeedSerializedData.main(new String[] { dataRoot, "80", seedMode });
        } catch (Exception e) {
            System.out.println("Seeding failed: " + e.getMessage());
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }

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
        ServerController.main(new String[] { dataRoot });
    }
}
