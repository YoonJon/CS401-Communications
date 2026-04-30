package server;

import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import shared.enums.RequestType;
import shared.enums.RegisterStatus;
import shared.enums.ResponseType;
import shared.networking.ConnectionHandler;
import shared.networking.ConnectionListener;
import shared.networking.Request;
import shared.networking.Response;
import shared.networking.User.UserInfo;
import shared.payload.AddToConversationPayload;
import shared.payload.Conversation;
import shared.payload.ConversationMetadata;
import shared.payload.LoginCredentials;
import shared.payload.LoginResult;
import shared.payload.Message;
import shared.payload.RegisterResult;
import shared.payload.UserCreationPayload;
import shared.enums.LoginStatus;

public class ServerController {
    private Map<String, ConnectionHandler> activeSessions;
    private DataManager dataManager;
    private ConnectionListener connectionListener;
    private final LinkedBlockingQueue<Map.Entry<String, Response>> responseQueue;
    private Thread broadcasterThread;

    /*
     * Entry point for the server application.
     *
     * Usage: java ServerController [dataRootPath] [port] [ipv4]
     *
     *   dataRootPath - directory where persistent data (users, messages) is stored.
     *                  Defaults to "data" if not provided.
     *   port         - TCP port the server listens on for incoming client connections.
     *                  Defaults to 8080 if not provided.
     *                  Must be configurable so different environments (dev, staging, prod)
     *                  and multiple server instances can each bind to their own port.
     *   ipv4         - local interface IPv4 to bind to (e.g. 192.168.1.10).
     *                  Defaults to "0.0.0.0" (all interfaces), except when only
     *                  dataRootPath is provided (then defaults to "localhost").
     *
     * Example:
     *   java ServerController data 8080 192.168.1.10
     */
    public static void main(String[] args) {
        String dataRootPath = args.length > 0 ? args[0] : "data";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        String bindIPv4 = System.getProperty("server.bind.ip");
        if (bindIPv4 == null || bindIPv4.isBlank()) {
            bindIPv4 = args.length == 1 ? "localhost" : (args.length > 2 ? args[2] : "0.0.0.0");
        }
        ServerController serverController = new ServerController(bindIPv4, port, dataRootPath);
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

    public ServerController(String bindIPv4, int port, String dataRootPath) {
        this.activeSessions = new ConcurrentHashMap<>();
        this.dataManager = new DataManager(dataRootPath);
        this.connectionListener = new ConnectionListener(bindIPv4, port, this);
        this.responseQueue = new LinkedBlockingQueue<>();
        startBroadcasterThread();
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
        if (request.getType() != RequestType.PING) {
            System.out.println("Processing request: " + request.getType());
        }
        switch (request.getType()) {
            case REGISTER: {
                Response response = dataManager.handleRegister(request);
                if (response != null && response.getPayload() instanceof RegisterResult) {
                    RegisterResult registerResult = (RegisterResult) response.getPayload();
                    if (registerResult.getRegisterStatus() == RegisterStatus.SUCCESS
                            && registerResult.getUserInfo() != null) {
                        Response userCreationResponse = new Response(
                                ResponseType.USER_CREATION,
                                new UserCreationPayload(registerResult.getUserInfo()));
                        for (String userId : activeSessions.keySet()) {
                            responseQueue.offer(new AbstractMap.SimpleImmutableEntry<>(userId, userCreationResponse));
                        }
                    }
                }
                return response;
            }
            case LOGIN: {
                LoginCredentials loginCredentials = (LoginCredentials) request.getPayload();
                String loginName = loginCredentials == null ? null : loginCredentials.getLoginName();
                String existingUserId = dataManager.getUserIdByLoginName(loginName);
                if (existingUserId != null && hasActiveSession(existingUserId)) {
                    return new Response(ResponseType.LOGIN_RESULT, new LoginResult(LoginStatus.DUPLICATE_SESSION));
                }
                return dataManager.handleLogin(request);
            }
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
            case LEAVE_CONVERSATION: {
                // Capture the conversation id before the leaver is removed so we can
                // look up the remaining participants and notify them afterward.
                long leavingConvId = ((shared.payload.LeaveConversationPayload) request.getPayload())
                        .getTargetConversationId();
                Response leaveResponse = dataManager.handleLeaveConversation(request);
                // After handleLeaveConversation the leaver is already removed from the
                // participant roster, so getParticipantList now returns only the remaining members.
                enqueueLeaveConversationBroadcast(leavingConvId, request.getSenderId());
                return leaveResponse;
            }
            case ADMIN_CONVERSATION_QUERY:
                return dataManager.handleAdminConversationQuery(request);
            case ADMIN_VIEW_CONVERSATION:
                // Silent admin read — no broadcast, no participant mutation.
                return dataManager.handleAdminViewConversation(request);
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
                enqueueCreateConversationBroadcast(request.getSenderId(), response);
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
        String senderId = request.getSenderId();
        for (UserInfo participant : dataManager.getParticipantList(message.getConversationId())) {
            if (participant == null || participant.getUserId() == null) continue;
            String id = participant.getUserId();
            if (id.equals(senderId)) continue; // sender already gets the response via direct return
            if (hasActiveSession(id)) {
                responseQueue.offer(new AbstractMap.SimpleImmutableEntry<>(id, response));
            }
        }
    }

    private void enqueueCreateConversationBroadcast(String requesterId, Response response) {
        if (!(response.getPayload() instanceof Conversation)) return;
        Conversation conversation = (Conversation) response.getPayload();
        for (UserInfo participant : dataManager.getParticipantList(conversation.getConversationId())) {
            if (participant == null || participant.getUserId() == null) continue;
            String id = participant.getUserId();
            if (id.equals(requesterId)) continue; // requester already gets the response via direct return
            if (hasActiveSession(id)) {
                responseQueue.offer(new AbstractMap.SimpleImmutableEntry<>(id, response));
            }
        }
    }

    private void enqueueLeaveConversationBroadcast(long conversationId, String leaverId) {
        // getParticipantList returns the roster AFTER the leaver was removed, so every
        // entry here is a remaining participant — no need to skip by leaverId.
        ArrayList<UserInfo> remaining = dataManager.getParticipantList(conversationId);
        if (remaining.isEmpty()) return;
        Conversation conversation = dataManager.getConversation(conversationId);
        if (conversation == null) return;
        ConversationMetadata metadata = conversation.toMetadata();
        Response metadataResponse = new Response(ResponseType.CONVERSATION_METADATA, metadata);
        enqueueToActiveParticipants(remaining, metadataResponse);
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

    public boolean userExists(String loginName) {
        return dataManager.userExists(loginName);
    }

    public void removeSession(String userId) {
        activeSessions.remove(userId);
    }

    public void addSession(String userId, ConnectionHandler handler) {
        activeSessions.put(userId, handler);
    }
}
