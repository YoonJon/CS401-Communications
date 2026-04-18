package client;

import shared.enums.*;
import shared.networking.*;
import shared.payload.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

public class ClientController {
    private ClientUI gui;
    private ConnectionStatus connectionStatus;
    private Deque<Request> requestQueue;
    private boolean loggedIn;
    private UserInfo currentUser;
    private String hostIp;
    private int hostPort;
    private Socket socket;
    private ArrayList<Conversation> conversations;
    /** 0 means no conversation selected. */
    private long currentConversationId;
    private ArrayList<UserInfo> currentDirectory;
    private ArrayList<Conversation> currentConversationList;
    private ArrayList<Conversation> currentAdminConversationSearch;
    private Thread responseListenerThread;
    private Thread inactivityDetectorThread;

    public static void main(String[] args) {
    	ClientController ctr = new ClientController("localhost", 8080);
    }

    public ClientController(String hostIp, int hostPort) {
        this.hostIp = hostIp;
        this.hostPort = hostPort;
        this.connectionStatus = ConnectionStatus.NOT_CONNECTED;
        this.requestQueue = new LinkedBlockingDeque<>();
        this.loggedIn = false;
        this.conversations = new ArrayList<>();
        this.currentDirectory = new ArrayList<>();
        this.currentConversationList = new ArrayList<>();
        this.currentAdminConversationSearch = new ArrayList<>();
        this.gui = new ClientUI(this);
        gui.showMainView();
    }

    public void close() {
        // TODO: clean up socket and threads
    }

    private void processResponse(Response response) {
        // TODO: dispatch on response type, update model, notify GUI
    }

    private void ensureConnected() {
        // TODO: lazily establish TCP connection if not connected
    }

    public void register(String userId, String realName, String loginName, char[] password) {
        // TODO
    }

    public void login(String loginName, char[] password) {
        // TODO
    }

    public void logout() {
        // TODO
    }

    public void sendMessage(long conversationId, String m) {
        // TODO
    }

    public void searchDirectory(String query) {
        // TODO
    }

    public void searchConversationList(String query) {
        // TODO
    }

    public void adminConversationSearch(String query) {
        // TODO
    }

    public void createConversation(ArrayList<UserInfo> p) {
        // TODO
    }

    public void addToConversation(ArrayList<UserInfo> p, long conversationId) {
        // TODO
    }

    public void leaveConversation(long conversationId) {
        // TODO
    }

    public void adminGetUserConversations(String userID) {
        // TODO
    }

    public void joinConversation(long conversationId) {
        // TODO
    }

    public UserInfo getCurrentUserInfo() { return currentUser; }

    public ArrayList<UserInfo> getFilteredDirectory(String query) {
        // TODO: filter currentDirectory by query
        return null;
    }

    public ArrayList<Conversation> getFilteredConversationList(String query) {
        // TODO
        return null;
    }

    public ArrayList<Conversation> getFilteredAdminConversationSearch(String q) {
        // TODO
        return null;
    }

    public void setCurrentConversationId(long conversationId) { this.currentConversationId = conversationId; }
    public long getCurrentConversationId() { return currentConversationId; }

    public Conversation getCurrentConversation() {
        // TODO: look up currentConversationId in conversations
        return null;
    }

    private void sendRequest(Request r) {
        // TODO: write directly to socket output stream
    }

    private void enqueueRequest(Request r) {
        requestQueue.add(r);
    }

    // -------------------------------------------------------------------------
    class ResponseListener implements Runnable {
        public ResponseListener() {}

        @Override
        public void run() {
            // TODO: read Responses from socket ObjectInputStream, call processResponse
        }
    }

    // -------------------------------------------------------------------------
    class InactivityDetector implements Runnable {
        public InactivityDetector() {}

        @Override
        public void run() {
            // TODO: monitor lastActivityTimestamp, trigger logout on inactivity
        }
    }
}
