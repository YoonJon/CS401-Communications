import shared.enums.UserType;
import shared.networking.User;
import shared.networking.User.UserInfo;
import shared.payload.Conversation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Seeds serialized .user and .conversation files under a DataManager-compatible data root.
 *
 * Usage:
 *   java -cp out SeedSerializedData [dataRoot] [bottomUserCount]
 *
 * Defaults:
 *   dataRoot = "data"
 *   bottomUserCount = 80
 */
public class SeedSerializedData {

    public static void main(String[] args) throws Exception {
        String dataRoot = args.length > 0 ? args[0] : "data";
        int bottomUserCount = args.length > 1 ? Integer.parseInt(args[1]) : 80;

        Path root = Path.of(dataRoot);
        Path authUsersPath = root.resolve("server_data/authorized_ids/authorized_users.txt");
        Path authAdminsPath = root.resolve("server_data/authorized_ids/authorized_admins.txt");
        Path userDataPath = root.resolve("user_data");
        Path convDataPath = root.resolve("conversation_data");
        Path serverConfigPath = root.resolve("server_data/server_config.txt");

        List<String> allAuthorizedUsers = Files.readAllLines(authUsersPath, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        if (allAuthorizedUsers.size() < bottomUserCount) {
            throw new IllegalStateException("authorized_users has only " + allAuthorizedUsers.size()
                    + " entries, cannot take bottom " + bottomUserCount);
        }
        List<String> selected = allAuthorizedUsers.subList(allAuthorizedUsers.size() - bottomUserCount, allAuthorizedUsers.size());

        Set<String> adminIds = new HashSet<>(Files.readAllLines(authAdminsPath, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList());

        Files.createDirectories(userDataPath);
        Files.createDirectories(convDataPath);
        Files.createDirectories(serverConfigPath.getParent());

        clearSerializedFiles(userDataPath, ".user");
        clearSerializedFiles(convDataPath, ".conversation");

        ArrayList<User> users = new ArrayList<>();
        ArrayList<UserInfo> userInfos = new ArrayList<>();

        int loginCounter = 1;
        for (String row : selected) {
            String[] parts = row.split(",", 2);
            if (parts.length != 2) {
                continue;
            }
            String userId = parts[0].trim();
            String name = parts[1].trim();
            if (userId.isEmpty() || name.isEmpty()) {
                continue;
            }

            String loginName = "seed" + String.format("%03d", loginCounter++);
            String password = "seedpass";
            UserType userType = adminIds.contains(userId) ? UserType.ADMIN : UserType.USER;
            User user = new User(userId, name, loginName, password, userType);
            users.add(user);
            userInfos.add(user.toUserInfo());

            writeObject(new File(userDataPath.toFile(), userId + ".user"), user);
        }

        long conversationId = 1L;

        // 30 private conversations across the first 60 selected users.
        for (int i = 0; i + 1 < Math.min(userInfos.size(), 60); i += 2) {
            ArrayList<UserInfo> participants = new ArrayList<>();
            participants.add(userInfos.get(i));
            participants.add(userInfos.get(i + 1));
            Conversation c = new Conversation(conversationId, participants);
            writeObject(new File(convDataPath.toFile(), conversationId + ".conversation"), c);
            conversationId++;
        }

        // 10 group conversations (5 participants each) over all selected users.
        int groupCount = 10;
        int groupSize = 5;
        for (int g = 0; g < groupCount; g++) {
            ArrayList<UserInfo> participants = new ArrayList<>();
            for (int j = 0; j < groupSize; j++) {
                int idx = (g * groupSize + j) % userInfos.size();
                participants.add(userInfos.get(idx));
            }
            Conversation c = new Conversation(conversationId, participants);
            writeObject(new File(convDataPath.toFile(), conversationId + ".conversation"), c);
            conversationId++;
        }

        Properties props = new Properties();
        props.setProperty("conversationIdCounter", Long.toString(conversationId - 1));
        props.setProperty("messageSequenceCounter", "0");
        try (FileOutputStream out = new FileOutputStream(serverConfigPath.toFile())) {
            props.store(out, "Server counters (message sequence and conversation id)");
        }

        System.out.println("Seed complete.");
        System.out.println("Users seeded: " + users.size());
        System.out.println("Conversations seeded: " + (conversationId - 1));
        System.out.println("User source: bottom " + bottomUserCount + " entries of authorized_users.txt");
    }

    private static void clearSerializedFiles(Path dir, String extension) throws Exception {
        File[] files = dir.toFile().listFiles((d, name) -> name.endsWith(extension));
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (!f.delete()) {
                throw new IllegalStateException("Failed to delete existing seed file: " + f.getAbsolutePath());
            }
        }
    }

    private static void writeObject(File path, Object value) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(path);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(value);
        }
    }
}
