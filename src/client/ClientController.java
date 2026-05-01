package client;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import shared.enums.*;
import shared.networking.*;
import shared.networking.User.UserInfo;
import shared.payload.*;

public class ClientController {

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    private final ClientUI gui;

    // -------------------------------------------------------------------------
    // Server endpoint (fixed for this controller instance)
    // -------------------------------------------------------------------------

    private final String hostIp;
    private final int hostPort;

    // -------------------------------------------------------------------------
    // TCP connection + object streams
    // -------------------------------------------------------------------------

    private volatile ConnectionStatus connectionStatus = ConnectionStatus.NOT_CONNECTED;
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    // -------------------------------------------------------------------------
    // Outbound requests + background workers
    // -------------------------------------------------------------------------

    private final LinkedBlockingDeque<Request> requestQueue = new LinkedBlockingDeque<>();
    private Thread requestDrainThread;
    private Thread responseListenerThread;
    private Thread inactivityDetectorThread;

    // -------------------------------------------------------------------------
    // Session (authenticated user)
    // -------------------------------------------------------------------------

    private volatile boolean loggedIn = false;
    /**
     * The reference is reassigned off-EDT (response listener publishes a fresh instance with
     * lastRead pre-populated); after publication, the lastRead map is mutated only on the EDT.
     * volatile ensures readers observe the freshest reference.
     */
    private volatile UserInfo currentUser;
    /** Atomic so rapid duplicate clicks lose the compareAndSet race rather than both passing
     *  a non-atomic {@code if (!flag) flag = true}. */
    private final AtomicBoolean authInFlight = new AtomicBoolean(false);
    /** EDT-only single-shot timer that surfaces a network error if the server never replies. */
    private javax.swing.Timer authWatchdogTimer;
    private static final int AUTH_WATCHDOG_MS = 15_000;
    private volatile String lastRegisteredLoginName = null;

    // -------------------------------------------------------------------------
    // Client-side caches backing list views / filters
    // -------------------------------------------------------------------------

    private final List<Conversation> conversations = Collections.synchronizedList(new ArrayList<>());
    /** -1 means no conversation selected. */
    private volatile long currentConversationId = -1;
    private ArrayList<UserInfo> currentDirectory = new ArrayList<>();
    private ArrayList<ConversationMetadata> currentAdminConversationSearch = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Window-focus gate: inbound messages while the OS window is inactive must
    // NOT advance the read pointer. Buffer the per-conversation max sequence
    // and replay on activation, coalescing a burst into one UPDATE_READ_MESSAGES.
    // -------------------------------------------------------------------------

    /** Defaults true so headless tests and pre-window-event states do not suppress reads. */
    private final AtomicBoolean windowActive = new AtomicBoolean(true);
    /**
     * Conversation id → highest seq of an inbound message that arrived while windowActive=false.
     * <p>Concurrency: writes and the drain-and-clear all serialize via the {@code conversations}
     * monitor. Reads outside that monitor are unsafe.
     */
    private final HashMap<Long, Long> deferredReadAdvance = new HashMap<>();

    // -------------------------------------------------------------------------
    // Server path liveness (updated on inbound server-driven signals, e.g. PONG)
    // -------------------------------------------------------------------------

    private volatile long lastServerActivityMillis = System.currentTimeMillis();

    // -------------------------------------------------------------------------
    // Tunables
    // -------------------------------------------------------------------------

