import shared.enums.UserType;
import shared.networking.User;
import shared.networking.User.UserInfo;
import shared.payload.Conversation;
import shared.payload.Message;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seeds serialized .user and .conversation files under a DataManager-compatible data root.
 * Includes bulk bottom-N users, standard private/group threads, plus Jon / Quan / Harumi with
 * fixed demo logins and thirty extra private threads (ten partners each from the bulk roster).
 * Conversation ids grow over a simulated timeline with irregular gaps (like a long-running server).
 * Message sequence numbers increase monotonically in chronological order with small random gaps; timestamps
 * are spread across months with uneven spacing between messages.
 *
 * Usage:
 *   java -cp out SeedSerializedData [dataRoot] [bottomUserCount]
 *
 * Defaults:
 *   dataRoot = "data"
 *   bottomUserCount = 80
 */
public class SeedSerializedData {

    private static final String JON_ID = "Q7M2X9K4LP";
    private static final String QUAN_ID = "N4R8T1BZQK";
    private static final String HARUMI_ID = "V3P6L0WQ9D";

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
        List<RosterRow> fullRoster = parseRosterRows(allAuthorizedUsers);
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

        // Jon / Quan / Harumi — same test presentation logins (overwrites if those ids were in bulk slice)
        writeHeroUser(userDataPath, findRow(fullRoster, JON_ID), "pretzul", "a", adminIds);
        writeHeroUser(userDataPath, findRow(fullRoster, QUAN_ID), "user123", "a", adminIds);
        writeHeroUser(userDataPath, findRow(fullRoster, HARUMI_ID), "user456", "a", adminIds);

        ArrayList<ConvSeed> pending = new ArrayList<>();

        // 30 private conversations across the first 60 selected users.
        for (int i = 0; i + 1 < Math.min(userInfos.size(), 60); i += 2) {
            ArrayList<UserInfo> participants = new ArrayList<>();
            participants.add(userInfos.get(i));
            participants.add(userInfos.get(i + 1));
            pending.add(new ConvSeed(participants, ThreadLocalRandom.current().nextInt(2, 11)));
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
            pending.add(new ConvSeed(participants, ThreadLocalRandom.current().nextInt(2, 11)));
        }

        // 30 privates: Jon, Quan, Harumi × ten distinct bulk partners
        Set<String> heroIds = Set.of(JON_ID, QUAN_ID, HARUMI_ID);
        List<UserInfo> partnerPool = new ArrayList<>();
        for (UserInfo u : userInfos) {
            if (u != null && u.getUserId() != null && !heroIds.contains(u.getUserId())) {
                partnerPool.add(u);
            }
        }
        if (partnerPool.size() < 10) {
            throw new IllegalStateException("Need at least 10 bulk-seeded users besides Jon/Quan/Harumi ids; found "
                    + partnerPool.size());
        }
        Collections.shuffle(partnerPool);
        List<UserInfo> partners = new ArrayList<>(partnerPool.subList(0, 10));
        UserInfo jonInfo = heroUserInfo(findRow(fullRoster, JON_ID), adminIds);
        UserInfo quanInfo = heroUserInfo(findRow(fullRoster, QUAN_ID), adminIds);
        UserInfo harumiInfo = heroUserInfo(findRow(fullRoster, HARUMI_ID), adminIds);
        for (UserInfo hero : List.of(jonInfo, quanInfo, harumiInfo)) {
            for (UserInfo other : partners) {
                ArrayList<UserInfo> pair = new ArrayList<>(2);
                pair.add(hero);
                pair.add(other);
                pending.add(new ConvSeed(pair, ThreadLocalRandom.current().nextInt(2, 11)));
            }
        }

        int nConv = pending.size();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long windowStartMs = System.currentTimeMillis() - 200L * 24 * 3600 * 1000;
        long windowLenMs = 198L * 24 * 3600 * 1000;
        for (ConvSeed s : pending) {
            s.syntheticCreatedMs = windowStartMs + rnd.nextLong(0, windowLenMs);
        }
        pending.sort(Comparator.comparingLong(seed -> seed.syntheticCreatedMs));

        long[] nextMessageSeq = { 40 + rnd.nextInt(1, 120) };
        long nextConvId = 12 + rnd.nextInt(1, 40);
        long maxConvId = 0L;
        long lastMessageSeq = nextMessageSeq[0] - 1;

        for (int i = 0; i < nConv; i++) {
            long cid = nextConvId;
            maxConvId = Math.max(maxConvId, cid);
            nextConvId += rnd.nextInt(2, 22);
            ConvSeed s = pending.get(i);
            Conversation c = new Conversation(cid, s.participants);
            lastMessageSeq = appendMessagesOrganic(c, s, nextMessageSeq);
            writeObject(new File(convDataPath.toFile(), cid + ".conversation"), c);
        }

