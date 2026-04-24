package client;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import javax.swing.DefaultListModel;
import javax.swing.SwingUtilities;
import shared.enums.*;
import shared.networking.*;
import shared.networking.User.UserInfo;
import shared.payload.*;

public class ClientController {

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    private ClientUI gui;

    // -------------------------------------------------------------------------
    // Server endpoint (fixed for this controller instance)
    // -------------------------------------------------------------------------

    private String hostIp;
    private int hostPort;

    // -------------------------------------------------------------------------
    // TCP connection + object streams
    // -------------------------------------------------------------------------

    private ConnectionStatus connectionStatus;
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    // -------------------------------------------------------------------------
    // Outbound requests + background workers
    // -------------------------------------------------------------------------

    private LinkedBlockingDeque<Request> requestQueue;
    private Thread requestDrainThread;
    private Thread responseListenerThread;
    private Thread inactivityDetectorThread;

    // -------------------------------------------------------------------------
    // Session (authenticated user)
    // -------------------------------------------------------------------------

    private boolean loggedIn;
    private UserInfo currentUser;

    // -------------------------------------------------------------------------
    // Client-side caches backing list views / filters
    // -------------------------------------------------------------------------

    private ArrayList<Conversation> conversations;
    /** -1 means no conversation selected. */
    private long currentConversationId = -1;
    private ArrayList<UserInfo> currentDirectory;
    private ArrayList<ConversationMetadata> currentAdminConversationSearch;

    // -------------------------------------------------------------------------
    // Server path liveness (updated on inbound server-driven signals, e.g. PONG)
    // -------------------------------------------------------------------------

    private volatile long lastServerActivityMillis = System.currentTimeMillis();

    public static void main(String[] args) {
        new ClientController("localhost", 8080);
    }

    public ClientController(String hostIp, int hostPort) {
        this.hostIp = hostIp;
        this.hostPort = hostPort;
        this.connectionStatus = ConnectionStatus.NOT_CONNECTED;
        this.requestQueue = new LinkedBlockingDeque<>();
        this.loggedIn = false;
        this.conversations = new ArrayList<>();
        this.currentDirectory = new ArrayList<>();
        this.currentAdminConversationSearch = new ArrayList<>();
        this.gui = new ClientUI(this);
        gui.showLoginView();
        startRequestDrainThread();
        startResponseListenerThread();
        startInactivityDetectorThread();
    }

    /** Runs the request loop in the current thread (blocking). For tests / headless use. */
    void runRequestLoop() {
        try {
            ensureConnected();
        } catch (IOException e) {
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
            e.printStackTrace();
            return;
        }
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Request r = requestQueue.take();
                sendRequest(r);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
        this.currentAdminConversationSearch = new ArrayList<>();
        this.gui = guiOverride;
        // Integration tests pass a real port (>0) and need request processing
        if (hostPort > 0) {
            startRequestDrainThread();
        }
    }

    /** Interrupts workers and tears down the TCP connection. Use for application shutdown. */
    public void close() {
        if (requestDrainThread != null) requestDrainThread.interrupt();
        if (responseListenerThread != null) responseListenerThread.interrupt();
        if (inactivityDetectorThread != null) inactivityDetectorThread.interrupt();
        disconnectSocket();
    }

    /**
     * Closes object streams and the TCP socket and sets {@link #connectionStatus} to
     * {@link ConnectionStatus#NOT_CONNECTED}. Does <b>not</b> interrupt the request drain, response listener,
     * or inactivity threads — they keep running and can wait for the next lazy reconnect (e.g. after
     * manual logout while the UI stays up). For full shutdown use {@link #close()}.
     */
    public synchronized void disconnectSocket() {
        disconnectSocket(null);
    }