    /** Bounded so an unreachable server fails fast (~7s) rather than the ~75s OS default. */
    private static final int CONNECT_TIMEOUT_MS = 7000;
    /** Send a PING this often while connected to keep NAT/firewall paths warm. */
    private static final long PING_INTERVAL_MS = 30_000L;
    /** No server activity for this long → assume the server is gone and force-logout. */
    private static final long INACTIVITY_LIMIT_MS = 300_000L;
    private static final long RECONNECT_POLL_MS = 500L;
    private static final long RETRY_BACKOFF_MS = 300L;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        ClientController controller = new ClientController(host, port);
        Runtime.getRuntime().addShutdownHook(new Thread(controller::close, "ClientController-Shutdown"));
    }

    public ClientController(String hostIp, int hostPort) {
        this.hostIp = hostIp;
        this.hostPort = hostPort;
        this.gui = new ClientUI(this);
        gui.showLoginView();
        startRequestDrainThread();
        startResponseListenerThread();
        startInactivityDetectorThread();
    }

    /** Test seam: accepts a pre-built (or null) GUI; no Swing window opened. */
    ClientController(String hostIp, int hostPort, ClientUI guiOverride) {
        this.hostIp = hostIp;
        this.hostPort = hostPort;
        this.gui = guiOverride;
        if (hostPort > 0) {
            startRequestDrainThread();
        }
    }

    private void close() {
        if (requestDrainThread != null) requestDrainThread.interrupt();
        if (responseListenerThread != null) responseListenerThread.interrupt();
        if (inactivityDetectorThread != null) inactivityDetectorThread.interrupt();
        disconnectSocket();
    }

    /**
     * Closes streams and socket without stopping background threads, so they can wait for the
     * next lazy reconnect. Field swaps happen under {@code synchronized(this)} so concurrent
     * {@link #ensureConnected} / {@link #sendRequest} calls observe a consistent (out, in, socket) triple.
     */
    private void disconnectSocket() {
        final ObjectOutputStream out;
        final ObjectInputStream in;
        final Socket sock;
        synchronized (this) {
            out = outputStream; outputStream = null;
            in = inputStream;   inputStream = null;
            sock = socket;      socket = null;
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
        }
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (sock != null && !sock.isClosed()) sock.close(); } catch (IOException ignored) {}
    }

    /** Test seam: drive response handling directly without a live socket. */
    void processResponse(Response response) {
        if (response == null) return;
        lastServerActivityMillis = System.currentTimeMillis();
        switch (response.getType()) {
            case LOGIN_RESULT:
                handleLoginResultResponse(response);
                break;
            case REGISTER_RESULT:
                handleRegisterResultResponse(response);
                break;
            case MESSAGE:
                handleMessageResponse(response);
                break;
            case CONVERSATION:
                handleConversationResponse(response);
                break;
            case LEAVE_RESULT:
                handleLeaveResultResponse(response);
                break;
            case READ_MESSAGES_UPDATED:
                handleReadMessagesUpdatedResponse(response);
                break;
            case ADMIN_CONVERSATION_RESULT:
                handleAdminConversationResultResponse(response);
                break;
            case ADMIN_VIEW_CONVERSATION_RESULT:
                handleAdminViewConversationResultResponse(response);
                break;
            case CONVERSATION_METADATA:
                handleConversationMetadataResponse(response);
                break;
            case USER_CREATION:
                handleUserCreationResponse(response);
                break;
            case CONNECTED:
                connectionStatus = ConnectionStatus.CONNECTED;
                break;
            case PONG:
                // lastServerActivityMillis already updated above; nothing else to do.
                break;
            default:
                break;
        }
    }

    private void handleLoginResultResponse(Response response) {
        LoginResult lr = (LoginResult) response.getPayload();
        // Coalesce field reset + UI updates into one EDT task so the in-flight flag and
        // button state cannot be observed out-of-order.
        SwingUtilities.invokeLater(() -> {
            authInFlight.set(false);
            cancelAuthWatchdog();
            if (gui != null) gui.setLoginInFlight(false);
            switch (lr.getLoginStatus()) {
                case SUCCESS:
                    loggedIn = true;
                    currentUser = lr.getUserInfo();
                    // Defense-in-depth: server already sorts the wire payload, but re-sort
                    // here so the controller's recency invariant holds for any direct consumer.
                    ArrayList<Conversation> sorted = lr.getConversationList() != null
                            ? new ArrayList<>(lr.getConversationList()) : new ArrayList<>();
                    sorted.sort((a, b) -> Long.compare(latestSequenceNumber(b), latestSequenceNumber(a)));
                    synchronized (conversations) {
                        conversations.clear();
                        conversations.addAll(sorted);
                    }
                    currentDirectory = lr.getDirectoryUserInfoList() != null
                            ? lr.getDirectoryUserInfoList() : new ArrayList<>();
                    if (gui != null) {
                        gui.showMainView();
                    }
                    break;
                case INVALID_CREDENTIALS:
                case NO_ACCOUNT_EXISTS:
                case DUPLICATE_SESSION:
                    if (gui != null) gui.showLoginError(lr.getLoginStatus());
                    break;
            }
        });
    }

    private void handleRegisterResultResponse(Response response) {
        RegisterResult rr = (RegisterResult) response.getPayload();
        SwingUtilities.invokeLater(() -> {
            authInFlight.set(false);
            cancelAuthWatchdog();
            if (gui != null) gui.setLoginInFlight(false);
            switch (rr.getRegisterStatus()) {
                case SUCCESS:
                    if (gui != null) gui.showLoginView(lastRegisteredLoginName);
                    break;
                case USER_ID_TAKEN:
                case USER_ID_INVALID:
                case LOGIN_NAME_TAKEN:
                case LOGIN_NAME_INVALID:
                    if (gui != null) gui.showRegisterError(rr.getRegisterStatus());
                    break;
            }
        });
    }

    private void handleMessageResponse(Response response) {
        Message msg = (Message) response.getPayload();
        ArrayList<Conversation> snapshot;
        boolean isCurrentConv;
        boolean shouldAdvanceLastRead = false;
        // The lastRead-advance decision must happen under the same monitor that
        // openConversationAtomically uses, so a click-to-open cannot capture a stale lastRead.
        // The setLastRead mutation is deferred to the EDT (per UserInfo's contract); EDT
        // preserves submission order, so the per-conversation pointer stays monotonic.
        synchronized (conversations) {
            for (int i = 0; i < conversations.size(); i++) {
                if (conversations.get(i).getConversationId() == msg.getConversationId()) {
                    Conversation c = conversations.remove(i);
                    c.append(msg);
                    conversations.add(0, c);
                    break;
                }
            }
            snapshot = new ArrayList<>(conversations);
            isCurrentConv = msg.getConversationId() == currentConversationId;
            if (isCurrentConv && currentUser != null) {
                boolean userViewingBottom = (gui == null) || gui.userIsViewingBottom();
                if (windowActive.get() && userViewingBottom) {
                    shouldAdvanceLastRead = true;
                } else {
                    deferredReadAdvance.merge(msg.getConversationId(), msg.getSequenceNumber(), Math::max);
                }
            }
        }
        if (shouldAdvanceLastRead) {
            final long convId = msg.getConversationId();
            final long seq = msg.getSequenceNumber();
            SwingUtilities.invokeLater(() -> {
                UserInfo me = currentUser;
                if (me != null) me.setLastRead(convId, seq);
            });
        }
        if (gui != null) {
            gui.updateConversationListModel(snapshot);
            if (isCurrentConv) {
                gui.appendMessageToConversationView(msg);
            }
        }
        if (shouldAdvanceLastRead) {
            updateReadMessages(msg.getConversationId(), msg.getSequenceNumber());
        }
    }

    /**
     * Gate is {@code windowActive && userIsViewingBottom}: optimistically advance
     * {@code currentUser.lastRead} and ask the server to persist; otherwise buffer the
     * per-conversation max seq for replay on the next window activation.
     * <p>Test seam: {@link #handleMessageResponse} inlines the same decision on the live
     * path under the {@code conversations} monitor — keep the two implementations in sync.
     * Headless callers (gui == null) implicitly satisfy the bottom-viewing predicate.
     */
    void tryAdvanceReadOnInbound(long convId, long seq) {
        UserInfo me = currentUser;
        if (me == null) return;
        boolean userViewingBottom = (gui == null) || gui.userIsViewingBottom();
        if (windowActive.get() && userViewingBottom) {
            me.setLastRead(convId, seq);
            updateReadMessages(convId, seq);
        } else {
            synchronized (conversations) {
                deferredReadAdvance.merge(convId, seq, Math::max);
            }
        }
    }

    public void setWindowActive(boolean active) {
        windowActive.set(active);
    }

    public boolean isWindowActive() {
        return windowActive.get();
    }

    /**
     * Drain {@link #deferredReadAdvance}: send one UPDATE_READ_MESSAGES per conversation at the
     * highest deferred seq, advance local lastRead, and re-sync the open conversation. Idempotent
     * on an empty buffer. Drain-and-clear runs under {@code synchronized(conversations)} so it
     * cannot race a concurrent inbound merging into the same map.
     */
    public void replayReadAdvanceIfNeeded() {
        UserInfo me = currentUser;
        if (me == null) return;
        HashMap<Long, Long> drained;
        long openId;
        synchronized (conversations) {
            if (deferredReadAdvance.isEmpty()) return;
            drained = new HashMap<>(deferredReadAdvance);
            deferredReadAdvance.clear();
            openId = currentConversationId;
        }
        for (Map.Entry<Long, Long> e : drained.entrySet()) {
            long convId = e.getKey();
            long maxSeq = e.getValue();
            if (me.getLastRead(convId) < maxSeq) {
                me.setLastRead(convId, maxSeq);
                updateReadMessages(convId, maxSeq);
            }
        }
        if (gui != null && openId != -1L) {
            Long openMax = drained.get(openId);
            if (openMax != null) gui.markDisplayedReadUpTo(openMax);
            gui.repaintMessageList();
            gui.repaintConversationList();
        }
    }

    private void handleConversationResponse(Response response) {
        Conversation conv = (Conversation) response.getPayload();
        boolean isNew;
        ArrayList<Conversation> snapshot;
        synchronized (conversations) {
            isNew = conversations.removeIf(c -> c.getConversationId() == conv.getConversationId()) == false;
            conversations.add(0, conv);
            snapshot = new ArrayList<>(conversations);
        }
        if (gui != null) {
            gui.updateConversationListModel(snapshot);
            if (isNew) {
                setCurrentConversationId(conv.getConversationId());
                SwingUtilities.invokeLater(() -> gui.updateMessageListModel(conv));
                // TODO: call gui.selectConversationInList(conv) once that method exists on ClientUI
            }
        }
    }

    private void handleConversationMetadataResponse(Response response) {
        ConversationMetadata meta = (ConversationMetadata) response.getPayload();
        // TODO(meta-merge): meta is currently never merged into the matching Conversation —
        // fold participant updates in, or delete this handler if the emitter has been removed.
        ArrayList<Conversation> snapshot;
        synchronized (conversations) {
            boolean found = false;
            for (Conversation c : conversations) {
                if (c.getConversationId() == meta.getConversationId()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.err.println("CONVERSATION_METADATA: no matching conversation for id=" + meta.getConversationId());
                return;
            }
            snapshot = new ArrayList<>(conversations);
        }
        if (gui != null) {
            final ArrayList<Conversation> snap = snapshot;
            SwingUtilities.invokeLater(() -> gui.updateConversationListModel(snap));
        }
    }

    private void handleUserCreationResponse(Response response) {
        UserCreationPayload payload = (UserCreationPayload) response.getPayload();
        if (payload == null || payload.getUserInfo() == null) return;
        UserInfo created = payload.getUserInfo();
        // EDT-marshal: currentDirectory is read on EDT; mutating it off-EDT would race iteration.
        SwingUtilities.invokeLater(() -> {
            for (UserInfo u : currentDirectory) {
                if (u != null && Objects.equals(u.getUserId(), created.getUserId())) {
                    return;
                }
            }
            currentDirectory.add(created);
            if (gui != null) gui.updateDirectoryModel(getFilteredDirectory(""));
        });
    }

    private void handleLeaveResultResponse(Response response) {
        LeaveResult lr = (LeaveResult) response.getPayload();
        long leftId = lr.getLeftConversationID();
        ArrayList<Conversation> snapshot;
        synchronized (conversations) {
            conversations.removeIf(c -> c.getConversationId() == leftId);
            if (currentConversationId == leftId) {
                currentConversationId = conversations.isEmpty() ? -1 : conversations.get(0).getConversationId();
            }
            snapshot = new ArrayList<>(conversations);
        }
        if (gui != null) {
            Conversation current = getCurrentConversation();
            gui.updateConversationListModel(snapshot);
            gui.updateMessageListModel(current);
        }
    }

    private void handleReadMessagesUpdatedResponse(Response response) {
        ReadMessagesUpdated updated = (ReadMessagesUpdated) response.getPayload();
        if (updated == null || updated.getUpdatedUserInfo() == null) return;
        UserInfo fresh = updated.getUpdatedUserInfo();
        UserInfo old = currentUser;
        if (old != null) {
            // Preserve any local optimistic advance the server has not yet acked.
            // The defensive copy is load-bearing: getLastReadMap() returns an unmodifiable
            // view of the live HashMap, so iterating it raw races a concurrent EDT writer.
            Map<Long, Long> oldEntries = new HashMap<>(old.getLastReadMap());
            for (Map.Entry<Long, Long> e : oldEntries.entrySet()) {
                long convId = e.getKey();
                long localSeq = e.getValue();
                if (localSeq > fresh.getLastRead(convId)) {
                    fresh.setLastRead(convId, localSeq);
                }
            }
        }
        currentUser = fresh;
        if (gui != null) {
            gui.repaintMessageList();
            gui.repaintConversationList();
        }
    }

    private void handleAdminConversationResultResponse(Response response) {
        AdminConversationResult acr = (AdminConversationResult) response.getPayload();
        // EDT-marshal: currentAdminConversationSearch is read on the EDT; reassigning off-EDT
        // races iteration in getFilteredAdminConversationSearch.
        SwingUtilities.invokeLater(() -> {
            currentAdminConversationSearch = new ArrayList<>(acr.getConversations());
            if (gui != null) {
                gui.updateAdminConversationSearchModel(currentAdminConversationSearch);
            }
        });
    }

    /** Silent read: does not touch {@code conversations} or {@code currentConversationId},
     *  so the viewed conversation never enters the admin's own sidebar. */
    private void handleAdminViewConversationResultResponse(Response response) {
        Object payload = response.getPayload();
        if (gui != null && payload instanceof Conversation) {
            gui.showAdminConversationView((Conversation) payload);
        }
    }

    /** Synchronized so the state flip and stream creation happen atomically — concurrent
     *  callers either skip at the CONNECTED check or wait their turn. */
    private synchronized void ensureConnected() throws IOException {
        if (connectionStatus == ConnectionStatus.CONNECTED) return;
        socket = new Socket();
        socket.connect(new InetSocketAddress(hostIp, hostPort), CONNECT_TIMEOUT_MS);
        // OOS must be created and flushed BEFORE OIS on both sides to avoid header deadlock.
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush();
        inputStream = new ObjectInputStream(socket.getInputStream());
        connectionStatus = ConnectionStatus.CONNECTED;
        lastServerActivityMillis = System.currentTimeMillis();
    }

    /** Snapshot the volatile reference once so callers cannot observe it flipping to null mid-method. */
    private UserInfo requireSession() {
        UserInfo me = currentUser;
        return (loggedIn && me != null) ? me : null;
    }

    private UserInfo requireAdminSession() {
        UserInfo me = requireSession();
        return (me != null && me.getUserType() == UserType.ADMIN) ? me : null;
    }

    // -------------------------------------------------------------------------
    // Public action methods — each maps 1-to-1 with a DataManager handler.
    // -------------------------------------------------------------------------

    public void register(String userId, String realName, String loginName, char[] password) {
        if (!authInFlight.compareAndSet(false, true)) return;
        lastRegisteredLoginName = loginName;
        if (gui != null) gui.setLoginInFlight(true);
        startAuthWatchdog();
        RegisterCredentials creds = new RegisterCredentials(userId, loginName, new String(password), realName);
        enqueueRequest(new Request(RequestType.REGISTER, creds, null));
    }

    public void login(String loginName, char[] password) {
        if (!authInFlight.compareAndSet(false, true)) return;
        if (gui != null) gui.setLoginInFlight(true);
        startAuthWatchdog();
        LoginCredentials creds = new LoginCredentials(loginName, new String(password));
        enqueueRequest(new Request(RequestType.LOGIN, creds, null));
    }

    /** EDT-only. Replaces any prior watchdog so a stalled server cannot leave the login
     *  button in the in-flight state forever. */
    private void startAuthWatchdog() {
        if (authWatchdogTimer != null) authWatchdogTimer.stop();
        authWatchdogTimer = new javax.swing.Timer(AUTH_WATCHDOG_MS, e -> {
            if (authInFlight.compareAndSet(true, false)) {
                if (gui != null) {
                    gui.setLoginInFlight(false);
                    gui.showNetworkError();
                }
            }
        });
        authWatchdogTimer.setRepeats(false);
        authWatchdogTimer.start();
    }

    private void cancelAuthWatchdog() {
        if (authWatchdogTimer != null) {
            authWatchdogTimer.stop();
            authWatchdogTimer = null;
        }
    }

    /**
     * Clears local session synchronously and switches to the login view. The wire LOGOUT and
     * stream teardown run on a daemon helper so the EDT never blocks on socket I/O.
     * <p>Ordering: clear {@link #requestQueue} BEFORE nulling the streams, so any request the
     * drain thread already took will, on its next {@link #sendRequest(Request)} call, observe
     * {@code outputStream == null} and drop silently. The captured stream refs are local to the
     * helper, letting a subsequent re-login rebuild a fresh connection without racing the
     * in-flight LOGOUT write.
     */
    public void logout() {
        UserInfo me = requireSession();
        if (me == null) return;
        String userId = me.getUserId();
        requestQueue.clear();
        // Capture-and-null under `this` so concurrent ensureConnected/sendRequest see a
        // consistent (out, in, socket) triple.
        final ObjectOutputStream out;
        final ObjectInputStream in;
        final Socket sock;
        final boolean wasConnected;
        synchronized (this) {
            out = outputStream; outputStream = null;
            in = inputStream;   inputStream = null;
            sock = socket;      socket = null;
            wasConnected = connectionStatus == ConnectionStatus.CONNECTED;
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
        }
        loggedIn = false;
        currentUser = null;
        conversations.clear();
        currentConversationId = -1;
        currentAdminConversationSearch.clear();
        currentDirectory.clear();
        if (gui != null) gui.showLoginView();
        Thread t = new Thread(() -> closeWithLogout(userId, wasConnected, out, in, sock),
                              "client-logout");
        t.setDaemon(true);
        t.start();
    }

    private static void closeWithLogout(String userId, boolean wasConnected,
                                        ObjectOutputStream out, ObjectInputStream in, Socket sock) {
        if (wasConnected && out != null) {
            try {
                out.writeObject(new Request(RequestType.LOGOUT, null, userId));
                out.flush();
            } catch (IOException ignored) { /* socket already gone */ }
        }
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (sock != null && !sock.isClosed()) sock.close(); } catch (IOException ignored) {}
    }

    /** Defensive copy: the live list is reassigned + cleared on the EDT. */
    public ArrayList<ConversationMetadata> getCurrentAdminConversationSearch() {
        return new ArrayList<>(currentAdminConversationSearch);
    }

    public void clearAdminConversationSearch() {
        currentAdminConversationSearch.clear();
    }

    public void sendMessage(long conversationId, String m) {
        UserInfo me = requireSession();
        if (me == null) return;
        enqueueRequest(new Request(RequestType.MESSAGE,
                new RawMessage(m, conversationId), me.getUserId()));
    }

    public void createConversation(ArrayList<UserInfo> p) {
        UserInfo me = requireSession();
        if (me == null) return;
        ArrayList<UserInfo> participants = new ArrayList<>(p);
        boolean creatorPresent = false;
        for (UserInfo u : participants) {
            if (me.getUserId().equals(u.getUserId())) { creatorPresent = true; break; }
        }
        if (!creatorPresent) participants.add(0, me);
        enqueueRequest(new Request(RequestType.CREATE_CONVERSATION,
                new CreateConversationPayload(participants), me.getUserId()));
    }

    public void addToConversation(ArrayList<UserInfo> p, long conversationId) {
        UserInfo me = requireSession();
        if (me == null) return;
        enqueueRequest(new Request(RequestType.ADD_PARTICIPANT,
                new AddToConversationPayload(p, conversationId), me.getUserId()));
    }

    public void leaveConversation(long conversationId) {
        UserInfo me = requireSession();
        if (me == null) return;
        enqueueRequest(new Request(RequestType.LEAVE_CONVERSATION,
                new LeaveConversationPayload(conversationId), me.getUserId()));
    }

    public void updateReadMessages(long conversationId, long lastSeenSequenceNumber) {
        UserInfo me = requireSession();
        if (me == null) return;
        enqueueRequest(new Request(
                RequestType.UPDATE_READ_MESSAGES,
                new UpdateReadMessages(conversationId, lastSeenSequenceNumber),
                me.getUserId()));
    }

    public void adminGetUserConversations(String userID) {
        UserInfo me = requireAdminSession();
        if (me == null) return;
        enqueueRequest(new Request(RequestType.ADMIN_CONVERSATION_QUERY,
                new AdminConversationQuery(userID), me.getUserId()));
    }

    /** Read-only snapshot for the admin viewer; server does NOT mutate the participant list,
     *  so other clients are unaware of the lookup. */
    public void adminViewConversation(long conversationId) {
        UserInfo me = requireAdminSession();
        if (me == null) return;
        enqueueRequest(new Request(RequestType.ADMIN_VIEW_CONVERSATION,
                new AdminViewConversationQuery(conversationId), me.getUserId()));
    }

    public void joinConversation(long conversationId) {
        if (!loggedIn || currentUser == null || currentUser.getUserType() != UserType.ADMIN) return;
        enqueueRequest(new Request(RequestType.JOIN_CONVERSATION,
                new JoinConversationPayload(conversationId), currentUser.getUserId()));
    }

    // -------------------------------------------------------------------------
    // UI/local filtering + read-only accessors.
    // -------------------------------------------------------------------------

    public void searchDirectory(String query) {
        if (gui == null) return;
        gui.updateDirectoryModel(getFilteredDirectory(query));
    }

    public void searchConversationList(String query) {
        if (gui == null) return;
        gui.updateConversationListModel(getFilteredConversationList(query));
    }

    public UserInfo getCurrentUserInfo() { return currentUser; }
    public boolean isLoggedIn()           { return loggedIn; }

    /** Test seam: seed the directory list without a live server. */
    void setCurrentDirectoryForTesting(ArrayList<UserInfo> dir) {
        this.currentDirectory = new ArrayList<>(dir);
    }

    /** Test seam: verify whether optimization paths did or did not enqueue a request. */
    int requestQueueDepthForTesting() {
        return requestQueue.size();
    }

    Request peekLastRequestForTesting() {
        return requestQueue.peekLast();
    }

    public ArrayList<UserInfo> getFilteredDirectory(String query) {
        if (query == null || query.isBlank()) return new ArrayList<>(currentDirectory);
        ArrayList<UserInfo> filtered = new ArrayList<>();
        String q = query.toLowerCase();
        for (UserInfo u : currentDirectory) {
            if (userMatches(u, q)) filtered.add(u);
        }
        return filtered;
    }

    public ArrayList<Conversation> getFilteredConversationList(String query) {
        synchronized (conversations) {
            if (query == null || query.isBlank()) {
                ArrayList<Conversation> all = new ArrayList<>(conversations);
                all.sort((a, b) -> Long.compare(latestSequenceNumber(b), latestSequenceNumber(a)));
                return all;
            }
            ArrayList<Conversation> filtered = new ArrayList<>();
            String q = query.toLowerCase();
            for (Conversation c : conversations) {
                if (anyUserMatches(c.getParticipants(), q)) filtered.add(c);
            }
            filtered.sort((a, b) -> Long.compare(latestSequenceNumber(b), latestSequenceNumber(a)));
            return filtered;
        }
    }

    private static long latestSequenceNumber(Conversation c) {
        if (c == null) return 0L;
        if (c.getMessages().isEmpty()) {
            // No messages yet; fall back to conversationId for creation-recency ordering.
            return c.getConversationId();
        }
        return c.getMessages().get(c.getMessages().size() - 1).getSequenceNumber();
    }

    /** Matches both active and historical participants so orphaned conversations
     *  (everyone has left) remain searchable. */
    public ArrayList<ConversationMetadata> getFilteredAdminConversationSearch(String q) {
        if (q == null || q.isBlank()) return new ArrayList<>(currentAdminConversationSearch);
        ArrayList<ConversationMetadata> filtered = new ArrayList<>();
        String query = q.toLowerCase();
        for (ConversationMetadata m : currentAdminConversationSearch) {
            if (anyUserMatches(m.getParticipants(), query)
                    || anyUserMatches(m.getHistoricalParticipants(), query)) {
                filtered.add(m);
            }
        }
        return filtered;
    }

    /** {@code lowerQuery} must be pre-lowercased — done once at the call site, not per element. */
    private static boolean userMatches(UserInfo u, String lowerQuery) {
        if (u == null) return false;
        return u.getUserId().toLowerCase().contains(lowerQuery)
                || u.getName().toLowerCase().contains(lowerQuery);
    }

    private static boolean anyUserMatches(Collection<UserInfo> people, String lowerQuery) {
        if (people == null) return false;
        for (UserInfo u : people) {
            if (userMatches(u, lowerQuery)) return true;
        }
        return false;
    }

    public void setCurrentConversationId(long conversationId) { this.currentConversationId = conversationId; }
    public long getCurrentConversationId() { return currentConversationId; }

    public Conversation getCurrentConversation() {
        if (currentConversationId == -1) return null;
        synchronized (conversations) {
            for (Conversation c : conversations) {
                if (c.getConversationId() == currentConversationId) return c;
            }
        }
        return null;
    }

    /**
     * Atomically capture (conversation, viewer's lastRead at open time, message-list snapshot)
     * AND publish {@code currentConversationId} in one critical section. Without atomic capture,
     * a message arriving between the lastRead read and the snapshot could land on the wrong side
     * of the "New messages" divider.
     * <p>Lock order: {@code conversations} → {@code Conversation.this} — matches
     * {@link #handleMessageResponse}, so deadlock is impossible.
     * <p>Returns null if no user is logged in or the conversation is unknown.
     */
    public ConversationOpenSnapshot openConversationAtomically(long id) {
        UserInfo me = currentUser;
        if (me == null) return null;
        synchronized (conversations) {
            for (Conversation c : conversations) {
                if (c.getConversationId() == id) {
                    long lastRead = me.getLastRead(id);
                    ArrayList<Message> snap = c.getMessages();
                    this.currentConversationId = id;
                    return new ConversationOpenSnapshot(c, lastRead, snap);
                }
            }
        }
        return null;
    }

    /** Immutable open-time snapshot. {@link #messages} is a defensive copy — do not mutate. */
    public static final class ConversationOpenSnapshot {
        public final Conversation conversation;
        public final long lastReadAtOpen;
        public final ArrayList<Message> messages;
        ConversationOpenSnapshot(Conversation conversation, long lastReadAtOpen, ArrayList<Message> messages) {
            this.conversation = conversation;
            this.lastReadAtOpen = lastReadAtOpen;
            this.messages = messages;
        }
    }

    // -------------------------------------------------------------------------
    // Read/unread math — public statics delegate so existing call sites (UI
    // renderers, tests pinning ClientController.isUnread / etc.) keep compiling.
    // -------------------------------------------------------------------------

    private static final class ReadCalc {
        private ReadCalc() {}

        static boolean isUnread(Conversation conv, UserInfo viewer) {
            if (conv == null || viewer == null) return false;
            long lastReadSeq = viewer.getLastRead(conv.getConversationId());
            ArrayList<Message> msgs = conv.getMessages();
            long latestSeq = msgs.isEmpty() ? 0L : msgs.get(msgs.size() - 1).getSequenceNumber();
            return latestSeq > lastReadSeq;
        }

        static boolean isMessageUnread(Message msg, long displayedLastReadSeq) {
            if (msg == null) return false;
            return msg.getSequenceNumber() > displayedLastReadSeq;
        }

        /** O(unread) backward scan: relies on {@link Conversation#append} being the only
         *  mutator and appending in monotonic seq order. */
        static int unreadCount(Conversation conv, UserInfo viewer) {
            if (conv == null || viewer == null) return 0;
            long lastReadSeq = viewer.getLastRead(conv.getConversationId());
            ArrayList<Message> msgs = conv.getMessages();
            int count = 0;
            for (int i = msgs.size() - 1; i >= 0; i--) {
                if (msgs.get(i).getSequenceNumber() > lastReadSeq) count++;
                else break;
            }
            return count;
        }
    }

    public static boolean isUnread(Conversation conv, UserInfo viewer) {
        return ReadCalc.isUnread(conv, viewer);
    }

    public static boolean isMessageUnread(Message msg, long displayedLastReadSeq) {
        return ReadCalc.isMessageUnread(msg, displayedLastReadSeq);
    }

    public static int unreadCount(Conversation conv, UserInfo viewer) {
        return ReadCalc.unreadCount(conv, viewer);
    }

    /** Synchronized to prevent interleaved writes on the shared output stream. */
    private synchronized void sendRequest(Request r) {
        if (outputStream == null || connectionStatus != ConnectionStatus.CONNECTED) return;
        try {
            outputStream.writeObject(r);
            outputStream.flush();
            outputStream.reset(); // prevents the object-reference cache from leaking memory
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
                    connectionStatus = ConnectionStatus.NOT_CONNECTED;
                    if (authInFlight.compareAndSet(true, false)) {
                        // Drop the queued auth and notify, rather than silently retrying an
                        // unreachable server forever. EDT-marshal the GUI work.
                        SwingUtilities.invokeLater(() -> {
                            cancelAuthWatchdog();
                            if (gui != null) {
                                gui.setLoginInFlight(false);
                                gui.showNetworkError();
                            }
                        });
                    } else {
                        requestQueue.addFirst(r);
                    }
                    e.printStackTrace();
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS);
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

    private void startResponseListenerThread() {
        responseListenerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                while (!Thread.currentThread().isInterrupted()
                        && connectionStatus != ConnectionStatus.CONNECTED) {
                    try {
                        Thread.sleep(RECONNECT_POLL_MS);
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
        inactivityDetectorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                while (!Thread.currentThread().isInterrupted()
                        && connectionStatus != ConnectionStatus.CONNECTED) {
                    try {
                        Thread.sleep(RECONNECT_POLL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (Thread.currentThread().isInterrupted()) break;

                while (!Thread.currentThread().isInterrupted()
                        && connectionStatus == ConnectionStatus.CONNECTED) {
                    try {
                        Thread.sleep(PING_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    long idle = System.currentTimeMillis() - lastServerActivityMillis;
                    if (idle >= INACTIVITY_LIMIT_MS) {
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
