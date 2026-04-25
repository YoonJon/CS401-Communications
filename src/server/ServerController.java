package server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    public static void main(String[] args) {
        try {
            String localhost = InetAddress.getLocalHost().getHostAddress();
            ServerController serverController = new ServerController(localhost, 8080);
            keepAliveUntilInterrupted(serverController);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private static void keepAliveUntilInterrupted(ServerController serverController) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                serverController.close();
                break;
            }
        }
    }

    public ServerController(String dataRootPath, int port) {
        this.activeSessions = new ConcurrentHashMap<>();
        this.dataManager = new DataManager(dataRootPath);
        this.connectionListener = new ConnectionListener(port, this);
        this.responseQueue = new LinkedBlockingQueue<>();
        startBroadcasterThread();
        startConnectionListenerThread();
    }

    public void close() {
        if (broadcasterThread != null) {
            broadcasterThread.interrupt();
        }
        if (connectionListener != null) {
            connectionListener.close();
        }
        if (dataManager != null) {
            dataManager.close();
        }
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
            case UPDATE_READ:
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

    private void enqueueMessageBroadcast(Request request, Response response) {
        if (!(response.getPayload() instanceof Message)) {
            return;
        }
        Message message = (Message) response.getPayload();
        ArrayList<UserInfo> participants = dataManager.getParticipantList(message.getConversationId());
        for (UserInfo participant : participants) {
            if (participant == null || participant.getUserId() == null) {
                continue;
            }
            String participantId = participant.getUserId();
            if (!hasActiveSession(participantId)) {
                continue;
            }
            responseQueue.offer(new AbstractMap.SimpleImmutableEntry<>(participantId, response));
        }
    }

    private void enqueueCreateConversationBroadcast(Response response) {
        if (!(response.getPayload() instanceof Conversation)) {
            return;
        }
        Conversation conversation = (Conversation) response.getPayload();
        ArrayList<UserInfo> participants = dataManager.getParticipantList(conversation.getConversationId());
        for (UserInfo participant : participants) {
            if (participant == null || participant.getUserId() == null) {
                continue;
            }
            String participantId = participant.getUserId();
            if (!hasActiveSession(participantId)) {
                continue;
            }
            responseQueue.offer(new AbstractMap.SimpleImmutableEntry<>(participantId, response));
        }
    }

    private void enqueueAddParticipantBroadcast(Request request, Response response) {
        if (!(response.getPayload() instanceof Conversation)
                || !(request.getPayload() instanceof AddToConversationPayload)) {
            return;
        }
        Conversation conversation = (Conversation) response.getPayload();
        AddToConversationPayload payload = (AddToConversationPayload) request.getPayload();
        Set<String> addedParticipantIds = new HashSet<>();
        ArrayList<UserInfo> participantsToAdd = payload.getParticipants();
        if (participantsToAdd != null) {
            for (UserInfo participant : participantsToAdd) {
                if (participant != null && participant.getUserId() != null) {
                    addedParticipantIds.add(participant.getUserId());
                }
            }
        }
        Response metadataResponse = new Response(ResponseType.CONVERSATION, conversation.toMetadata());
        for (UserInfo participant : dataManager.getParticipantList(conversation.getConversationId())) {
            if (participant == null || participant.getUserId() == null) {
                continue;
            }
            String participantId = participant.getUserId();
            if (!hasActiveSession(participantId)) {
                continue;
            }
            Response delivery = addedParticipantIds.contains(participantId) ? response : metadataResponse;
            responseQueue.offer(new AbstractMap.SimpleImmutableEntry<>(participantId, delivery));
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
