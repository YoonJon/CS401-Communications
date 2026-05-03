package client;

import java.io.Closeable;
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

    private final ClientUI gui;
    private final String hostIp;
    private final int hostPort;

    private volatile ConnectionStatus connectionStatus = ConnectionStatus.NOT_CONNECTED;
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    private final LinkedBlockingDeque<Request> requestQueue = new LinkedBlockingDeque<>();
    private Thread requestDrainThread;
    private Thread responseListenerThread;
    private Thread inactivityDetectorThread;

    private volatile boolean loggedIn = false;
    /**
     * Reassigned off-EDT (response listener publishes a fresh instance with lastRead pre-populated);
     * after publication, the lastRead map is mutated only on the EDT. volatile ensures readers
     * observe the freshest reference.
     */
    private volatile UserInfo currentUser;
    /** Atomic so rapid duplicate clicks lose the compareAndSet race rather than both passing
     *  a non-atomic {@code if (!flag) flag = true}. */
    private final AtomicBoolean authInFlight = new AtomicBoolean(false);
    /** EDT-only single-shot timer that surfaces a network error if the server never replies. */
    private javax.swing.Timer authWatchdogTimer;
    private static final int AUTH_WATCHDOG_MS = 15_000;
    private volatile String lastRegisteredLoginName = null;

    private final List<Conversation> conversations = Collections.synchronizedList(new ArrayList<>());
    /** -1 means no conversation selected. */
    private volatile long currentConversationId = -1;
    private ArrayList<UserInfo> currentDirectory = new ArrayList<>();
    private ArrayList<ConversationMetadata> currentAdminConversationSearch = new ArrayList<>();

    /** Defaults true so headless tests and pre-window-event states do not suppress reads. */
    private final AtomicBoolean windowActive = new AtomicBoolean(true);
    /**
     * Mirrors whether {@code messageInputField} owns the focus. Drives the read-ack predicate
     * (replaces {@code windowActive} for that purpose). Defaults true for headless tests and
     * pre-attach states. Written from the EDT inside {@link ClientUI}'s FocusListener; read
     * off-EDT in {@link #handleMessageResponse}.
     */
    private final AtomicBoolean inputFocused = new AtomicBoolean(true);
    /**
     * Conversation id → highest seq of an inbound message that arrived while the input was
     * unfocused (or the user was scrolled up).
     * <p>Concurrency: writes and the drain-and-clear all serialize via the {@code conversations}
     * monitor. Reads outside that monitor are unsafe.
     */
    private final HashMap<Long, Long> deferredReadAdvance = new HashMap<>();

    private volatile long lastServerActivityMillis = System.currentTimeMillis();

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

    /** Field swaps under {@code synchronized(this)} so concurrent ensureConnected/sendRequest
     *  observe a consistent (out, in, socket) triple. Threads stay alive for lazy reconnect. */
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
        closeQuietly(in, out, sock);
    }

    private static void closeQuietly(Closeable... streams) {
        for (Closeable s : streams) {
            if (s != null) try { s.close(); } catch (IOException ignored) {}
        }
    }

    /** Test seam: drive response handling directly without a live socket. */
    void processResponse(Response response) {
        if (response == null) return;
        lastServerActivityMillis = System.currentTimeMillis();
        switch (response.getType()) {
            case LOGIN_RESULT:                   handleLoginResultResponse(response); break;
            case REGISTER_RESULT:                handleRegisterResultResponse(response); break;
            case MESSAGE:                        handleMessageResponse(response); break;
            case CONVERSATION:                   handleConversationResponse(response); break;
            case LEAVE_RESULT:                   handleLeaveResultResponse(response); break;
            case READ_MESSAGES_UPDATED:          handleReadMessagesUpdatedResponse(response); break;
            case ADMIN_CONVERSATION_RESULT:      handleAdminConversationResultResponse(response); break;
            case ADMIN_VIEW_CONVERSATION_RESULT: handleAdminViewConversationResultResponse(response); break;
            case USER_CREATION:                  handleUserCreationResponse(response); break;
            case CONNECTED:                      connectionStatus = ConnectionStatus.CONNECTED; break;
            case PONG:                           break;
            default:                             break;
        }
    }

    private void handleLoginResultResponse(Response response) {
        LoginResult lr = (LoginResult) response.getPayload();
        SwingUtilities.invokeLater(() -> {
            authInFlight.set(false);
            cancelAuthWatchdog();
            if (gui != null) gui.setLoginInFlight(false);
            switch (lr.getLoginStatus()) {
                case SUCCESS:
                    loggedIn = true;
                    currentUser = lr.getUserInfo();
                    synchronized (conversations) {
                        conversations.clear();
                        if (lr.getConversationList() != null) {
                            conversations.addAll(lr.getConversationList());
                        }
                    }
                    currentDirectory = lr.getDirectoryUserInfoList() != null
                            ? lr.getDirectoryUserInfoList() : new ArrayList<>();
                    if (gui != null) gui.showMainView();
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
        // setLastRead is deferred to the EDT (per UserInfo's contract); EDT preserves submission
        // order, so the per-conversation pointer stays monotonic.
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
                // Sending is itself a read-ack. Echoes of the user's own outbound message
                // must advance lastRead unconditionally — focus may be on the Send button
                // (not the input) by the time the echo arrives, which would otherwise defer
                // the advance and let the user's own message inflate their own unread badge.
                boolean fromSelf = msg.getSenderId() != null
                        && msg.getSenderId().equals(currentUser.getUserId());
                boolean userViewingBottom = (gui == null) || gui.userIsViewingBottom();
                if (fromSelf || (inputFocused.get() && userViewingBottom)) {
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

    public void setWindowActive(boolean active) {
        windowActive.set(active);
    }

    public boolean isWindowActive() {
        return windowActive.get();
    }

    public void setInputFocused(boolean focused) {
        inputFocused.set(focused);
    }

    public boolean isInputFocused() {
        return inputFocused.get();
    }

    /** Drain-and-clear runs under {@code synchronized(conversations)} so it cannot race a
     *  concurrent inbound merging into the same map. Idempotent on an empty buffer. */
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
            }
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
            // Preserve any local optimistic advance the server has not yet acked. Defensive copy
            // is load-bearing: getLastReadMap() returns an unmodifiable view of the live HashMap,
            // so iterating it raw races a concurrent EDT writer.
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

    /** Synchronized so the state flip and stream creation happen atomically. */
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

    /** Clears local session synchronously and switches to the login view. The wire LOGOUT and
     *  stream teardown run on a daemon helper so the EDT never blocks on socket I/O. The queue
     *  is cleared BEFORE nulling streams so any request the drain thread already took observes
     *  outputStream==null on its next sendRequest and drops silently. */
    public void logout() {
        UserInfo me = requireSession();
        if (me == null) return;
        String userId = me.getUserId();
        requestQueue.clear();
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
        inputFocused.set(true);
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
        closeQuietly(in, out, sock);
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
            // Fall back to conversationId for creation-recency ordering.
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

    /** Atomically capture (conversation, viewer's lastRead at open time, message-list snapshot)
     *  AND publish currentConversationId in one critical section, so a message arriving between
     *  the lastRead read and the snapshot cannot land on the wrong side of the divider.
     *  Lock order: conversations → Conversation.this — matches handleMessageResponse. */
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

    public static boolean isUnread(Conversation conv, UserInfo viewer) {
        if (conv == null || viewer == null) return false;
        long lastReadSeq = viewer.getLastRead(conv.getConversationId());
        ArrayList<Message> msgs = conv.getMessages();
        long latestSeq = msgs.isEmpty() ? 0L : msgs.get(msgs.size() - 1).getSequenceNumber();
        return latestSeq > lastReadSeq;
    }

    public static boolean isMessageUnread(Message msg, long displayedLastReadSeq) {
        if (msg == null) return false;
        return msg.getSequenceNumber() > displayedLastReadSeq;
    }

    /** O(unread) backward scan: relies on Conversation#append being the only mutator and
     *  appending in monotonic seq order. */
    public static int unreadCount(Conversation conv, UserInfo viewer) {
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

    private void awaitConnected() {
        while (!Thread.currentThread().isInterrupted()
                && connectionStatus != ConnectionStatus.CONNECTED) {
            try {
                Thread.sleep(RECONNECT_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void attemptSendOrBackoff(Request r) {
        try {
            ensureConnected();
            sendRequest(r);
        } catch (IOException e) {
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
            if (authInFlight.compareAndSet(true, false)) {
                // Drop the queued auth and notify rather than silently retrying an unreachable
                // server forever. EDT-marshal the GUI work.
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
            }
        }
    }

    private void startRequestDrainThread() {
        requestDrainThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Request r;
                try {
                    r = requestQueue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                attemptSendOrBackoff(r);
            }
        }, "request-drain");
        requestDrainThread.setDaemon(true);
        requestDrainThread.start();
    }

    private void startResponseListenerThread() {
        responseListenerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                awaitConnected();
                if (Thread.currentThread().isInterrupted()) break;

                while (!Thread.currentThread().isInterrupted()
                        && connectionStatus == ConnectionStatus.CONNECTED && inputStream != null) {
                    try {
                        Object obj = inputStream.readObject();
                        if (obj instanceof Response) {
                            processResponse((Response) obj);
                        }
                    } catch (EOFException | SocketException e) {
                        connectionStatus = ConnectionStatus.NOT_CONNECTED;
                        break;
                    } catch (IOException | ClassNotFoundException e) {
                        connectionStatus = ConnectionStatus.NOT_CONNECTED;
                        e.printStackTrace();
                        break;
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
                awaitConnected();
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
