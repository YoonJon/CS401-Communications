package server;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import shared.enums.ConversationType;
import shared.enums.ResponseType;
import shared.enums.UserType;
import shared.networking.Request;
import shared.networking.Response;
import shared.networking.User;
import shared.networking.User.UserInfo;
import shared.payload.*;

/**
 * Server-side state and request handling for persisted data.
 * <p>
 * <b>Session invariant:</b> Every entry point except {@link #handleRegister} and {@link #handleLogin}
 * is invoked only after a successful sign-in (enforced by the controller). Callers are authenticated
 * users; these methods do not repeat login or session validation.
 */
public class DataManager {

    private static final long WRITER_SLEEP_MS = 100L;

    /**
     * Authoritative monotonic message sequence counter; persisted under
     * {@code messageSequenceCounter} in {@code server_data/server_config.txt}.
     */
    private static final AtomicLong messageSequenceCounter = new AtomicLong(0);

    /**
     * Monotonic numeric id assignment for new {@link Conversation} records; persisted under
     * {@code conversationIdCounter} in {@code server_data/server_config.txt}.
     */
    private static final AtomicLong conversationIdCounter = new AtomicLong(0);

    private ConcurrentMap<String, User> usersByUserID;
    private ConcurrentMap<String, User> usersByLoginName;
    /** User id → conversation ids that user belongs to. */
    private ConcurrentMap<String, Set<Long>> conversationIDsByUserID;
    /** Conversation id → user ids in that conversation. */
    private ConcurrentMap<Long, Set<String>> userIDsByConversationID;
    private ConcurrentMap<Long, Conversation> conversationsByConversationID;
    /** Serializes check-then-put in {@link #handleCreateConversation} so concurrent
     *  creates for the same PRIVATE pair cannot race past the duplicate-detection scan. */
    private final Object createConversationLock = new Object();
    private Map<String, String> authorizedUsers;
    private ArrayList<String> authorizedAdminIds;
    private CopyOnWriteArrayList<UserInfo> directoryUserInfos;

    /** Root for {@code server_data/}, {@code conversation_data/}, {@code user_data/}. */
    private final File dataRoot;
    private final File serverDataDir;
    private final File authorizedIdsDir;
    private final File authorizedUsersFile;
    private final File authorizedAdminsFile;
    private final File serverConfigFile;
    private final File conversationDataDir;
    private final File userDataDir;

    /** Backed by {@link ConcurrentHashMap#newKeySet()} (Java has no {@code ConcurrentHashSet}). */
    private final Set<String> dirtyUsers;
    private final Set<Long> dirtyConversations;

    /** Background flush of dirty users/conversations every {@value #WRITER_SLEEP_MS} ms. */
    private final Thread writerThread;

    /**
     * @param dataRootPath path to the root directory for persisted state (absolute or relative).
     *                     Layout under this root: {@code server_data/server_config.txt},
     *                     {@code server_data/authorized_ids/}, {@code conversation_data/},
     *                     {@code user_data/}. Tests may use e.g. {@code "test_data"} so production
     *                     {@code data/} is untouched.
     */
    public DataManager(String dataRootPath) {
        File root = new File(dataRootPath);
        this.dataRoot = root;
        this.serverDataDir = new File(root, "server_data");
        this.authorizedIdsDir = new File(serverDataDir, "authorized_ids");
        this.authorizedUsersFile = new File(authorizedIdsDir, "authorized_users.txt");
        this.authorizedAdminsFile = new File(authorizedIdsDir, "authorized_admins.txt");
        this.serverConfigFile = new File(serverDataDir, "server_config.txt");
        this.conversationDataDir = new File(root, "conversation_data");
        this.userDataDir = new File(root, "user_data");

        this.usersByUserID = new ConcurrentHashMap<>();
        this.usersByLoginName = new ConcurrentHashMap<>();
        this.conversationIDsByUserID = new ConcurrentHashMap<>();
        this.userIDsByConversationID = new ConcurrentHashMap<>();
        this.conversationsByConversationID = new ConcurrentHashMap<>();
        this.authorizedUsers = new HashMap<>();
        this.authorizedAdminIds = new ArrayList<>();
        this.directoryUserInfos = new CopyOnWriteArrayList<>();
        this.dirtyUsers = ConcurrentHashMap.newKeySet();
        this.dirtyConversations = ConcurrentHashMap.newKeySet();

        loadData();

        Thread t = new Thread(this::writerLoop, "DataManager-Writer");
        t.setDaemon(true);
        t.start();
        this.writerThread = t;
    }

