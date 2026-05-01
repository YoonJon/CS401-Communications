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
     * Reassigned off-EDT by the response listener ({@link #handleReadMessagesUpdatedResponse}
     * builds a fresh {@link UserInfo}, populates its {@code lastRead} map pre-publication, then
     * publishes the new reference). After publication, the {@code lastRead} map is mutated only
     * on the EDT — see the {@link UserInfo} contract. The response thread reads the reference
     * (e.g. to gate read-advance in {@link #handleMessageResponse}) and schedules a
     * {@code SwingUtilities.invokeLater} for any actual mutation. {@code volatile} ensures
     * readers observe the freshest reference.
     */
    private volatile UserInfo currentUser;
    /** True while a LOGIN or REGISTER request is enqueued and unresolved. Atomic so rapid
     *  duplicate clicks lose the {@code compareAndSet} race instead of both passing a
     *  non-atomic {@code if (!flag) flag = true}. */
    private final AtomicBoolean authInFlight = new AtomicBoolean(false);
    /** EDT-only Swing timer that resets {@link #authInFlight} and surfaces a network error if
     *  the server never replies (crash-after-receive, hung handler). Cancelled on auth resolve. */
    private javax.swing.Timer authWatchdogTimer;
    private static final int AUTH_WATCHDOG_MS = 15_000;
    /** #139: cached on register() so handleRegisterResultResponse can pre-fill the login screen. */
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
    // Window-focus gate (Fix A): inbound messages while the OS window is inactive
    // must NOT advance the read pointer — the user hasn't actually seen them.
    // We buffer the per-conversation max sequence and replay on activation,
    // coalescing a burst of N messages into a single UPDATE_READ_MESSAGES.
    // -------------------------------------------------------------------------

    /** True while the JFrame is the active OS window. Defaults true so headless
     *  tests (no window-activation events) and pre-window-event states do not
     *  suppress reads. */
    private final AtomicBoolean windowActive = new AtomicBoolean(true);
    /**
     * Conversation id → highest seq of an inbound message that arrived while
     * {@code windowActive=false}. Drained by {@link #replayReadAdvanceIfNeeded()}
     * on window activation.
     * <p>Concurrency: writes serialize via the {@code conversations} monitor (the
     * response listener writes inside {@link #handleMessageResponse}'s monitor;
     * {@link #tryAdvanceReadOnInbound} writes under the same monitor; the EDT
     * {@link #replayReadAdvanceIfNeeded} drain re-acquires that monitor for the
     * atomic drain-and-clear). Reads outside the monitor are unsafe.
     */
    private final HashMap<Long, Long> deferredReadAdvance = new HashMap<>();

    // -------------------------------------------------------------------------
    // Server path liveness (updated on inbound server-driven signals, e.g. PONG)
    // -------------------------------------------------------------------------

    private volatile long lastServerActivityMillis = System.currentTimeMillis();

    // -------------------------------------------------------------------------
    // Tunables
    // -------------------------------------------------------------------------

    /** #138: bounded TCP connect timeout — unreachable server fails fast (~7s) instead of
     *  the OS default (~75s on most platforms). */
    private static final int CONNECT_TIMEOUT_MS = 7000;
    /** Send a PING this often while connected to keep NAT/firewall paths warm. */
    private static final long PING_INTERVAL_MS = 30_000L;
    /** No server activity for this long → assume the server is gone and force-logout. */
    private static final long INACTIVITY_LIMIT_MS = 300_000L;
    /** Polling interval the listener and inactivity threads use while waiting for the next
     *  successful reconnect. */
    private static final long RECONNECT_POLL_MS = 500L;
    /** Backoff after a failed connect/send in the drain thread before retrying. */
    private static final long RETRY_BACKOFF_MS = 300L;

    /*
     * Entry point for the client application.
     *
     * Usage: java ClientController [host] [port]
     *
     *   host - IP address or hostname of the server to connect to.
     *          Defaults to "localhost" if not provided.
     *   port - TCP port the server is listening on.
     *          Defaults to 8080 if not provided.
     *          Must match the port the server was started with.
     *          Configurable so clients can reach servers on different ports
     *          across environments (dev, staging, prod).
     *
     * Example:
     *   java ClientController 192.168.1.10 8080
     */
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

    /** Package-private test seam: accepts a pre-built (or null) GUI; no Swing window opened. */
    ClientController(String hostIp, int hostPort, ClientUI guiOverride) {
        this.hostIp = hostIp;
        this.hostPort = hostPort;
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
     * {@link ConnectionStatus#NOT_CONNECTED}. Does <b>not</b> interrupt the request drain,
     * response listener, or inactivity threads — they keep running and can wait for the next
     * lazy reconnect. For full shutdown use {@link #close()}; for logout, use
     * {@link #logout()} which sends LOGOUT off-thread before tearing down the streams.
     * Field swaps happen under {@code synchronized(this)} so concurrent {@link #ensureConnected}
     * / {@link #sendRequest} calls observe a consistent (out, in, socket) triple.
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

    /** Package-private so tests can drive response handling directly without a live socket. */
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
                // lastServerActivityMillis is already updated for every response above.
                break;
            default:
                break;
        }
    }

    private void handleLoginResultResponse(Response response) {
        LoginResult lr = (LoginResult) response.getPayload();
        // Coalesce field reset + UI updates into a single EDT task so the in-flight flag and
        // visible button state can never be observed out-of-order with each other.
        SwingUtilities.invokeLater(() -> {
            authInFlight.set(false);
            cancelAuthWatchdog();
            if (gui != null) gui.setLoginInFlight(false);
            switch (lr.getLoginStatus()) {
                case SUCCESS:
                    loggedIn = true;
                    currentUser = lr.getUserInfo();
                    // #214: defense-in-depth — server now sorts the wire payload, but
                    // copy + re-sort here so the controller's invariant holds for any
                    // direct consumer of `conversations` (tests, future callers).
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
        // Move the matching conversation to front (most recent), then refresh the list view.
        Message msg = (Message) response.getPayload();
        ArrayList<Conversation> snapshot;
        boolean isCurrentConv;
        boolean shouldAdvanceLastRead = false;
        // The lastRead-advance decision must happen under the same monitor that
        // openConversationAtomically uses, so a click-to-open never captures a lastRead
        // that an in-flight inbound has just bumped past the new message. The actual
        // setLastRead mutation is deferred to the EDT to honour the UserInfo contract;
        // EDT preserves submission order, and openConversationAtomically also runs on
        // the EDT, so the per-conversation pointer remains monotonic.
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
     * Inbound message arrived in the open conversation. The composed gate is
     * {@code windowActive && userIsViewingBottom}: if the OS window is active AND the user
     * is parked at the bottom of the message list (Fix E1), optimistically advance
     * {@code currentUser.lastRead} and ask the server to persist. Otherwise buffer the
     * per-conversation max seq for replay on the next window activation OR scroll-to-bottom
     * transition (whichever comes first).
     * <p>Package-private seam so unit tests (with {@code gui == null}) can exercise the
     * read-advance contract directly. Headless callers (gui == null) implicitly satisfy
     * the bottom-viewing predicate so existing tests need not stage a scroll position.
     * <p>NOT on the live path: {@link #handleMessageResponse} inlines the same decision
     * under the {@code conversations} monitor to serialize with
     * {@link #openConversationAtomically}. Keep the two implementations in sync.
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

    /** Called by {@link ClientUI}'s WindowAdapter on activate/deactivate. */
    public void setWindowActive(boolean active) {
        windowActive.set(active);
    }

    /** True iff the OS window is currently active. Exposed for tests and gating callers
     *  (e.g. {@link ClientUI#appendMessageToConversationView}). */
    public boolean isWindowActive() {
        return windowActive.get();
    }

    /**
     * Drain {@link #deferredReadAdvance}: for each conversation, send one
     * UPDATE_READ_MESSAGES at the highest deferred seq, advance local lastRead,
     * and re-sync the open conversation's per-message snapshot. Called on window
     * activation. Idempotent on an empty buffer.
     * <p>The drain-and-clear runs under {@code synchronized(conversations)} so it
     * cannot race with a concurrent inbound that {@code merge}s into the same map
     * inside {@link #handleMessageResponse}'s monitor.
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
        // Move to front regardless of whether it is new or an update (recency sort).
        // Track whether this conversation was already known so we can auto-open new ones.
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
                // Auto-open the newly created conversation in the center panel.
                setCurrentConversationId(conv.getConversationId());
                SwingUtilities.invokeLater(() -> gui.updateMessageListModel(conv));
                // TODO: call gui.selectConversationInList(conv) once that method exists on ClientUI
            }
        }
    }

    private void handleConversationMetadataResponse(Response response) {
        ConversationMetadata meta = (ConversationMetadata) response.getPayload();
        // TODO(meta-merge): meta is currently never merged into the matching Conversation —
        // either fold participant updates from `meta` into the in-cache Conversation, or
        // delete this handler if the server-side emitter has been removed.
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
        // EDT-marshal: currentDirectory is read on EDT (search filter, directory render);
        // mutating it on the response thread would race with EDT iteration.
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

            // If we just left the current conversation, switch to the most recent one
            if (currentConversationId == leftId) {
                currentConversationId = conversations.isEmpty() ? -1 : conversations.get(0).getConversationId();
            }
            snapshot = new ArrayList<>(conversations);
        }
        if (gui != null) {
            Conversation current = getCurrentConversation();
            // Update conversation list (sidebar)
            gui.updateConversationListModel(snapshot);
            // Update conversation view (message panel) to show the new current conversation
            gui.updateMessageListModel(current);
        }
    }

    private void handleReadMessagesUpdatedResponse(Response response) {
        ReadMessagesUpdated updated = (ReadMessagesUpdated) response.getPayload();
        if (updated == null || updated.getUpdatedUserInfo() == null) return;
        UserInfo fresh = updated.getUpdatedUserInfo();
        UserInfo old = currentUser;
        if (old != null) {
            // #209: preserve any local optimistic advance the server has not yet acked.
            // getLastReadMap() returns an unmodifiableMap view of the live HashMap; the
            // defensive copy below is load-bearing — without it, a concurrent EDT writer
            // can throw ConcurrentModificationException during iteration.
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
        // AdminConversationResult carries ConversationMetadata — store it directly.
        AdminConversationResult acr = (AdminConversationResult) response.getPayload();
        // EDT-marshal: currentAdminConversationSearch is read on the EDT (admin dialog
        // filter, model update). Reassigning the reference on the response thread races
        // EDT iteration in getFilteredAdminConversationSearch.
        SwingUtilities.invokeLater(() -> {
            currentAdminConversationSearch = new ArrayList<>(acr.getConversations());
            if (gui != null) {
                gui.updateAdminConversationSearchModel(currentAdminConversationSearch);
            }
        });
    }

    /** Routes the admin's silent read into the open admin viewer panel. Does not touch
     *  {@code conversations} or {@code currentConversationId}, so the viewed conversation
     *  never enters the admin's own sidebar. */
    private void handleAdminViewConversationResultResponse(Response response) {
        Object payload = response.getPayload();
        if (gui != null && payload instanceof Conversation) {
            gui.showAdminConversationView((Conversation) payload);
        }
    }

    /** Lazily opens a TCP connection to the server and initializes the input and output streams.
     *  Synchronized so the state field flip and stream creation happen as one block — concurrent
     *  callers either skip out at the CONNECTED check or wait their turn. */
    private synchronized void ensureConnected() throws IOException {
        if (connectionStatus == ConnectionStatus.CONNECTED) return;
        socket = new Socket();
        socket.connect(new InetSocketAddress(hostIp, hostPort), CONNECT_TIMEOUT_MS);
        // OOS must be created and flushed BEFORE OIS on both sides to avoid header deadlock
        // (mirrors the convention in ConnectionHandler on the server).
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush();
        inputStream = new ObjectInputStream(socket.getInputStream());
        connectionStatus = ConnectionStatus.CONNECTED;
        lastServerActivityMillis = System.currentTimeMillis();
    }

    /** Captures {@link #currentUser} once and returns it iff a session is active. Any caller
     *  that derefs {@code currentUser} more than once should go through this helper to avoid
     *  the volatile reference flipping to null mid-method. */
    private UserInfo requireSession() {
        UserInfo me = currentUser;
        return (loggedIn && me != null) ? me : null;
    }

    /** Same contract as {@link #requireSession()} but additionally requires admin privileges. */
    private UserInfo requireAdminSession() {
        UserInfo me = requireSession();
        return (me != null && me.getUserType() == UserType.ADMIN) ? me : null;
    }

    // -------------------------------------------------------------------------
    // Public action methods — each maps 1-to-1 with a DataManager handler.
    // -------------------------------------------------------------------------

    /** Matches DataManager.handleRegister — payload: RegisterCredentials(userId, loginName, password, name). */
    public void register(String userId, String realName, String loginName, char[] password) {
        if (!authInFlight.compareAndSet(false, true)) return; // drop rapid duplicate clicks
        lastRegisteredLoginName = loginName;
        if (gui != null) gui.setLoginInFlight(true);
        startAuthWatchdog();
        RegisterCredentials creds = new RegisterCredentials(userId, loginName, new String(password), realName);
        enqueueRequest(new Request(RequestType.REGISTER, creds, null));
    }

    /** Matches DataManager.handleLogin — payload: LoginCredentials(loginName, password). */
    public void login(String loginName, char[] password) {
        if (!authInFlight.compareAndSet(false, true)) return; // drop rapid duplicate clicks
        if (gui != null) gui.setLoginInFlight(true);
        startAuthWatchdog();
        LoginCredentials creds = new LoginCredentials(loginName, new String(password));
        enqueueRequest(new Request(RequestType.LOGIN, creds, null));
    }

    /** EDT-only. Replaces any prior watchdog with a single-shot timer that fires after
     *  {@link #AUTH_WATCHDOG_MS} if the server never responds, freeing the login button. */
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

    /** EDT-only. Stops the watchdog if running. */
    private void cancelAuthWatchdog() {
        if (authWatchdogTimer != null) {
            authWatchdogTimer.stop();
            authWatchdogTimer = null;
        }
    }

    /**
     * Clears local session synchronously (lists, queue, flags) and switches to the login view.
     * The wire LOGOUT and stream teardown run on a daemon helper so the EDT does not block on
     * socket I/O. Session tracking lives outside {@code DataManager}.
     * <p>
     * Ordering: we clear {@link #requestQueue} <i>before</i> capturing/nulling the streams so any
     * request the drain thread already pulled with {@code take()} will, on its next
     * {@link #sendRequest(Request)} call, observe {@code outputStream == null} and drop silently.
     * The captured stream refs are local to the helper, so a subsequent re-login can rebuild a
     * fresh connection via {@link #ensureConnected()} without racing the in-flight LOGOUT write.
     */
    public void logout() {
        UserInfo me = requireSession();
        if (me == null) return;
        String userId = me.getUserId();
        // Clear queue first; drained-but-not-yet-sent requests will observe NOT_CONNECTED below.
        requestQueue.clear();
        // Capture-and-null the streams under `this` so concurrent ensureConnected/sendRequest
        // see a consistent (out, in, socket) triple. The captured triple is handed to the helper.
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

    /** Off-EDT helper: best-effort LOGOUT write on the captured stream, then close all three. */
    private static void closeWithLogout(String userId, boolean wasConnected,
                                        ObjectOutputStream out, ObjectInputStream in, Socket sock) {
        if (wasConnected && out != null) {
            try {
                out.writeObject(new Request(RequestType.LOGOUT, null, userId));
                out.flush();
            } catch (IOException ignored) { /* socket already gone — close path below handles it */ }
        }
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (sock != null && !sock.isClosed()) sock.close(); } catch (IOException ignored) {}
    }

    /** #55/#124: read the cached admin search results so the dialog can seed itself on open
     *  even if the response arrived before the dialog finished constructing. Defensive copy:
     *  the live list is reassigned + cleared on the EDT, so callers must not retain a reference. */
    public ArrayList<ConversationMetadata> getCurrentAdminConversationSearch() {
        return new ArrayList<>(currentAdminConversationSearch);
    }

    /** #128: clear the cached admin search results so reopening the dialog starts fresh. */
    public void clearAdminConversationSearch() {
        currentAdminConversationSearch.clear();
    }

    /** Matches DataManager.handleSendMessage — payload: RawMessage(text, conversationId). */
    public void sendMessage(long conversationId, String m) {
        UserInfo me = requireSession();
        if (me == null) return;
        enqueueRequest(new Request(RequestType.MESSAGE,
                new RawMessage(m, conversationId), me.getUserId()));
    }

    /** Matches DataManager.handleCreateConversation — payload: CreateConversationPayload(participants). */
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

    /** Matches DataManager.handleAddToConversation — payload: AddToConversationPayload(participants, conversationId). */
    public void addToConversation(ArrayList<UserInfo> p, long conversationId) {
        UserInfo me = requireSession();
        if (me == null) return;
        enqueueRequest(new Request(RequestType.ADD_PARTICIPANT,
                new AddToConversationPayload(p, conversationId), me.getUserId()));
    }

    /** Matches DataManager.handleLeaveConversation — payload: LeaveConversationPayload(conversationId). */
    public void leaveConversation(long conversationId) {
        UserInfo me = requireSession();
        if (me == null) return;
        enqueueRequest(new Request(RequestType.LEAVE_CONVERSATION,
                new LeaveConversationPayload(conversationId), me.getUserId()));
    }

    /** Matches DataManager.handleUpdateReadMessages — payload: UpdateReadMessages(conversationId, lastSeenSequenceNumber). */
    public void updateReadMessages(long conversationId, long lastSeenSequenceNumber) {
        UserInfo me = requireSession();
        if (me == null) return;
        enqueueRequest(new Request(
                RequestType.UPDATE_READ_MESSAGES,
                new UpdateReadMessages(conversationId, lastSeenSequenceNumber),
                me.getUserId()));
    }

    /** Matches DataManager.handleAdminConversationQuery — payload: AdminConversationQuery(userID). */
    public void adminGetUserConversations(String userID) {
        UserInfo me = requireAdminSession();
        if (me == null) return;
        enqueueRequest(new Request(RequestType.ADMIN_CONVERSATION_QUERY,
                new AdminConversationQuery(userID), me.getUserId()));
    }

    /** Matches DataManager.handleAdminViewConversation — payload: AdminViewConversationQuery(conversationId).
     *  Pulls a full read-only snapshot for the admin viewer; server does NOT mutate the
     *  participant list, so other clients are unaware of the lookup. */
    public void adminViewConversation(long conversationId) {
        UserInfo me = requireAdminSession();
        if (me == null) return;
        enqueueRequest(new Request(RequestType.ADMIN_VIEW_CONVERSATION,
                new AdminViewConversationQuery(conversationId), me.getUserId()));
    }

    /** Matches DataManager.handleJoinConversation — payload: JoinConversationPayload(conversationId). */
    public void joinConversation(long conversationId) {
        if (!loggedIn || currentUser == null || currentUser.getUserType() != UserType.ADMIN) return;
        enqueueRequest(new Request(RequestType.JOIN_CONVERSATION,
                new JoinConversationPayload(conversationId), currentUser.getUserId()));
    }

    // -------------------------------------------------------------------------
    // UI/local filtering + read-only accessors.
    // -------------------------------------------------------------------------

    /** Filters local directory and refreshes the directory list model in the GUI. */
    public void searchDirectory(String query) {
        if (gui == null) return;
        gui.updateDirectoryModel(getFilteredDirectory(query));
    }

    /** Filters local conversation list and refreshes the conversation list model in the GUI. */
    public void searchConversationList(String query) {
        if (gui == null) return;
        gui.updateConversationListModel(getFilteredConversationList(query));
    }

    public UserInfo getCurrentUserInfo() { return currentUser; }
    public boolean isLoggedIn()           { return loggedIn; }

    /** Package-private: seeds the directory list for unit tests without a live server. */
    void setCurrentDirectoryForTesting(ArrayList<UserInfo> dir) {
        this.currentDirectory = new ArrayList<>(dir);
    }

    /** Package-private: outbound request queue depth — used by unit tests to verify whether
     *  optimization paths (e.g. {@link #replayReadAdvanceIfNeeded()} and
     *  {@link #handleMessageResponse(shared.networking.Response)}) did or did not enqueue a request. */
    int requestQueueDepthForTesting() {
        return requestQueue.size();
    }

    /** Package-private: most recently enqueued request, or {@code null} if empty. */
    Request peekLastRequestForTesting() {
        return requestQueue.peekLast();
    }

    /** Returns all users whose id or name contains {@code query} (case-insensitive). */
    public ArrayList<UserInfo> getFilteredDirectory(String query) {
        if (query == null || query.isBlank()) return new ArrayList<>(currentDirectory);
        ArrayList<UserInfo> filtered = new ArrayList<>();
        String q = query.toLowerCase();
        for (UserInfo u : currentDirectory) {
            if (userMatches(u, q)) filtered.add(u);
        }
        return filtered;
    }

    /** Returns conversations where any participant's id or name matches {@code query}. */
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
            // Empty conversations have no message sequence yet; use conversationId as a
            // creation-recency fallback.
            return c.getConversationId();
        }
        return c.getMessages().get(c.getMessages().size() - 1).getSequenceNumber();
    }

    /** Returns admin search results filtered by participant id or name,
     *  matching both active and historical participants so orphaned
     *  conversations (everyone has left) remain searchable. */
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

    /** Case-insensitive substring match on userId OR name. {@code lowerQuery} is assumed
     *  pre-lowercased — do this once at the call site, not per-element. */
    private static boolean userMatches(UserInfo u, String lowerQuery) {
        if (u == null) return false;
        return u.getUserId().toLowerCase().contains(lowerQuery)
                || u.getName().toLowerCase().contains(lowerQuery);
    }

    /** True iff any user in {@code people} matches {@code lowerQuery} per {@link #userMatches}. */
    private static boolean anyUserMatches(Collection<UserInfo> people, String lowerQuery) {
        if (people == null) return false;
        for (UserInfo u : people) {
            if (userMatches(u, lowerQuery)) return true;
        }
        return false;
    }

    public void setCurrentConversationId(long conversationId) { this.currentConversationId = conversationId; }
    public long getCurrentConversationId() { return currentConversationId; }

    /** Returns the full {@link Conversation} object for the currently selected conversation, or null. */
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
     * Fix E2: atomically capture the triple (conversation reference, viewer's lastRead at open
     * time, defensive message-list snapshot) under the {@code conversations} monitor AND publish
     * {@code currentConversationId} in the same critical section. Closes the race between the
     * click-to-open handler reading {@code lastRead} and {@code Conversation.append} adding a
     * new message before {@link ConversationView#setListModel} consumes the message list: without
     * atomic capture, the freshly-arrived message could land in the wrong half of the
     * "New messages" divider boundary.
     * <p>Returns null if no user is logged in or the conversation is unknown.
     * <p>Lock order: {@code conversations} → {@code Conversation.this} (via {@link Conversation#getMessages()}).
     * This matches {@link #handleMessageResponse} which acquires {@code conversations} then {@code Conversation.append}
     * in the same order, so deadlock is impossible.
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

    /**
     * Atomically-captured triple used by the click-to-open path. Immutable; callers must not
     * mutate {@link #messages} (it's a defensive copy from {@link Conversation#getMessages()}).
     */
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
    // Read/unread math — pure functions, grouped under ReadCalc to signal intent.
    // The public statics on ClientController are one-line delegates so existing
    // call sites (UI renderers, tests pinning ClientController.isUnread / etc.)
    // continue to compile.
    // -------------------------------------------------------------------------

    /** Pure read-state calculations. Stateless; no instance fields. */
    private static final class ReadCalc {
        private ReadCalc() {}

        /** True iff {@code viewer}'s last-read pointer trails the latest message sequence in {@code conv}. */
        static boolean isUnread(Conversation conv, UserInfo viewer) {
            if (conv == null || viewer == null) return false;
            long lastReadSeq = viewer.getLastRead(conv.getConversationId());
            ArrayList<Message> msgs = conv.getMessages();
            long latestSeq = msgs.isEmpty() ? 0L : msgs.get(msgs.size() - 1).getSequenceNumber();
            return latestSeq > lastReadSeq;
        }

        /** True iff {@code msg}'s sequence number is strictly above {@code displayedLastReadSeq}. */
        static boolean isMessageUnread(Message msg, long displayedLastReadSeq) {
            if (msg == null) return false;
            return msg.getSequenceNumber() > displayedLastReadSeq;
        }

        /** O(unread) backward scan; stops at the first read message. Relies on
         *  {@link Conversation#append} being the only mutator and appending in monotonic seq order. */
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

    /** Used by the conversation-list cell renderer to decide whether to bold a row and prepend
     *  the unread glyph. Returns false if either argument is null. */
    public static boolean isUnread(Conversation conv, UserInfo viewer) {
        return ReadCalc.isUnread(conv, viewer);
    }

    /** Per-message dot predicate paired with the open-conversation snapshot so the renderer
     *  and sidebar stay aligned. Returns false if {@code msg} is null. */
    public static boolean isMessageUnread(Message msg, long displayedLastReadSeq) {
        return ReadCalc.isMessageUnread(msg, displayedLastReadSeq);
    }

    /** Count of messages in {@code conv} with sequence above {@code viewer}'s last-read pointer.
     *  Returns 0 if either argument is null, the conversation is empty, or already caught up. */
    public static int unreadCount(Conversation conv, UserInfo viewer) {
        return ReadCalc.unreadCount(conv, viewer);
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
                    sendRequest(r);
                } catch (IOException e) {
                    connectionStatus = ConnectionStatus.NOT_CONNECTED;
                    if (authInFlight.compareAndSet(true, false)) {
                        // #138: drop the queued auth request and notify the user instead of
                        // silently retrying the unreachable server forever. EDT-marshal the
                        // GUI updates and watchdog-cancel since this is the drain thread.
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

    /** Waits for TCP, then reads {@link Response} objects and dispatches them. */
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
