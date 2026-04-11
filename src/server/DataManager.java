package server;

import shared.payload.*;
import java.util.*;

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
        this.dataFilePath = dataFilePath;
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
}