    /**
     * Like {@link #disconnectSocket()}, but if {@code logoutUserId} is non-null and the connection is still
     * up, sends {@link RequestType#LOGOUT} on the wire directly (not via {@link #requestQueue}) before
     * tearing down the socket.
     */
    public synchronized void disconnectSocket(String logoutUserId) {
        if (logoutUserId != null && outputStream != null
                && connectionStatus == ConnectionStatus.CONNECTED) {
            try {
                outputStream.writeObject(new Request(RequestType.LOGOUT, logoutUserId));
                outputStream.flush();
                outputStream.reset();
            } catch (IOException e) {
                connectionStatus = ConnectionStatus.NOT_CONNECTED;
                e.printStackTrace();
            }
        }
        connectionStatus = ConnectionStatus.NOT_CONNECTED;
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException ignored) {
        }
        inputStream = null;
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException ignored) {
        }
        outputStream = null;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        socket = null;
    }

    /** Package-private so tests can drive response handling directly without a live socket. */
    void processResponse(Response response) {
        if (response == null) return;
        switch (response.getType()) {

            case LOGIN_RESULT: {
                LoginResult lr = (LoginResult) response.getPayload();
                switch (lr.getLoginStatus()) {
                    case SUCCESS:
                        loggedIn = true;
                        currentUser = lr.getUserInfo();
                        conversations = lr.getConversationList() != null
                                ? lr.getConversationList() : new ArrayList<>();
                        if (gui != null) {
                            if (currentUser.getUserType() == UserType.ADMIN) {
                                gui.showAdminMainView();
                            } else {
                                gui.showMainView();
                            }
                        }
                        break;
                    case INVALID_CREDENTIALS:
                    case NO_ACCOUNT_EXISTS:
                    case DUPLICATE_SESSION:
                        if (gui != null) gui.showLoginError(lr.getLoginStatus());
                        break;
                }
                break;
            }

            case REGISTER_RESULT: {
                RegisterResult rr = (RegisterResult) response.getPayload();
                switch (rr.getRegisterStatus()) {
                    case SUCCESS:
                        if (gui != null) gui.showLoginView();
                        break;
                    case USER_ID_TAKEN:
                    case USER_ID_INVALID:
                    case LOGIN_NAME_TAKEN:
                    case LOGIN_NAME_INVALID:
                        if (gui != null) gui.showRegisterError(rr.getRegisterStatus());
                        break;
                }
                break;
            }

            case MESSAGE: {
                // Move the matching conversation to front (most recent), then refresh the list view.
                Message msg = (Message) response.getPayload();
                for (int i = 0; i < conversations.size(); i++) {
                    if (conversations.get(i).getConversationId() == msg.getConversationId()) {
                        Conversation c = conversations.remove(i);
                        c.append(msg);
                        conversations.add(0, c);
                        break;
                    }
                }
                if (gui != null) {
                    ArrayList<Conversation> snapshot = new ArrayList<>(conversations);
                    DefaultListModel model = gui.getConversationListViewModel();
                    SwingUtilities.invokeLater(() -> {
                        model.clear();
                        for (Conversation c : snapshot) model.addElement(c);
                    });
                }
                break;
            }

            case CONVERSATION: {
                // Move to front regardless of whether it is new or an update (recency sort).
                Conversation conv = (Conversation) response.getPayload();
                conversations.removeIf(c -> c.getConversationId() == conv.getConversationId());
                conversations.add(0, conv);
                if (gui != null) {
                    ArrayList<Conversation> snapshot = new ArrayList<>(conversations);
                    DefaultListModel model = gui.getConversationListViewModel();
                    SwingUtilities.invokeLater(() -> {
                        model.clear();
                        for (Conversation c : snapshot) model.addElement(c);
                    });
                }
                break;
            }

            case LEAVE_RESULT: {
                LeaveResult lr = (LeaveResult) response.getPayload();
                long leftId = lr.getLeftConversationID();
                conversations.removeIf(c -> c.getConversationId() == leftId);
                
                // If we just left the current conversation, switch to the most recent one
                if (currentConversationId == leftId) {
                    currentConversationId = conversations.isEmpty() ? -1 : conversations.get(0).getConversationId();
                }
                
                if (gui != null) {
                    ArrayList<Conversation> snapshot = new ArrayList<>(conversations);
                    Conversation current = getCurrentConversation();
                    DefaultListModel conversationListModel = gui.getConversationListViewModel();
                    DefaultListModel conversationViewModel = gui.getConversationViewModel();
                    
                    SwingUtilities.invokeLater(() -> {
                        // Update conversation list (sidebar)
                        conversationListModel.clear();
                        for (Conversation c : snapshot) conversationListModel.addElement(c);
                        
                        // Update conversation view (message panel) to show the new current conversation
                        conversationViewModel.clear();
                        if (current != null) {
                            for (Message msg : current.getMessages()) {
                                conversationViewModel.addElement(msg);
                            }
                        }
                    });
                }
                break;
            }

            case ADMIN_CONVERSATION_RESULT: {
                // AdminConversationResult carries ConversationMetadata — store it directly.
                AdminConversationResult acr = (AdminConversationResult) response.getPayload();
                currentAdminConversationSearch = new ArrayList<>(acr.getConversations());
                break;
            }

            case CONNECTED:
                connectionStatus = ConnectionStatus.CONNECTED;
                break;

            case PONG:
                lastServerActivityMillis = System.currentTimeMillis();
                break;

            default:
                break;
        }
    }

    /** Lazily opens a TCP connection to the server and initializes the input and output streams*/
    private synchronized void ensureConnected() throws IOException {
        if (connectionStatus == ConnectionStatus.CONNECTED) return;
        connectionStatus = ConnectionStatus.CONNECTING;
        socket = new Socket(hostIp, hostPort);
        // OOS must be created and flushed BEFORE OIS on both sides to avoid header deadlock
        // (mirrors the convention in ConnectionHandler on the server).
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush();
        inputStream = new ObjectInputStream(socket.getInputStream());
        connectionStatus = ConnectionStatus.CONNECTED;
        lastServerActivityMillis = System.currentTimeMillis();
    }

    // -------------------------------------------------------------------------
    // Public action methods — each maps 1-to-1 with a DataManager handler.
    // -------------------------------------------------------------------------

    /** Matches DataManager.handleRegister — payload: RegisterCredentials(userId, loginName, password, name). */
    public void register(String userId, String realName, String loginName, char[] password) {
        RegisterCredentials creds = new RegisterCredentials(userId, loginName, new String(password), realName);
        enqueueRequest(new Request(RequestType.REGISTER, creds, null));
    }

    /** Matches DataManager.handleLogin — payload: LoginCredentials(loginName, password). */
    public void login(String loginName, char[] password) {
        LoginCredentials creds = new LoginCredentials(loginName, new String(password));
        enqueueRequest(new Request(RequestType.LOGIN, creds, null));
    }

    /**
     * Clears local session (lists, queue, flags), shows login UI, sends {@link RequestType#LOGOUT} on the
     * wire (if connected), then {@link #disconnectSocket(String)}. Session tracking lives outside
     * {@code DataManager}.
     */
    public void logout() {
        if (!loggedIn || currentUser == null) return;
        String userId = currentUser.getUserId();
        loggedIn = false;
        currentUser = null;
        conversations.clear();
        currentConversationId = -1;
        currentAdminConversationSearch.clear();
        currentDirectory.clear();
        requestQueue.clear();
        if (gui != null) gui.showLoginView();
        disconnectSocket(userId);
    }

    /** Matches DataManager.handleSendMessage — payload: RawMessage(text, conversationId). */
    public void sendMessage(long conversationId, String m) {
        if (!loggedIn || currentUser == null) return;
        enqueueRequest(new Request(RequestType.MESSAGE,
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
        gui.getConversationListViewModel().clear();
        for (Conversation c : getFilteredConversationList(query)) {
            gui.getConversationListViewModel().addElement(c);
        }
    }

    /** Matches DataManager.handleAdminConversationQuery — payload: AdminConversationQuery(userId). */
    public void adminConversationSearch(String query) {
        if (!loggedIn || currentUser == null || currentUser.getUserType() != UserType.ADMIN) return;
        enqueueRequest(new Request(RequestType.ADMIN_CONVERSATION_QUERY,
                new AdminConversationQuery(query), currentUser.getUserId()));
    }

    /** Matches DataManager.handleCreateConversation — payload: CreateConversationPayload(participants). */
    public void createConversation(ArrayList<UserInfo> p) {
        if (!loggedIn || currentUser == null) return;
        enqueueRequest(new Request(RequestType.CREATE_CONVERSATION,
                new CreateConversationPayload(p), currentUser.getUserId()));
    }

    /** Matches DataManager.handleAddToConversation — payload: AddToConversationPayload(participants, conversationId). */
    public void addToConversation(ArrayList<UserInfo> p, long conversationId) {
        if (!loggedIn || currentUser == null) return;
        enqueueRequest(new Request(RequestType.ADD_PARTICIPANT,
                new AddToConversationPayload(p, conversationId), currentUser.getUserId()));
    }

    /** Matches DataManager.handleLeaveConversation — payload: LeaveConversationPayload(conversationId). */
    public void leaveConversation(long conversationId) {
        if (!loggedIn || currentUser == null) return;
        enqueueRequest(new Request(RequestType.LEAVE_CONVERSATION,
                new LeaveConversationPayload(conversationId), currentUser.getUserId()));
    }

    /** Matches DataManager.handleAdminConversationQuery — payload: AdminConversationQuery(userID). */
    public void adminGetUserConversations(String userID) {
        if (!loggedIn || currentUser == null || currentUser.getUserType() != UserType.ADMIN) return;
        enqueueRequest(new Request(RequestType.ADMIN_CONVERSATION_QUERY,
                new AdminConversationQuery(userID), currentUser.getUserId()));
    }

    /** Matches DataManager.handleJoinConversation — payload: JoinConversationPayload(conversationId). */
    public void joinConversation(long conversationId) {
        if (!loggedIn || currentUser == null || currentUser.getUserType() != UserType.ADMIN) return;
        enqueueRequest(new Request(RequestType.JOIN_CONVERSATION,
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
    public ArrayList<ConversationMetadata> getFilteredAdminConversationSearch(String q) {
        if (q == null || q.isBlank()) return new ArrayList<>(currentAdminConversationSearch);
        ArrayList<ConversationMetadata> filtered = new ArrayList<>();
        String query = q.toLowerCase();
        for (ConversationMetadata m : currentAdminConversationSearch) {
            for (UserInfo p : m.getParticipants()) {
                if (p.getName().toLowerCase().contains(query) || p.getUserId().toLowerCase().contains(query)) {
                    filtered.add(m);
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
        if (currentConversationId == -1) return null;
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
        } catch (IOException e) {
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
            e.printStackTrace();
        }
    }

    private void enqueueRequest(Request r) {
        requestQueue.add(r);
    }

    // -------------------------------------------------------------------------
    // Background thread starters (daemon workers for queue drain, responses,
    // and connection / ping keepalive).
    // -------------------------------------------------------------------------

    /** Drains {@link #requestQueue}: connect if needed, then {@link #sendRequest(Request)}. */
    private void startRequestDrainThread() {
        requestDrainThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Request r = null;
                try {
                    r = requestQueue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                try {
                    ensureConnected();
                } catch (IOException e) {
                    requestQueue.addFirst(r);
                    connectionStatus = ConnectionStatus.NOT_CONNECTED;
                    e.printStackTrace();
                }
                sendRequest(r);
            }
        }, "request-drain");
        requestDrainThread.setDaemon(true);
        requestDrainThread.start();
    }

    /** Waits for TCP, then reads {@link Response} objects and dispatches them. */
    private void startResponseListenerThread() {
        responseListenerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                while (!Thread.currentThread().isInterrupted()
                        && connectionStatus != ConnectionStatus.CONNECTED) {
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (Thread.currentThread().isInterrupted()) break;

                readLoop:
                while (!Thread.currentThread().isInterrupted()
                        && connectionStatus == ConnectionStatus.CONNECTED) {
                    try {
                        Object obj = inputStream.readObject();
                        if (obj instanceof Response) {
                            processResponse((Response) obj);
                        }
                    } catch (EOFException | SocketException e) {
                        connectionStatus = ConnectionStatus.NOT_CONNECTED;
                        break readLoop;
                    } catch (IOException | ClassNotFoundException e) {
                        connectionStatus = ConnectionStatus.NOT_CONNECTED;
                        e.printStackTrace();
                        break readLoop;
                    }
                }
            }
        }, "client-resp");
        responseListenerThread.setDaemon(true);
        responseListenerThread.start();
    }

    private void startInactivityDetectorThread() {
        final long pingIntervalMs = 30_000L;
        final long inactivityLimitMs = 300_000L;
        final long waitForConnectPollMs = 500L;
        inactivityDetectorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                while (!Thread.currentThread().isInterrupted()
                        && connectionStatus != ConnectionStatus.CONNECTED) {
                    try {
                        Thread.sleep(waitForConnectPollMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (Thread.currentThread().isInterrupted()) break;

                while (!Thread.currentThread().isInterrupted()
                        && connectionStatus == ConnectionStatus.CONNECTED) {
                    try {
                        Thread.sleep(pingIntervalMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    long idle = System.currentTimeMillis() - lastServerActivityMillis;
                    if (idle >= inactivityLimitMs) {
                        logout();
                        break;
                    }
                    if (loggedIn && currentUser != null) {
                        sendRequest(new Request(RequestType.PING, currentUser.getUserId()));
                    }
                }
            }
        }, "client-inact");
        inactivityDetectorThread.setDaemon(true);
        inactivityDetectorThread.start();
    }
}
