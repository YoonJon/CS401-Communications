package client;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import shared.enums.*;
import shared.networking.*;
import shared.networking.User.UserInfo;
import shared.payload.*;

public class ClientController {
    private ClientUI gui;
    private ConnectionStatus connectionStatus;
    private Deque<Request> requestQueue;
    private boolean loggedIn;
    private UserInfo currentUser;
    private String hostIp;
    private int hostPort;
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private ArrayList<Conversation> conversations;
    /** 0 means no conversation selected. */
    private long currentConversationId;
    private ArrayList<UserInfo> currentDirectory;
    private ArrayList<Conversation> currentConversationList;
    private ArrayList<Conversation> currentAdminConversationSearch;
    private Thread responseListenerThread;
    private Thread inactivityDetectorThread;
    private volatile long lastActivityTimestamp;

    public static void main(String[] args) {
        ClientController ctr = new ClientController("localhost", 8080);
    }

    public ClientController(String hostIp, int hostPort) {
        this(hostIp, hostPort, null);
        this.gui = new ClientUI(this);
        gui.showLoginView();
    }

    /** Package-private test seam: accepts a pre-built (or null) GUI; no Swing window opened. */
    ClientController(String hostIp, int hostPort, ClientUI guiOverride) {
        this.hostIp = hostIp;
        this.hostPort = hostPort;
        this.connectionStatus = ConnectionStatus.NOT_CONNECTED;
        this.requestQueue = new LinkedBlockingDeque<>();
        this.loggedIn = false;
        this.conversations = new ArrayList<>();
        this.currentDirectory = new ArrayList<>();
        this.currentConversationList = new ArrayList<>();
        this.currentAdminConversationSearch = new ArrayList<>();
        this.gui = guiOverride;
    }

