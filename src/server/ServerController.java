package server;

import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import shared.enums.ResponseType;
import shared.networking.ConnectionHandler;
import shared.networking.ConnectionListener;
import shared.networking.Request;
import shared.networking.Response;
import shared.networking.User.UserInfo;
import shared.payload.AddToConversationPayload;
import shared.payload.Conversation;
import shared.payload.Message;

public class ServerController {
    private Map<String, ConnectionHandler> activeSessions;
    private DataManager dataManager;
    private ConnectionListener connectionListener;
    private final LinkedBlockingQueue<Map.Entry<String, Response>> responseQueue;
    private Thread broadcasterThread;

    /*
     * Entry point for the server application.
     *
     * Usage: java ServerController [dataRootPath] [port]
     *
     *   dataRootPath - directory where persistent data (users, messages) is stored.
     *                  Defaults to "data" if not provided.
     *   port         - TCP port the server listens on for incoming client connections.
     *                  Defaults to 8080 if not provided.
     *                  Must be configurable so different environments (dev, staging, prod)
     *                  and multiple server instances can each bind to their own port.
     *
     * Example:
     *   java ServerController data 8080
     */
    public static void main(String[] args) {
        // First CLI arg is the data root path; port is fixed at 8080.
        String dataRootPath = args.length > 0 ? args[0] : "data";
        System.out.println("cwd is " + System.getProperty("user.dir"));
        ServerController serverController = new ServerController(dataRootPath, 8080);
        System.out.println("Server started on port 8080");
        keepAliveUntilInterrupted(serverController);
    }

