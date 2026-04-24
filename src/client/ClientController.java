package client;

import shared.enums.*;
import shared.networking.*;
import shared.networking.User.UserInfo;
import shared.payload.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

public class ClientController {

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    private ClientUI gui;

    private ConnectionStatus connectionStatus;
    private Deque<Request> requestQueue;
    private boolean loggedIn;
    private UserInfo currentUser;


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
    /** 0 means no conversation selected. */
    private long currentConversationId;
    private ArrayList<UserInfo> currentDirectory;

    private ArrayList<Conversation> currentConversationList;
    private ArrayList<Conversation> currentAdminConversationSearch;
    private Thread responseListenerThread;
    private Thread inactivityDetectorThread;

    public static void main(String[] args) {
    	ClientController ctr = new ClientController("localhost", 8080);
    

    private ArrayList<ConversationMetadata> currentAdminConversationSearch;

    // -------------------------------------------------------------------------
    // Server path liveness (updated on inbound server-driven signals, e.g. PONG)
    // -------------------------------------------------------------------------

    private volatile long lastServerActivityMillis = System.currentTimeMillis();

    public static void main(String[] args) {
        new ClientController("localhost", 8080);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

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
    private void close() {
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
    private synchronized void disconnectSocket() {
        disconnectSocket(null);
    }

    /**
     * Like {@link #disconnectSocket()}, but if {@code logoutUserId} is non-null and the connection is still
     * up, sends {@link RequestType#LOGOUT} on the wire directly (not via {@link #requestQueue}) before
     * tearing down the socket.
     */
    private synchronized void disconnectSocket(String logoutUserId) {
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

    public void register(String userId, String realName, String loginName, char[] password) {
        
    }

    public void login(String loginName, char[] password) {
        // TODO
    }

    public void logout() {
        // TODO

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

    public void sendMessage(Conversation conversation, String m) {
        // TODO
    }


    public void searchDirectory(String query) {
        // TODO
    }

    public void searchConversationList(String query) {
        // TODO
    }


    /** Matches DataManager.handleAdminConversationQuery — payload: AdminConversationQuery(userId). */

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

    // -------------------------------------------------------------------------
    // UI/local filtering + read-only accessors.
    // -------------------------------------------------------------------------

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

    public UserInfo getCurrentUserInfo() { return currentUser; }

    public boolean isLoggedIn()           { return loggedIn; }


    public ArrayList<UserInfo> getFilteredDirectory(String query) {
    	ArrayList<UserInfo> filtered = new ArrayList<>();
    	for(UserInfo item: currentDirectory) {
    		if(item.getName().toUpperCase().contains(query)) {
    			filtered.add(item);
    		}
    	}
        return filtered;
    }

    public ArrayList<Conversation> getFilteredConversationList(String query) {
    	ArrayList<Conversation> filtered = new ArrayList<>();
    	for(Conversation item: conversations) {
    		for(UserInfo user: item.getParticipants()) {
    			if(user.getName().toUpperCase().contains(query)) {
	    			filtered.add(item);
	    		}
    		}
    	}
        return filtered;
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
                    sendRequest(r);
                } catch (IOException e) {
                    requestQueue.addFirst(r);
                    connectionStatus = ConnectionStatus.NOT_CONNECTED;
                    e.printStackTrace();
                    try {
                        Thread.sleep(300L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
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
                        && connectionStatus == ConnectionStatus.CONNECTED && inputStream != null) {
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