    public void close() {
        writerThread.interrupt();
        try {
            writerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            for (User u : usersByUserID.values()) {
                dirtyUsers.add(u.getUserId());
            }
            for (Conversation c : conversationsByConversationID.values()) {
                dirtyConversations.add(c.getConversationId());
            }
            writeDirty();
            persistServerCounters();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sleeps {@value #WRITER_SLEEP_MS} ms between calls to {@link #writeDirty()}; exits when interrupted
     * (shutdown via {@link #close()}).
     */
    private void writerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(WRITER_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                writeDirty();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static long nextMessageSequenceNumber() {
        return messageSequenceCounter.incrementAndGet();
    }

    static long nextConversationId() {
        return conversationIdCounter.incrementAndGet();
    }

    private void persistUser(User user) {
        if (user != null && user.getUserId() != null) {
            dirtyUsers.add(user.getUserId());
        }
    }

    private void persistConversation(Conversation conversation) {
        if (conversation != null) {
            dirtyConversations.add(conversation.getConversationId());
        }
    }

    /**
     * For each id in {@link #dirtyUsers} and {@link #dirtyConversations}, loads the object from the
     * in-memory maps and writes it to the corresponding {@code .user} / {@code .conversation} file,
     * then clears both dirty sets.
     */
    public void writeDirty() throws IOException {
        Set<String> userIds = new HashSet<>(dirtyUsers);
        Set<Long> conversationIds = new HashSet<>(dirtyConversations);
        dirtyUsers.clear();
        dirtyConversations.clear();

        for (String userId : userIds) {
            User u = usersByUserID.get(userId);
            if (u != null) {
                try (FileOutputStream fos = new FileOutputStream(userPersistenceFile(userId));
                     ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                    oos.writeObject(u);
                }
            }
        }
        for (Long conversationId : conversationIds) {
            Conversation c = conversationsByConversationID.get(conversationId);
            if (c != null) {
                synchronized (c) {
                    try (FileOutputStream fos = new FileOutputStream(conversationPersistenceFile(conversationId));
                         ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                        oos.writeObject(c);
                    }
                }
            }
        }
    }

    private File userPersistenceFile(String userId) {
        return new File(userDataDir, userId + ".user");
    }

    private File conversationPersistenceFile(long conversationId) {
        return new File(conversationDataDir, Long.toString(conversationId) + ".conversation");
    }

    private void loadServerCounters() throws IOException {
        if (!serverConfigFile.isFile() || serverConfigFile.length() == 0) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(serverConfigFile)) {
            props.load(in);
        }
        String msg = props.getProperty("messageSequenceCounter");
        if (msg != null && !msg.isBlank()) {
            try {
                messageSequenceCounter.set(Math.max(0L, Long.parseLong(msg.trim())));
            } catch (NumberFormatException e) {
                System.err.println("WARNING: invalid messageSequenceCounter in server_config.txt: " + msg);
            }
        }
        String conv = props.getProperty("conversationIdCounter");
        if (conv != null && !conv.isBlank()) {
            try {
                conversationIdCounter.set(Math.max(0L, Long.parseLong(conv.trim())));
            } catch (NumberFormatException e) {
                System.err.println("WARNING: invalid conversationIdCounter in server_config.txt: " + conv);
            }
        }
    }

    private void persistServerCounters() throws IOException {
        Properties props = new Properties();
        props.setProperty("messageSequenceCounter", Long.toString(messageSequenceCounter.get()));
        props.setProperty("conversationIdCounter", Long.toString(conversationIdCounter.get()));
        try (OutputStream out = new FileOutputStream(serverConfigFile)) {
            props.store(out, "Server counters (message sequence and conversation id)");
        }
    }

    private Set<String> newConcurrentStringSet() {
        return ConcurrentHashMap.newKeySet();
    }

    private Set<Long> newConcurrentLongSet() {
        return ConcurrentHashMap.newKeySet();
    }

    private boolean isValidUser(String userID, String userName) {
        return Objects.equals(authorizedUsers.get(userID), userName);
    }

    /*
     * On-disk layout (root may be e.g. "data" or "test_data"; children are identical):
     *   <dataRoot>/
     *     server_data/
     *       server_config.txt
     *       authorized_ids/
     *         authorized_admins.txt
     *         authorized_users.txt
     *     conversation_data/*.conversation
     *     user_data/*.user
     */
    private void loadData() {
        if (!dataRoot.exists()) System.err.println("WARNING: data directory not found");
        if (!serverDataDir.exists()) System.err.println("WARNING: server_data directory not found");
        if (!authorizedIdsDir.exists()) System.err.println("WARNING: authorized_ids directory not found");
        if (!authorizedAdminsFile.exists()) System.err.println("WARNING: authorized_admins.txt not found");
        if (!authorizedUsersFile.exists()) System.err.println("WARNING: authorized_users.txt not found");

        try {
            if (!serverConfigFile.exists()) serverConfigFile.createNewFile();
            if (!conversationDataDir.exists()) conversationDataDir.mkdirs();
            if (!userDataDir.exists()) userDataDir.mkdirs();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (authorizedUsersFile.isFile()) {
                loadAuthorizedUsersCsv(authorizedUsersFile);
            }
            if (authorizedAdminsFile.isFile()) {
                loadAuthorizedAdminsCsv(authorizedAdminsFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        File[] listOfConversationFiles = conversationDataDir.listFiles(
            f -> f.isFile() && f.getName().endsWith(".conversation"));
        if (listOfConversationFiles == null) {
            listOfConversationFiles = new File[0];
        }
        ObjectInputStream in = null;
        try {
	        for (File f : listOfConversationFiles) {
	        	FileInputStream fs = new FileInputStream(f);
	        	in = new ObjectInputStream(fs);
	        	Conversation newConversation = (Conversation) in.readObject();
	        	conversationsByConversationID.put(newConversation.getConversationId(),newConversation);
	        	linkParticipantsToConversation(newConversation.getConversationId(), newConversation.getParticipants());
	        }
        }catch(IOException e) {
        	e.printStackTrace();
        }catch(ClassNotFoundException e) {
        	e.printStackTrace();
        }

        File[] listOfUserFiles = userDataDir.listFiles(
            f -> f.isFile() && f.getName().endsWith(".user"));
        if (listOfUserFiles == null) {
            listOfUserFiles = new File[0];
        }
        try {
            for (File f : listOfUserFiles) {
                User u = User.fromFile(f);
                usersByUserID.put(u.getUserId(), u);
                usersByLoginName.put(u.getLoginName(), u);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        buildDirectoryAtStartup();

        try {
            loadServerCounters();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * CSV: one row per line, two columns {@code userId,name} (external supply).
     * Blank lines and lines starting with {@code #} are ignored.
     */
    private void loadAuthorizedUsersCsv(File file) throws IOException {
        authorizedUsers.clear();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(",", 2);
                if (parts.length < 2) {
                    System.err.println("WARNING: skipping authorized_users line (expected userId,name): " + line);
                    continue;
                }
                String userId = parts[0].trim();
                String name = parts[1].trim();
                if (!name.isEmpty() && !userId.isEmpty()) {
                    authorizedUsers.put(userId, name);
                }
            }
        }
    }

    /**
     * Text file: one admin user id per line.
     * Blank lines and lines starting with {@code #} are ignored.
     */
    private void loadAuthorizedAdminsCsv(File file) throws IOException {
        authorizedAdminIds.clear();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (!line.isEmpty()) {
                    if (!authorizedAdminIds.contains(line)) {
                        authorizedAdminIds.add(line);
                    }
                }
            }
        }
    }

    /**
     * Build the initial directory cache from users loaded at startup.
     * Runtime updates are append-only via {@link #addUserToDirectory(User)}.
     */
    private void buildDirectoryAtStartup() {
        ArrayList<UserInfo> snapshot = new ArrayList<>();
        for (User user : usersByUserID.values()) {
            if (user != null) {
                snapshot.add(user.toUserInfo());
            }
        }
        snapshot.sort(Comparator.comparing(UserInfo::getName));
        directoryUserInfos.clear();
        directoryUserInfos.addAll(snapshot);
    }

    private void addUserToDirectory(User user) {
        if (user == null) {
            return;
        }
        UserInfo newUserInfo = user.toUserInfo();
        Comparator<UserInfo> byName = (left, right) ->
                String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName());
        int insertIndex = 0;
        while (insertIndex < directoryUserInfos.size()
                && byName.compare(directoryUserInfos.get(insertIndex), newUserInfo) <= 0) {
            insertIndex++;
        }
        directoryUserInfos.add(insertIndex, newUserInfo);
    }

    private ArrayList<UserInfo> getDirectorySnapshot() {
        return new ArrayList<>(directoryUserInfos);
    }

    private void updateConversation(Message message) {
        Conversation conversation = conversationsByConversationID.get(message.getConversationId());
        if (conversation == null) {
            throw new IllegalArgumentException("Unknown conversation: " + message.getConversationId());
        }
        synchronized (conversation) {
            conversation.append(message);
            persistConversation(conversation);
        }
    }

    /**
     * Bidirectional index update: {@code userId} is a member of {@code conversationId} in both
     * {@link #conversationIDsByUserID} and {@link #userIDsByConversationID}.
     */
    private void linkUserToConversation(String userId, long conversationId) {
        if (userId == null) {
            return;
        }
        conversationIDsByUserID
            .computeIfAbsent(userId, ignored -> newConcurrentLongSet())
            .add(conversationId);
        userIDsByConversationID
            .computeIfAbsent(conversationId, ignored -> newConcurrentStringSet())
            .add(userId);
    }

    /** Applies {@link #linkUserToConversation(String, long)} for each participant in {@code participants}. */
    private void linkParticipantsToConversation(long conversationId, Iterable<UserInfo> participants) {
        for (UserInfo userInfo : participants) {
            if (userInfo == null || userInfo.getUserId() == null) {
                continue;
            }
            linkUserToConversation(userInfo.getUserId(), conversationId);
        }
    }

    /**
     * Inverse of {@link #linkUserToConversation(String, long)}: remove {@code userId} from both indices
     * and prune empty sets.
     */
    private void unlinkUserFromConversation(String userId, long conversationId) {
        Conversation conversation = conversationsByConversationID.get(conversationId);
        if (conversation != null) {
            synchronized (conversation) {
                conversation.removeParticipant(userId);
                persistConversation(conversation);
            }
        }

        Set<Long> convIdsForUser = conversationIDsByUserID.get(userId);
        if (convIdsForUser != null) {
            convIdsForUser.remove(conversationId);
            if (convIdsForUser.isEmpty()) {
                conversationIDsByUserID.remove(userId);
            }
        }

        Set<String> userIdsForConv = userIDsByConversationID.get(conversationId);
        if (userIdsForConv != null) {
            userIdsForConv.remove(userId);
            if (userIdsForConv.isEmpty()) {
                userIDsByConversationID.remove(conversationId);
            }
        }

        User user = usersByUserID.get(userId);
        if (user != null) {
            user.removeConversationFromLastReadMap(conversationId);
            persistUser(user);
        }
    }

    /**
     * Discord-style "close DM" reactivation. If {@code convId} is a {@link ConversationType#PRIVATE}
     * conversation whose only active participant is {@code senderId} and whose
     * {@linkplain Conversation#getHistoricalParticipants() historical participants} contain a user
     * not in the active roster, re-add that user to the active roster and re-link them in the
     * server indices. Returns the reactivated user's id, or {@code null} if there is nothing to do.
     * <p>
     * Synchronizes on the {@link Conversation} so this is atomic against concurrent leave/send.
     */
    public String maybeReactivatePrivatePeerOnSend(long convId, String senderId) {
        if (senderId == null) {
            return null;
        }
        Conversation conversation = conversationsByConversationID.get(convId);
        if (conversation == null || conversation.getType() != ConversationType.PRIVATE) {
            return null;
        }
        synchronized (conversation) {
            ArrayList<UserInfo> active = conversation.getParticipants();
            if (active.size() != 1) {
                return null;
            }
            if (!senderId.equals(active.get(0).getUserId())) {
                return null;
            }
            UserInfo peer = null;
            for (UserInfo u : conversation.getHistoricalParticipants()) {
                if (u == null || u.getUserId() == null) {
                    continue;
                }
                if (senderId.equals(u.getUserId())) {
                    continue;
                }
                peer = u;
                break;
            }
            if (peer == null) {
                return null;
            }
            ArrayList<UserInfo> toAdd = new ArrayList<>();
            toAdd.add(peer);
            conversation.addParticipants(toAdd);
            linkUserToConversation(peer.getUserId(), convId);
            persistConversation(conversation);
            return peer.getUserId();
        }
    }

    private static boolean containsParticipant(ArrayList<UserInfo> participants, String userId) {
        for (UserInfo p : participants) {
            if (p != null && p.getUserId() != null && userId.equals(p.getUserId())) {
                return true;
            }
        }
        return false;
    }

    public Response handleRegister(Request request) {
        RegisterCredentials registerCredentials = (RegisterCredentials) request.getPayload();
        String userId = registerCredentials.getUserId();
        String loginName = registerCredentials.getLoginName();
        String password = registerCredentials.getPassword();
        String name = registerCredentials.getName();
        if(!isValidUser(userId, name)) {
            return new Response(ResponseType.REGISTER_RESULT, new RegisterResult(shared.enums.RegisterStatus.USER_ID_INVALID));
        }
        if(usersByUserID.containsKey(userId)) {
            return new Response(ResponseType.REGISTER_RESULT, new RegisterResult(shared.enums.RegisterStatus.USER_ID_TAKEN));
        }
        // loginName forms a filesystem path; restrict to safe charset
        if(loginName == null || loginName.isBlank() || !loginName.matches("[a-zA-Z0-9_\\-]+")) {
            return new Response(ResponseType.REGISTER_RESULT, new RegisterResult(shared.enums.RegisterStatus.LOGIN_NAME_INVALID));
        }
        if(usersByLoginName.containsKey(loginName)) {
            return new Response(ResponseType.REGISTER_RESULT, new RegisterResult(shared.enums.RegisterStatus.LOGIN_NAME_TAKEN));
        }
        UserType registrationType = authorizedAdminIds.contains(userId) ? UserType.ADMIN : UserType.USER;
        User user = new User(userId, name, loginName, password, registrationType);
        usersByUserID.put(userId, user);
        usersByLoginName.put(loginName, user);
        addUserToDirectory(user);
        persistUser(user);
        return new Response(ResponseType.REGISTER_RESULT, new RegisterResult(shared.enums.RegisterStatus.SUCCESS, user.toUserInfo()));
    }

    public Response handleLogin(Request request) {
        LoginCredentials loginCredentials = (LoginCredentials) request.getPayload();
        String loginName = loginCredentials.getLoginName();
        String password = loginCredentials.getPassword();

        if(!usersByLoginName.containsKey(loginName)) {
            return new Response(ResponseType.LOGIN_RESULT, new LoginResult(shared.enums.LoginStatus.NO_ACCOUNT_EXISTS));
        }

        if(!usersByLoginName.get(loginName).getPassword().equals(password)) {
            return new Response(ResponseType.LOGIN_RESULT, new LoginResult(shared.enums.LoginStatus.INVALID_CREDENTIALS));
        }

        User user = usersByLoginName.get(loginName);
        String userID = user.getUserId();
        Set<Long> conversationIDs = conversationIDsByUserID.get(userID);
        if (conversationIDs == null) {
            conversationIDs = Collections.emptySet();
        }
        ArrayList<Conversation> conversationList = new ArrayList<>();
        for (Long conversationId : conversationIDs) {
            Conversation c = conversationsByConversationID.get(conversationId);
            if (c != null) {
                conversationList.add(c);
            }
        }
        // #214: sort most-recent-first so the wire payload is deterministic.
        // Tie-break by conversationId desc so empty conversations keep stable order.
        conversationList.sort((a, b) -> {
            int cmp = Long.compare(latestSequenceNumberOrId(b), latestSequenceNumberOrId(a));
            if (cmp != 0) return cmp;
            return Long.compare(b.getConversationId(), a.getConversationId());
        });
        return new Response(ResponseType.LOGIN_RESULT, new LoginResult(
            shared.enums.LoginStatus.SUCCESS,
            user.toUserInfo(),
            conversationList,
            getDirectorySnapshot()));
    }

    private static long latestSequenceNumberOrId(Conversation c) {
        if (c == null) return 0L;
        ArrayList<Message> msgs = c.getMessages();
        if (msgs == null || msgs.isEmpty()) return c.getConversationId();
        return msgs.get(msgs.size() - 1).getSequenceNumber();
    }

    public Response handleSendMessage(Request request) {
        RawMessage rawMessage = (RawMessage) request.getPayload();
        String text = rawMessage.getText();
        long conversationId = rawMessage.getTargetConversationId();
        if (!conversationsByConversationID.containsKey(conversationId)) {
            return null;
        }
        long sequenceNumber = nextMessageSequenceNumber();
        Date timestamp = new Date();
        Message message = new Message(text, sequenceNumber, timestamp, request.getSenderId(), conversationId);
        updateConversation(message);
        return new Response(shared.enums.ResponseType.MESSAGE, message);

    }

    /**
     * Stores read cursors on the {@link User} so the next {@link User#toUserInfo()} can restore unread
     * markers on the next login. No concurrent consistency guarantees; the client may update UI
     * before or without relying on this response.
     */
    public Response handleUpdateReadMessages(Request request) {
        UpdateReadMessages updateReadMessages = (UpdateReadMessages) request.getPayload();
        long conversationID = updateReadMessages.getConversationID();
        long lastSeenSequenceNumber = updateReadMessages.getLastSeenSequenceNumber();
        User user = usersByUserID.get(request.getSenderId());
        user.setLastRead(conversationID, lastSeenSequenceNumber);
        persistUser(user);
        UserInfo updated = user.toUserInfo();
        return new Response(ResponseType.READ_MESSAGES_UPDATED, new ReadMessagesUpdated(updated));
    }

    public Response handleCreateConversation(Request request) {
        CreateConversationPayload createConversationPayload = (CreateConversationPayload) request.getPayload();
        ArrayList<UserInfo> participants = new ArrayList<>(createConversationPayload.getParticipants());
        // Caller must supply at least one participant; empty creates are rejected until error payloads exist.
        if (participants == null || participants.isEmpty()) {
            return null;
        }
        synchronized (createConversationLock) {
            // For PRIVATE (2-participant) creates, return the existing conversation if one already
            // exists with the same active roster. Group dedupe is intentionally out of scope.
            if (participants.size() == 2) {
                String aId = participants.get(0) != null ? participants.get(0).getUserId() : null;
                String bId = participants.get(1) != null ? participants.get(1).getUserId() : null;
                if (aId != null && bId != null) {
                    Set<Long> aConvIds = conversationIDsByUserID.getOrDefault(aId, Collections.emptySet());
                    for (Long cid : aConvIds) {
                        Conversation existing = conversationsByConversationID.get(cid);
                        if (existing == null || existing.getType() != ConversationType.PRIVATE) {
                            continue;
                        }
                        ArrayList<UserInfo> active = existing.getParticipants();
                        if (containsParticipant(active, aId) && containsParticipant(active, bId)) {
                            return new Response(ResponseType.CONVERSATION, existing);
                        }
                    }
                }
            }
            long conversationId = nextConversationId();
            // Derives PRIVATE vs GROUP from participant count; seeds historicalParticipants from this list.
            Conversation conversation = new Conversation(conversationId, participants);
            conversationsByConversationID.put(conversationId, conversation);
            linkParticipantsToConversation(conversationId, conversation.getParticipants());
            persistConversation(conversation);
            return new Response(ResponseType.CONVERSATION, conversation);
        }
    }

    public Response handleAddToConversation(Request request) {
        AddToConversationPayload payload = (AddToConversationPayload) request.getPayload();
        ArrayList<UserInfo> incoming = payload.getParticipants();
        long targetConversationId = payload.getTargetConversationId();
        if (incoming == null || incoming.isEmpty()) {
            return null;
        }
        Conversation existing = conversationsByConversationID.get(targetConversationId);
        if (existing == null) {
            return null;
        }
        ArrayList<UserInfo> netNew = new ArrayList<>();
        for (UserInfo u : incoming) {
            if (u == null || u.getUserId() == null) {
                continue;
            }
            if (!containsParticipant(existing.getParticipants(), u.getUserId())) {
                netNew.add(u);
            }
        }
        if (netNew.isEmpty()) {
            return null;
        }

        // GROUP: never fork; append members and keep the same conversation id.
        if (existing.getType() == ConversationType.GROUP) {
            existing.addParticipants(netNew);
            linkParticipantsToConversation(targetConversationId, netNew);
            persistConversation(existing);
            return new Response(ResponseType.CONVERSATION, existing);
        }

        // PRIVATE: always fork on add (new roster, blank history); original private thread unchanged.
        if (existing.getType() == ConversationType.PRIVATE) {
            ArrayList<UserInfo> merged = new ArrayList<>(existing.getParticipants());
            merged.addAll(netNew);
            long forkId = nextConversationId();
            Conversation forked = new Conversation(forkId, merged);
            conversationsByConversationID.put(forkId, forked);
            linkParticipantsToConversation(forkId, forked.getParticipants());
            persistConversation(forked);
            return new Response(ResponseType.CONVERSATION, forked);
        }

        return null;
    }

    public Response handleLeaveConversation(Request request) {
        LeaveConversationPayload leaveConversationPayload = (LeaveConversationPayload) request.getPayload();
        long conversationId = leaveConversationPayload.getTargetConversationId();
        String userId = request.getSenderId();
        unlinkUserFromConversation(userId, conversationId);
        return new Response(ResponseType.LEAVE_RESULT, new LeaveResult(conversationId));
    }

    public Response handleAdminConversationQuery(Request request) {
        AdminConversationQuery adminConversationQuery = (AdminConversationQuery) request.getPayload();
        String userId = adminConversationQuery.getUserId();
        // Scan historicalParticipants instead of the active conversationIDsByUserID index
        // so orphaned conversations (everyone has left) and conversations the user has
        // since left still surface for the admin viewer.
        ArrayList<ConversationMetadata> metas = new ArrayList<>();
        ArrayList<Conversation> matched = new ArrayList<>();
        for (Conversation c : conversationsByConversationID.values()) {
            for (UserInfo u : c.getHistoricalParticipants()) {
                if (userId.equals(u.getUserId())) {
                    matched.add(c);
                    break;
                }
            }
        }
        matched.sort(Comparator.comparingLong(Conversation::getConversationId));
        for (Conversation c : matched) {
            metas.add(c.toMetadata());
        }
        return new Response(ResponseType.ADMIN_CONVERSATION_RESULT, new AdminConversationResult(metas));
    }

    public Response handleJoinConversation(Request request) {
        JoinConversationPayload joinConversationPayload = (JoinConversationPayload) request.getPayload();
        long conversationId = joinConversationPayload.getTargetConversationId();
        String userId = request.getSenderId();
        linkUserToConversation(userId, conversationId);
        return new Response(ResponseType.CONVERSATION, conversationsByConversationID.get(conversationId));
    }

    /**
     * Returns the full {@link Conversation} (with messages) to an admin caller for read-only
     * viewing. Performs an admin gate against {@link UserType#ADMIN} on the sender; non-admins
     * receive a null payload. Does NOT call {@code linkUserToConversation} or otherwise mutate
     * participant indices, so other users get no signal that the lookup happened.
     */
    public Response handleAdminViewConversation(Request request) {
        User caller = usersByUserID.get(request.getSenderId());
        if (caller == null || caller.getUserType() != UserType.ADMIN) {
            return new Response(ResponseType.ADMIN_VIEW_CONVERSATION_RESULT, null);
        }
        AdminViewConversationQuery query = (AdminViewConversationQuery) request.getPayload();
        Conversation conv = conversationsByConversationID.get(query.getConversationId());
        return new Response(ResponseType.ADMIN_VIEW_CONVERSATION_RESULT, conv);
    }

    /** Returns the {@link Conversation} for the given id, or {@code null} if not found. */
    public Conversation getConversation(long conversationId) {
        return conversationsByConversationID.get(conversationId);
    }

    public ArrayList<UserInfo> getParticipantList(long conversationId) {
        Conversation c = conversationsByConversationID.get(conversationId);
        if (c == null) {
            return new ArrayList<>();
        }
        return c.getParticipants();
    }

    public boolean userExists(String loginName) {
        return loginName != null && usersByLoginName.containsKey(loginName);
    }

    public String getUserIdByLoginName(String loginName) {
        User user = usersByLoginName.get(loginName);
        return user == null ? null : user.getUserId();
    }
}
