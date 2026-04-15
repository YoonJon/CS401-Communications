package server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import shared.payload.AddToConversationPayload;
import shared.payload.AdminConversationQuery;
import shared.payload.AdminConversationResult;
import shared.payload.Conversation;
import shared.payload.CreateConversationPayload;
import shared.payload.DirectoryQuery;
import shared.payload.DirectoryResult;
import shared.payload.JoinConversationPayload;
import shared.payload.LeaveConversationPayload;
import shared.payload.LeaveResult;
import shared.payload.LoginCredentials;
import shared.payload.LoginResult;
import shared.payload.Message;
import shared.payload.RawMessage;
import shared.payload.RegisterCredentials;
import shared.payload.RegisterResult;
import shared.payload.UpdateReadMessages;
import shared.payload.UserInfo;

public class DataManager {
    private Map<String, User> usersByUserID;
    private Map<String, User> usersByLoginName;
    private Map<String, Set<String>> conversationIDsByUserID;
    private Map<String, Set<String>> userIDsByConversationID;
    private Map<String, Conversation> conversationsByConversationID;
    private Map<String, String> authorizedUsers;
    private List<String> authorizedAdminIds;
    private String dataFilePath;

    public DataManager(String dataFilePath) {
    	// FIXME: remove filepath hardcoding
        this.dataFilePath = "data";
        this.usersByUserID = new HashMap<>();
        this.usersByLoginName = new HashMap<>();
        this.conversationIDsByUserID = new HashMap<>();
        this.userIDsByConversationID = new HashMap<>();
        this.conversationsByConversationID = new HashMap<>();
        this.authorizedUsers = new HashMap<>();
        this.authorizedAdminIds = new ArrayList<>();
        
        // TODO: load persisted data from dataFilePath
    }

    public void close() {
        // TODO: persist data to disk
    }

    public RegisterResult handleRegister(RegisterCredentials rc) {
        // TODO
        return null;
    }

    public LoginResult handleLogin(LoginCredentials lc) {
        // TODO
        return null;
    }

    public void handleLogout() {
        // TODO
    }

    public Message handleSendMessage(RawMessage rm) {
        // TODO
        return null;
    }

    public void handleUpdateReadMessages(UpdateReadMessages u) {
        // TODO
    }

    public DirectoryResult handleSearchDirectory(DirectoryQuery query) {
        // TODO
        return null;
    }

    public Conversation handleCreateConversation(CreateConversationPayload cc) {
        // TODO
        return null;
    }

    public Conversation handleAddToConversation(AddToConversationPayload atc) {
        // TODO
        return null;
    }

    public LeaveResult handleLeaveConversation(LeaveConversationPayload lc) {
        // TODO
        return null;
    }

    public AdminConversationResult handleAdminConversationQuery(AdminConversationQuery q) {
        // TODO
        return null;
    }

    public Conversation handleJoinConversation(JoinConversationPayload jc) {
        // TODO
        return null;
    }

    public ArrayList<UserInfo> getParticipantList(String c_id) {
        // TODO
        return null;
    }
    
    /*
     * format: 
     *	/data
     * 		/server_data
     * 			/server_config.txt
     * 			/authorized_ids
     * 				/authorized_admins.txt
     * 				/authorized_user.txt
     * 		/conversation_data
     * 		/user_data
     */
    private void loadData() {
        // these are assumed to exist, warn if not
        File dataDir = new File(dataFilePath);
        File serverData = new File(dataFilePath + File.separator + "server_data");
        File authorizedIds = new File(serverData + File.separator + "authorized_ids");
        File authorizedAdmins = new File(authorizedIds + File.separator + "authorized_admins.txt");
        File authorizedUsers = new File(authorizedIds + File.separator + "authorized_users.txt");


        if (!dataDir.exists()) System.err.println("WARNING: data directory not found");
        if (!serverData.exists()) System.err.println("WARNING: server_data directory not found");
        if (!authorizedIds.exists()) System.err.println("WARNING: authorized_ids directory not found");
        if (!authorizedAdmins.exists()) System.err.println("WARNING: authorized_admins.txt not found");
        if (!authorizedUsers.exists()) System.err.println("WARNING: authorized_users.txt not found");

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
        // TODO: use file handles to rehydrate data
        // TODO: mock data generation script
    }
}

