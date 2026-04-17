package server;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import shared.enums.ResponseType;
import shared.networking.Request;
import shared.networking.Response;
import shared.payload.*;


/**
 * Server-side state and request handling for persisted data.
 * <p>
 * <b>Session invariant:</b> Every entry point except {@link #handleRegister} and {@link #handleLogin}
 * is invoked only after a successful sign-in (enforced by the controller). Callers are authenticated
 * users; these methods do not repeat login or session validation.
 */
public class DataManager {
    /** Serialized conversations: {@code conversation_data/<id>.conversation}. */
    private static final String CONVERSATION_FILE_SUFFIX = ".conversation";
    /** Serialized users: {@code user_data/<userId>.user}. */
    private static final String USER_FILE_SUFFIX = ".user";

    /**
     * Authoritative monotonic message sequence counter. Rehydrate from
     * {@code server_data/server_config.txt} on load and persist on shutdown (TODO).
     */
    private static final AtomicLong messageSequenceCounter = new AtomicLong(0);

    private ConcurrentMap<String, User> usersByUserID;
    private ConcurrentMap<String, User> usersByLoginName;
    private ConcurrentMap<String, Set<String>> conversationIDsByUserID;
    private ConcurrentMap<String, Set<String>> userIDsByConversationID;
    private ConcurrentMap<String, Conversation> conversationsByConversationID;
    private ConcurrentMap<String, String> authorizedUsers;
    private CopyOnWriteArrayList<String> authorizedAdminIds;
    private String dataFilePath;

    public DataManager(String dataFilePath) {
    	// FIXME: remove filepath hardcoding
        this.dataFilePath = "data";
        this.usersByUserID = new ConcurrentHashMap<>();
        this.usersByLoginName = new ConcurrentHashMap<>();
        this.conversationIDsByUserID = new ConcurrentHashMap<>();
        this.userIDsByConversationID = new ConcurrentHashMap<>();
        this.conversationsByConversationID = new ConcurrentHashMap<>();
        this.authorizedUsers = new ConcurrentHashMap<>();
        this.authorizedAdminIds = new CopyOnWriteArrayList<>();

        loadData();
    }

    private Set<String> newConcurrentStringSet() {
        return ConcurrentHashMap.newKeySet();
    }

    private boolean isValidUser(String userID, String userName) {
        return Objects.equals(authorizedUsers.get(userID), userName);
    }