    public void close() {
        connectionStatus = ConnectionStatus.NOT_CONNECTED;
        if (responseListenerThread != null) responseListenerThread.interrupt();
        if (inactivityDetectorThread != null) inactivityDetectorThread.interrupt();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            // ignore on close
        }
    }

    /** Package-private so tests can drive response handling directly without a live socket. */
    void processResponse(Response response) {
        if (response == null) return;
        switch (response.getType()) {

            case LOGIN_RESULT: {
                LoginResult lr = (LoginResult) response.getPayload();
                if (lr.getLoginStatus() == LoginStatus.SUCCESS) {
                    loggedIn = true;
                    currentUser = lr.getUserInfo();
                    conversations = lr.getConversationList() != null
                            ? lr.getConversationList() : new ArrayList<>();
                    currentConversationList = new ArrayList<>(conversations);
                    lastActivityTimestamp = System.currentTimeMillis();
                    if (gui != null) gui.showMainView();
                } else {
                    if (gui != null) gui.showLoginError(lr.getLoginStatus());
                }
                break;
            }

            case REGISTER_RESULT: {
                RegisterResult rr = (RegisterResult) response.getPayload();
                if (rr.getRegisterStatus() == RegisterStatus.SUCCESS) {
                    if (gui != null) gui.showLoginView();
                } else {
                    if (gui != null) gui.showRegisterError(rr.getRegisterStatus());
                }
                break;
            }

            case LOGOUT_RESULT: {
                loggedIn = false;
                currentUser = null;
                conversations.clear();
                currentConversationList.clear();
                currentConversationId = 0;
                if (gui != null) gui.showLoginView();
                break;
            }

            case MESSAGE: {
                // Server distributes a Message; append it to the matching local conversation.
                Message msg = (Message) response.getPayload();
                for (Conversation c : conversations) {
                    if (c.getConversationId() == msg.getConversationId()) {
                        c.append(msg);
                        break;
                    }
                }
                break;
            }

            case CONVERSATION: {
                // Returned after create / add-participant / join.
                // Update in-place if already known; otherwise add to the list.
                Conversation conv = (Conversation) response.getPayload();
                boolean found = false;
                for (int i = 0; i < conversations.size(); i++) {
                    if (conversations.get(i).getConversationId() == conv.getConversationId()) {
                        conversations.set(i, conv);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    conversations.add(conv);
                }
                currentConversationList = new ArrayList<>(conversations);
                break;
            }

            case LEAVE_RESULT: {
                LeaveResult lr = (LeaveResult) response.getPayload();
                long leftId = lr.getLeftConversationID();
                conversations.removeIf(c -> c.getConversationId() == leftId);
                currentConversationList = new ArrayList<>(conversations);
                if (currentConversationId == leftId) {
                    currentConversationId = 0;
                }
                break;
            }

            case ADMIN_CONVERSATION_RESULT: {
                // AdminConversationResult carries ConversationMetadata, not full Conversation objects.
                // Store the raw payload for the GUI to consume via getFilteredAdminConversationSearch.
                AdminConversationResult acr = (AdminConversationResult) response.getPayload();
                currentAdminConversationSearch = new ArrayList<>();
                for (ConversationMetadata m : acr.getConversations()) {
                    currentAdminConversationSearch.add(
                        new Conversation(m.getConversationId(), m.getParticipants()));
                }
                break;
            }

            case READ_UPDATED:
                // Server acknowledged the read-cursor update; no local state change needed.
                break;

            case PONG:
                lastActivityTimestamp = System.currentTimeMillis();
                break;

            case CONNECTED:
                connectionStatus = ConnectionStatus.CONNECTED;
                break;

            default:
                break;
        }
    }

    /** Lazily opens a TCP connection to the server and starts the reader/detector threads. */
    private synchronized void ensureConnected() {
        if (connectionStatus == ConnectionStatus.CONNECTED) return;
        connectionStatus = ConnectionStatus.CONNECTING;
        try {
            socket = new Socket(hostIp, hostPort);
            // OOS must be created and flushed BEFORE OIS on both sides to avoid header deadlock
            // (mirrors the convention in ConnectionHandler on the server).
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            inputStream = new ObjectInputStream(socket.getInputStream());
            connectionStatus = ConnectionStatus.CONNECTED;
            lastActivityTimestamp = System.currentTimeMillis();

            responseListenerThread = new Thread(new ResponseListener(), "client-resp");
            responseListenerThread.setDaemon(true);
            responseListenerThread.start();

            inactivityDetectorThread = new Thread(new InactivityDetector(), "client-inact");
            inactivityDetectorThread.setDaemon(true);
            inactivityDetectorThread.start();
        } catch (IOException e) {
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Public action methods — each maps 1-to-1 with a DataManager handler.
    // -------------------------------------------------------------------------

    /** Matches DataManager.handleRegister — payload: RegisterCredentials(userId, loginName, password, name). */
    public void register(String userId, String realName, String loginName, char[] password) {
        ensureConnected();
        RegisterCredentials creds = new RegisterCredentials(userId, loginName, new String(password), realName);
        sendRequest(new Request(RequestType.REGISTER, creds, null));
    }

    /** Matches DataManager.handleLogin — payload: LoginCredentials(loginName, password). */
    public void login(String loginName, char[] password) {
        ensureConnected();
        LoginCredentials creds = new LoginCredentials(loginName, new String(password));
        sendRequest(new Request(RequestType.LOGIN, creds, null));
    }

    /** Matches DataManager.handleLogout — no payload, senderId = current user id. */
    public void logout() {
        if (!loggedIn || currentUser == null) return;
        sendRequest(new Request(RequestType.LOGOUT, currentUser.getUserId()));
    }

    /** Matches DataManager.handleSendMessage — payload: RawMessage(text, conversationId). */
    public void sendMessage(long conversationId, String m) {
        if (!loggedIn || currentUser == null) return;
        sendRequest(new Request(RequestType.MESSAGE,
                new RawMessage(m, conversationId), currentUser.getUserId()));
    }

    /** Filters local directory and refreshes the directory list model in the GUI. */
    public void searchDirectory(String query) {
        if (gui == null) return;
        gui.getDirectoryViewModel().clear();
        for (UserInfo u : getFilteredDirectory(query)) {
            gui.getDirectoryViewModel().addElement(u);
        }
    }

    /** Filters local conversation list and refreshes the conversation list model in the GUI. */
    public void searchConversationList(String query) {
        if (gui == null) return;
        gui.getConversationViewModel().clear();
        for (Conversation c : getFilteredConversationList(query)) {
            gui.getConversationViewModel().addElement(c);
        }
    }

    /** Matches DataManager.handleAdminConversationQuery — payload: AdminConversationQuery(userId). */
    public void adminConversationSearch(String query) {
        if (!loggedIn || currentUser == null) return;
        sendRequest(new Request(RequestType.ADMIN_CONVERSATION_QUERY,
                new AdminConversationQuery(query), currentUser.getUserId()));
    }

    /** Matches DataManager.handleCreateConversation — payload: CreateConversationPayload(participants). */
    public void createConversation(ArrayList<UserInfo> p) {
        if (!loggedIn || currentUser == null) return;
        sendRequest(new Request(RequestType.CREATE_CONVERSATION,
                new CreateConversationPayload(p), currentUser.getUserId()));
    }

    /** Matches DataManager.handleAddToConversation — payload: AddToConversationPayload(participants, conversationId). */
    public void addToConversation(ArrayList<UserInfo> p, long conversationId) {
        if (!loggedIn || currentUser == null) return;
        sendRequest(new Request(RequestType.ADD_PARTICIPANT,
                new AddToConversationPayload(p, conversationId), currentUser.getUserId()));
    }

    /** Matches DataManager.handleLeaveConversation — payload: LeaveConversationPayload(conversationId). */
    public void leaveConversation(long conversationId) {
        if (!loggedIn || currentUser == null) return;
        sendRequest(new Request(RequestType.LEAVE_CONVERSATION,
                new LeaveConversationPayload(conversationId), currentUser.getUserId()));
    }

    /** Matches DataManager.handleAdminConversationQuery — payload: AdminConversationQuery(userID). */
    public void adminGetUserConversations(String userID) {
        if (!loggedIn || currentUser == null) return;
        sendRequest(new Request(RequestType.ADMIN_CONVERSATION_QUERY,
                new AdminConversationQuery(userID), currentUser.getUserId()));
    }

    /** Matches DataManager.handleJoinConversation — payload: JoinConversationPayload(conversationId). */
    public void joinConversation(long conversationId) {
        if (!loggedIn || currentUser == null) return;
        sendRequest(new Request(RequestType.JOIN_CONVERSATION,
                new JoinConversationPayload(conversationId), currentUser.getUserId()));
    }

    public UserInfo getCurrentUserInfo() { return currentUser; }
    public boolean isLoggedIn()           { return loggedIn; }

    /** Package-private: seeds the directory list for unit tests without a live server. */
    void setCurrentDirectoryForTesting(ArrayList<UserInfo> dir) {
        this.currentDirectory = new ArrayList<>(dir);
    }

    /** Returns all users whose id or name contains {@code query} (case-insensitive). */
    public ArrayList<UserInfo> getFilteredDirectory(String query) {
        if (query == null || query.isBlank()) return new ArrayList<>(currentDirectory);
        ArrayList<UserInfo> filtered = new ArrayList<>();
        String q = query.toLowerCase();
        for (UserInfo u : currentDirectory) {
            if (u.getUserId().toLowerCase().contains(q) || u.getName().toLowerCase().contains(q)) {
                filtered.add(u);
            }
        }
        return filtered;
    }

    /** Returns conversations where any participant's id or name matches {@code query}. */
    public ArrayList<Conversation> getFilteredConversationList(String query) {
        if (query == null || query.isBlank()) return new ArrayList<>(conversations);
        ArrayList<Conversation> filtered = new ArrayList<>();
        String q = query.toLowerCase();
        for (Conversation c : conversations) {
            for (UserInfo p : c.getParticipants()) {
                if (p.getName().toLowerCase().contains(q) || p.getUserId().toLowerCase().contains(q)) {
                    filtered.add(c);
                    break;
                }
            }
        }
        return filtered;
    }

    /** Returns admin search results filtered by participant id or name. */
    public ArrayList<Conversation> getFilteredAdminConversationSearch(String q) {
        if (q == null || q.isBlank()) return new ArrayList<>(currentAdminConversationSearch);
        ArrayList<Conversation> filtered = new ArrayList<>();
        String query = q.toLowerCase();
        for (Conversation c : currentAdminConversationSearch) {
            for (UserInfo p : c.getParticipants()) {
                if (p.getName().toLowerCase().contains(query) || p.getUserId().toLowerCase().contains(query)) {
                    filtered.add(c);
                    break;
                }
            }
        }
        return filtered;
    }

    public void setCurrentConversationId(long conversationId) { this.currentConversationId = conversationId; }
    public long getCurrentConversationId() { return currentConversationId; }

    /** Returns the full {@link Conversation} object for the currently selected conversation, or null. */
    public Conversation getCurrentConversation() {
        if (currentConversationId == 0) return null;
        for (Conversation c : conversations) {
            if (c.getConversationId() == currentConversationId) return c;
        }
        return null;
    }

    /** Writes a {@link Request} to the socket stream. Synchronized to prevent interleaved writes. */
    private synchronized void sendRequest(Request r) {
        if (outputStream == null || connectionStatus != ConnectionStatus.CONNECTED) return;
        try {
            outputStream.writeObject(r);
            outputStream.flush();
            outputStream.reset(); // prevent object-reference cache memory leak
            lastActivityTimestamp = System.currentTimeMillis();
        } catch (IOException e) {
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
            e.printStackTrace();
        }
    }

    private void enqueueRequest(Request r) {
        requestQueue.add(r);
    }

    // -------------------------------------------------------------------------
    /** Continuously reads {@link Response} objects from the server and dispatches them. */
    class ResponseListener implements Runnable {
        public ResponseListener() {}

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()
                    && connectionStatus == ConnectionStatus.CONNECTED) {
                try {
                    Object obj = inputStream.readObject();
                    if (obj instanceof Response) {
                        processResponse((Response) obj);
                    }
                } catch (EOFException | SocketException e) {
                    break; // server closed connection cleanly
                } catch (IOException | ClassNotFoundException e) {
                    if (connectionStatus == ConnectionStatus.CONNECTED) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
        }
    }

    // -------------------------------------------------------------------------
    /** Sends periodic PING requests and logs the user out after 5 minutes of inactivity. */
    class InactivityDetector implements Runnable {
        private static final long PING_INTERVAL_MS    = 30_000L;  // ping every 30 s
        private static final long INACTIVITY_LIMIT_MS = 300_000L; // logout after 5 min

        public InactivityDetector() {}

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()
                    && connectionStatus == ConnectionStatus.CONNECTED) {
                try {
                    Thread.sleep(PING_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                long idle = System.currentTimeMillis() - lastActivityTimestamp;
                if (idle >= INACTIVITY_LIMIT_MS) {
                    logout();
                    break;
                }
                if (loggedIn && currentUser != null) {
                    sendRequest(new Request(RequestType.PING, currentUser.getUserId()));
                }
            }
        }
    }
}