    private static void keepAliveUntilInterrupted(ServerController serverController) {
        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            latch.countDown();
            serverController.close();
        }, "server-shutdown"));
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            serverController.close();
        }
    }

    /*
     * Production entry point — always starts the broadcaster thread.
     * Use this constructor everywhere outside of tests.
     */
    public ServerController(String dataRootPath, int port) {
        this(dataRootPath, port, true);
    }

    /**
     * Package-private: pass {@code startBroadcaster=false} in tests that need to
     * inspect the raw response queue without a draining thread racing against assertions.
     */
    ServerController(String dataRootPath, int port, boolean startBroadcaster) {
        this.activeSessions = new ConcurrentHashMap<>();
        this.dataManager = new DataManager(dataRootPath);
        this.connectionListener = new ConnectionListener(port, this);
        this.responseQueue = new LinkedBlockingQueue<>();
        if (startBroadcaster) startBroadcasterThread();
        startConnectionListenerThread();
    }

    public void close() {
        // 1. Stop accepting new connections first so no new responses are enqueued.
        if (connectionListener != null) {
            connectionListener.close();
        }
        // 2. Stop the broadcaster after the listener is closed.
        if (broadcasterThread != null) {
            broadcasterThread.interrupt();
        }
        // 3. Flush and close persistent storage.
        if (dataManager != null) {
            dataManager.close();
        }
        // 4. Close active client connections.
        for (ConnectionHandler handler : activeSessions.values()) {
            if (handler != null) {
                handler.close();
            }
        }
    }

    public Response processRequest(Request request) {
        if (request == null || request.getType() == null) return null;
        switch (request.getType()) {
            case REGISTER:
                return dataManager.handleRegister(request);
            case LOGIN:
                return dataManager.handleLogin(request);
            case MESSAGE: {
                Response response = dataManager.handleSendMessage(request);
                broadcastResponse(request, response);
                return response;
            }
            case UPDATE_READ_MESSAGES:
                return dataManager.handleUpdateReadMessages(request);
            case CREATE_CONVERSATION: {
                Response response = dataManager.handleCreateConversation(request);
                broadcastResponse(request, response);
                return response;
            }
            case ADD_PARTICIPANT: {
                Response response = dataManager.handleAddToConversation(request);
                broadcastResponse(request, response);
                return response;
            }
            case LEAVE_CONVERSATION:
                return dataManager.handleLeaveConversation(request);
            case ADMIN_CONVERSATION_QUERY:
                return dataManager.handleAdminConversationQuery(request);
            case JOIN_CONVERSATION:
                return dataManager.handleJoinConversation(request);
            case LOGOUT:
                removeSession(request.getSenderId());
                // Null is intentional: ConnectionHandler.handleSessionTransition owns LOGOUT
                // session cleanup and already guards against a null response before sending.
                return null;
            default:
                return null;
        }
    }

    private void broadcastResponse(Request request, Response response) {
        if (request == null || request.getType() == null || response == null) {
            return;
        }
        switch (request.getType()) {
            case MESSAGE:
                enqueueMessageBroadcast(request, response);
                break;
            case CREATE_CONVERSATION:
                enqueueCreateConversationBroadcast(response);
                break;
            case ADD_PARTICIPANT:
                enqueueAddParticipantBroadcast(request, response);
                break;
            default:
                break;
        }
    }

    private void enqueueToActiveParticipants(ArrayList<UserInfo> participants, Response response) {
        for (UserInfo participant : participants) {
            if (participant == null || participant.getUserId() == null) continue;
            String id = participant.getUserId();
            if (hasActiveSession(id)) {
                responseQueue.offer(new AbstractMap.SimpleImmutableEntry<>(id, response));
            }
        }
    }

    private void enqueueMessageBroadcast(Request request, Response response) {
        if (!(response.getPayload() instanceof Message)) return;
        Message message = (Message) response.getPayload();
        enqueueToActiveParticipants(dataManager.getParticipantList(message.getConversationId()), response);
    }

    private void enqueueCreateConversationBroadcast(Response response) {
        if (!(response.getPayload() instanceof Conversation)) return;
        Conversation conversation = (Conversation) response.getPayload();
        enqueueToActiveParticipants(dataManager.getParticipantList(conversation.getConversationId()), response);
    }

    private void enqueueAddParticipantBroadcast(Request request, Response response) {
        if (!(response.getPayload() instanceof Conversation)
                || !(request.getPayload() instanceof AddToConversationPayload)) {
            return;
        }
        Conversation conversation = (Conversation) response.getPayload();
        AddToConversationPayload payload = (AddToConversationPayload) request.getPayload();
        ArrayList<UserInfo> participantsToAdd = payload.getParticipants();
        Response metadataResponse = new Response(ResponseType.CONVERSATION_METADATA, conversation.toMetadata());
        ArrayList<UserInfo> existingParticipants = new ArrayList<>();
        for (UserInfo participant : conversation.getParticipants()) {
            if (participant == null || participant.getUserId() == null) continue;
            String id = participant.getUserId();
            boolean isAddedParticipant = false;
            if (participantsToAdd != null) {
                for (UserInfo added : participantsToAdd) {
                    if (added != null && id.equals(added.getUserId())) {
                        isAddedParticipant = true;
                        break;
                    }
                }
            }
            if (!isAddedParticipant) {
                existingParticipants.add(participant);
            }
        }
        enqueueToActiveParticipants(existingParticipants, metadataResponse);
        if (participantsToAdd != null) {
            enqueueToActiveParticipants(participantsToAdd, response);
        }
    }

    private void startConnectionListenerThread() {
        Thread t = new Thread(() -> connectionListener.listen(), "server-listener");
        t.setDaemon(true);
        t.start();
    }

    private void startBroadcasterThread() {
        broadcasterThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Map.Entry<String, Response> queued = responseQueue.take();
                    ConnectionHandler handler = activeSessions.get(queued.getKey());
                    if (handler != null) {
                        handler.sendResponse(queued.getValue());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "server-broadcast");
        broadcasterThread.setDaemon(true);
        broadcasterThread.start();
    }

    public boolean hasActiveSession(String userId) {
        return activeSessions.containsKey(userId);
    }

    public void removeSession(String userId) {
        activeSessions.remove(userId);
    }

    public void addSession(String userId, ConnectionHandler handler) {
        activeSessions.put(userId, handler);
    }
}