    public void close() {
        try {
            for (User u : usersByUserID.values()) {
                persistUser(u);
            }
            for (Conversation c : conversationsByConversationID.values()) {
                synchronized (c) {
                    writeConversationToDisk(c);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // TODO: persist messageSequenceCounter to dataFilePath/server_data/server_config.txt
    }

    /**
     * Returns the next message sequence number. Thread-safe.
     */
    static long nextMessageSequenceNumber() {
        return messageSequenceCounter.incrementAndGet();
    }

    private File conversationDataDirectory() {
        return new File(dataFilePath, "conversation_data");
    }

    private File userDataDirectory() {
        return new File(dataFilePath, "user_data");
    }

    private File userPersistenceFile(String userId) {
        return new File(userDataDirectory(), userId + USER_FILE_SUFFIX);
    }

    private void persistUser(User user) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(userPersistenceFile(user.getUserId()));
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(user);
        }
    }

    private void writeConversationToDisk(Conversation conversation) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(conversationPersistenceFile(conversation.getConversationId()));
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(conversation);
        }
    }

    /**
     * Persistence file for a conversation: Java-serialized {@link Conversation} at
     * {@code conversation_data/<conversationId>.conversation}.
     */
    private File conversationPersistenceFile(String conversationId) {
        return new File(conversationDataDirectory(), conversationId + CONVERSATION_FILE_SUFFIX);
    }

    /**
     * Appends {@code message} to its conversation in memory and rewrites that conversation's file on disk.
     */
    private void updateConversation(Message message) throws IOException {
        Conversation conversation = conversationsByConversationID.get(message.getConversationId());
        if (conversation == null) {
            throw new IllegalArgumentException("Unknown conversation: " + message.getConversationId());
        }
        synchronized (conversation) {
            conversation.append(message);
            writeConversationToDisk(conversation);
        }
    }

    public Response handleRegister(Request request) {
        RegisterCredentials registerCredentials = (RegisterCredentials) request.getPayload();
        String userId = registerCredentials.getUserId();
        String loginName = registerCredentials.getLoginName();
        String password = registerCredentials.getPassword();
        String name = registerCredentials.getName();
        // check if the user is authorized
        if(!isValidUser(userId, name)) {
            return new Response(ResponseType.REGISTER_RESULT, new RegisterResult(shared.enums.RegisterStatus.USER_ID_INVALID));
        }
        // check if the user ID is already taken
        if(usersByUserID.containsKey(userId)) {
            return new Response(ResponseType.REGISTER_RESULT, new RegisterResult(shared.enums.RegisterStatus.USER_ID_TAKEN));
        }
        // check if the login name is already taken
        if(usersByLoginName.containsKey(loginName)) {
            return new Response(ResponseType.REGISTER_RESULT, new RegisterResult(shared.enums.RegisterStatus.LOGIN_NAME_TAKEN));
        }
        // create the user
        User user = new User(userId, name, loginName, password);
        usersByUserID.put(userId, user);
        usersByLoginName.put(loginName, user);
        try {
            persistUser(user);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Response(ResponseType.REGISTER_RESULT, new RegisterResult(shared.enums.RegisterStatus.SUCCESS));
    }

    public Response handleLogin(Request request) {
        LoginCredentials loginCredentials = (LoginCredentials) request.getPayload();
        String loginName = loginCredentials.getLoginName();
        String password = loginCredentials.getPassword();

        // check if the user exists
        if(!usersByLoginName.containsKey(loginName)) {
            return new Response(ResponseType.LOGIN_RESULT, new LoginResult(shared.enums.LoginStatus.INVALID_CREDENTIALS));
        }

        // check if the password is correct
        if(!usersByLoginName.get(loginName).getPassword().equals(password)) {
            return new Response(ResponseType.LOGIN_RESULT, new LoginResult(shared.enums.LoginStatus.INVALID_CREDENTIALS));
        }

        // return the user's conversation list if the login is successful
        User user = usersByLoginName.get(loginName);
        String userID = user.getUserId();
        Set<String> conversationIDs = conversationIDsByUserID.get(userID);
        if (conversationIDs == null) {
            conversationIDs = Collections.emptySet();
        }
        ArrayList<Conversation> conversationList = new ArrayList<>();
        for (String conversationID : conversationIDs) {
            conversationList.add(conversationsByConversationID.get(conversationID));
        }
        return new Response(ResponseType.LOGIN_RESULT, new LoginResult(
            shared.enums.LoginStatus.SUCCESS,
            user.getUserInfo(),
            conversationList));
    }

    public Response handleLogout(Request request) {
        return new Response(ResponseType.LOGOUT_RESULT, null);
    }

    public Response handleSendMessage(Request request) {
        // this method needs to do 3 things:
        // 1. assign a sequence number and timestamp to the message
        // 2. append this message to the appropriate conversation
        // 3. return the message to the controller for subsequent distribution
        RawMessage rawMessage = (RawMessage) request.getPayload();
        String text = rawMessage.getText();
        String conversationId = rawMessage.getTargetConversationId();
        long sequenceNumber = nextMessageSequenceNumber();
        Date timestamp = new Date();
        Message message = new Message(text, sequenceNumber, timestamp, request.getSenderId(), conversationId);
        try {
            updateConversation(message);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
        return new Response(shared.enums.ResponseType.MESSAGE, message);

    }

    /**
     * Stores read cursors on the {@link User} so {@link User#getUserInfo()} can restore unread
     * markers on the next login. No concurrent consistency guarantees; the client may update UI
     * before or without relying on this response.
     */
    public Response handleUpdateReadMessages(Request request) {
        UpdateReadMessages updateReadMessages = (UpdateReadMessages) request.getPayload();
        String conversationID = updateReadMessages.getConversationID();
        long lastSeenSequenceNumber = updateReadMessages.getLastSeenSequenceNumber();
        User user = usersByUserID.get(request.getSenderId());
        user.setLastRead(conversationID, lastSeenSequenceNumber);
        try {
            persistUser(user);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Response(ResponseType.READ_UPDATED, new ReadMessagesUpdated());
    }

    public Response handleSearchDirectory(Request request) {
        // TODO
        return null;
    }

    public Response handleCreateConversation(Request request) {
        // TODO
        return null;
    }

    public Response handleAddToConversation(Request request) {
        // TODO
        return null;
    }

    public Response handleLeaveConversation(Request request) {
        // TODO
        return null;
    }

    public Response handleAdminConversationQuery(Request request) {
        // TODO
        return null;
    }

    public Response handleJoinConversation(Request request) {
        return null;
    }

    public ArrayList<UserInfo> getParticipantList(String c_id) {
        Conversation c = conversationsByConversationID.get(c_id);   //get the conversation by ID
        if (c == null) {
            return new ArrayList<>();
        }
        return c.getParticipants();
    }
    
    /*
     * format: 
     *	/data
     * 		/server_data
     * 			/server_config.txt
     * 			/authorized_ids
     * 				/authorized_admins.txt
     * 				/authorized_users.txt
     * 		/conversation_data/*.conversation
     * 		/user_data/*.user
     */
    private void loadData() {
        // these are assumed to exist, warn if not
        File dataDir = new File(dataFilePath);
        File serverData = new File(dataFilePath + File.separator + "server_data");
        File authorizedIds = new File(serverData + File.separator + "authorized_ids");
        File authorizedAdminsFile = new File(authorizedIds + File.separator + "authorized_admins.txt");
        File authorizedUsersFile = new File(authorizedIds + File.separator + "authorized_users.txt");


        if (!dataDir.exists()) System.err.println("WARNING: data directory not found");
        if (!serverData.exists()) System.err.println("WARNING: server_data directory not found");
        if (!authorizedIds.exists()) System.err.println("WARNING: authorized_ids directory not found");
        if (!authorizedAdminsFile.exists()) System.err.println("WARNING: authorized_admins.txt not found");
        if (!authorizedUsersFile.exists()) System.err.println("WARNING: authorized_users.txt not found");

        // these are not assumed to exist, create if missing
        File serverConfig = new File(serverData, "server_config.txt");
        File conversationDataDir = conversationDataDirectory();
        File userDataDir = userDataDirectory();

        try {
            if (!serverConfig.exists()) serverConfig.createNewFile();
            if (!conversationDataDir.exists()) conversationDataDir.mkdirs();
            if (!userDataDir.exists()) userDataDir.mkdirs();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // rehydrate authorized users and admins
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
        
        // rehydrate Conversations (*.conversation — serialized Conversation per file)
        File[] listOfConversationFiles = conversationDataDir.listFiles(
            f -> f.isFile() && f.getName().endsWith(CONVERSATION_FILE_SUFFIX));
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
	        	ArrayList<UserInfo> participantList = newConversation.getParticipants();
	        	Set<String> participantIds = userIDsByConversationID.computeIfAbsent(
	        	    newConversation.getConversationId(),
	        	    ignored -> newConcurrentStringSet()
	        	);
	        	// Rebuild both sides of user/conversation relationships.
                // for each user in the conversation, add the conversation ID to the user's conversation IDs
	        	for(UserInfo userInfo:participantList) {
	        		String userId = userInfo.getUserId();
	        		conversationIDsByUserID.computeIfAbsent(userId, ignored -> newConcurrentStringSet())
	        		    .add(newConversation.getConversationId());
	        		participantIds.add(userId);
	        	}
	        }
        }catch(IOException e) {
        	e.printStackTrace();
        }catch(ClassNotFoundException e) {
        	e.printStackTrace();
        }
        
        // rehydrate Users (*.user — serialized User per file)
        File[] listOfUserFiles = userDataDir.listFiles(
            f -> f.isFile() && f.getName().endsWith(USER_FILE_SUFFIX));
        if (listOfUserFiles == null) {
            listOfUserFiles = new File[0];
        }
        try {
            for (File f : listOfUserFiles) {
                FileInputStream fs = new FileInputStream(f);
                in = new ObjectInputStream(fs);
                User u = (User) in.readObject();
                usersByUserID.put(u.getUserId(), u);
                usersByLoginName.put(u.getLoginName(), u);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        // TODO: rehydrate messageSequenceCounter from serverConfig (source of truth on disk)
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
                    authorizedAdminIds.addIfAbsent(line);
                }
            }
        }
    }
}

