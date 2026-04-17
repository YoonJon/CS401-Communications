package server;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import shared.networking.Request;
import shared.payload.*;


public class DataManager {
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

    public void close() {
        // TODO: persist data to disk
    }

    public RegisterResult handleRegister(Request request) {
        // TODO
        return null;
    }

    public LoginResult handleLogin(Request request) {
        // TODO
        return null;
    }

    public void handleLogout(Request request) {
        // TODO
    }

    public Message handleSendMessage(Request request) {
        // TODO
        return null;
    }

    public void handleUpdateReadMessages(Request request) {
        // TODO
    }

    public DirectoryResult handleSearchDirectory(Request request) {
        // TODO
        return null;
    }

    public Conversation handleCreateConversation(Request request) {
        // TODO
        return null;
    }

    public Conversation handleAddToConversation(Request request) {
        // TODO
        return null;
    }

    public LeaveResult handleLeaveConversation(Request request) {
        // TODO
        return null;
    }

    public AdminConversationResult handleAdminConversationQuery(Request request) {
        // TODO
        return null;
    }

    public Conversation handleJoinConversation(Request request) {
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
     * 		/conversation_data
     * 		/user_data
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
        File serverConfig = new File(serverData + File.separator + "server_config.txt");
        File conversationData = new File(dataFilePath + File.separator + "conversation_data");
        File userData = new File(dataFilePath + File.separator + "user_data");

        try {
            if (!serverConfig.exists()) serverConfig.createNewFile();
            if (!conversationData.exists()) conversationData.mkdirs();
            if (!userData.exists()) userData.mkdirs();
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
        
        // rehydrate Conversations
        File[]listOfConversationFiles = conversationData.listFiles();
        if (listOfConversationFiles == null) {
            listOfConversationFiles = new File[0];
        }
        ObjectInputStream in = null;
        try {
	        for(File f:listOfConversationFiles) {
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
        
        // rehydrate Users
        File[]listOfUserFiles = userData.listFiles();
        if (listOfUserFiles == null) {
            listOfUserFiles = new File[0];
        }
        try {
	        for(File f:listOfUserFiles) {
	        	FileInputStream fs = new FileInputStream(f);
	        	in = new ObjectInputStream(fs);
	        	User u= (User) in.readObject();
	        	usersByUserID.put(u.getUserId(),u);
	        	usersByLoginName.put(u.getLoginName(), u);
	        }
        }catch(IOException e) {
        	e.printStackTrace();
        }catch(ClassNotFoundException e) {
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
                    authorizedAdminIds.addIfAbsent(line);
                }
            }
        }
    }
}