        Properties props = new Properties();
        props.setProperty("conversationIdCounter", Long.toString(maxConvId));
        props.setProperty("messageSequenceCounter", Long.toString(lastMessageSeq));
        try (FileOutputStream out = new FileOutputStream(serverConfigPath.toFile())) {
            props.store(out, "Server counters (message sequence and conversation id)");
        }

        System.out.println("Seed complete.");
        System.out.println("Users seeded: " + users.size() + " (bulk) + 3 (Jon, Quan, Harumi demo logins)");
        int totalMsgs = 0;
        for (ConvSeed s : pending) {
            totalMsgs += s.messageCount;
        }
        System.out.println("Conversations seeded: " + nConv + " (timeline-sorted, id max=" + maxConvId + ")");
        System.out.println("Messages seeded: " + totalMsgs + " (monotonic seq up to " + lastMessageSeq + ")");
        System.out.println("User source: bottom " + bottomUserCount + " entries of authorized_users.txt");
    }

    private static final class ConvSeed {
        final ArrayList<UserInfo> participants;
        final int messageCount;
        /** Wall-clock-ish anchor for this thread; set before sorting pending conversations. */
        long syntheticCreatedMs;

        ConvSeed(ArrayList<UserInfo> participants, int messageCount) {
            this.participants = participants;
            this.messageCount = messageCount;
        }
    }

    /**
     * Appends messages with strictly increasing sequence numbers and timestamps that drift forward
     * from the conversation's synthetic creation time (bursts, quiet stretches, occasional multi-day gaps).
     *
     * @param nextMessageSeq single-element array holding the next sequence to assign; updated as messages append
     * @return the highest sequence number written for this conversation
     */
    private static long appendMessagesOrganic(Conversation c, ConvSeed seed, long[] nextMessageSeq) {
        ArrayList<UserInfo> roster = seed.participants;
        if (roster == null || roster.isEmpty() || seed.messageCount <= 0) {
            return nextMessageSeq[0] - 1;
        }
        long convId = c.getConversationId();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long t = seed.syntheticCreatedMs;
        long lastSeq = nextMessageSeq[0] - 1;
        for (int i = 0; i < seed.messageCount; i++) {
            if (i > 0) {
                if (rnd.nextDouble() < 0.14) {
                    t += rnd.nextLong(12L * 3600 * 1000, 5L * 24 * 3600 * 1000);
                } else if (rnd.nextDouble() < 0.08) {
                    t += rnd.nextLong(45_000L, 900_000L);
                } else {
                    t += rnd.nextLong(120_000L, 3L * 3600 * 1000);
                }
            }
            long seq = nextMessageSeq[0];
            nextMessageSeq[0] = seq + 1 + rnd.nextInt(0, 3);
            lastSeq = seq;
            UserInfo sender = roster.get(rnd.nextInt(roster.size()));
            String text = "seed-" + convId + "-" + (i + 1);
            c.append(new Message(text, seq, new Date(t), sender.getUserId(), convId));
        }
        return lastSeq;
    }

    private static void writeHeroUser(Path userDataPath, RosterRow row, String login, String password, Set<String> adminIds)
            throws Exception {
        UserType userType = adminIds.contains(row.userId) ? UserType.ADMIN : UserType.USER;
        User user = new User(row.userId, row.name, login, password, userType);
        writeObject(new File(userDataPath.toFile(), row.userId + ".user"), user);
    }

    private static UserInfo heroUserInfo(RosterRow r, Set<String> adminIds) {
        UserType t = adminIds.contains(r.userId) ? UserType.ADMIN : UserType.USER;
        return new User(r.userId, r.name, "_", "_", t).toUserInfo();
    }

    private static RosterRow findRow(List<RosterRow> rows, String userId) {
        for (RosterRow r : rows) {
            if (userId.equals(r.userId)) {
                return r;
            }
        }
        throw new IllegalStateException("authorized_users.txt must contain user id " + userId);
    }

    private static List<RosterRow> parseRosterRows(List<String> lines) {
        List<RosterRow> out = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split(",", 2);
            if (parts.length != 2) {
                continue;
            }
            String id = parts[0].trim();
            String name = parts[1].trim();
            if (!id.isEmpty() && !name.isEmpty()) {
                out.add(new RosterRow(id, name));
            }
        }
        return out;
    }

    private record RosterRow(String userId, String name) {}

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
