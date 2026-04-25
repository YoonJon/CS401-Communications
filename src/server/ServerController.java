package server;

import java.util.*;
import java.util.concurrent.*;
import shared.networking.*;
import shared.enums.ResponseType;
import shared.networking.User.UserInfo;
import shared.payload.*;
import java.net.*;


public class ServerController {
    private Map<String, ConnectionHandler> activeSessions;
    private DataManager dataManager;
    private ConnectionListener connectionListener;
    private final LinkedBlockingQueue<Response> broadcasterQueue;
    private Thread broadcasterThread;

    public static void main(String[] args) {
        try {
            String localhost = InetAddress.getLocalHost().getHostAddress();
            ServerController serverController = new ServerController(localhost,8080);
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
        this.broadcasterQueue = new LinkedBlockingQueue<>();
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
            case REGISTER: {
                return dataManager.handleRegister(request);
            }
            case LOGIN: {
                return dataManager.handleLogin(request);
            }
            case MESSAGE: {
                Response response = dataManager.handleSendMessage(request);
                enqueueForBroadcast(response);
                return response;
            }
            case UPDATE_READ: {
                return dataManager.handleUpdateReadMessages(request);
            }
            case CREATE_CONVERSATION: {
                Response response = dataManager.handleCreateConversation(request);
                enqueueForBroadcast(response);
                return response;
            }
            case ADD_PARTICIPANT: {
                Response response = dataManager.handleAddToConversation(request);
                enqueueForBroadcast(response);
                return response;
            }
            case LEAVE_CONVERSATION: {
                return dataManager.handleLeaveConversation(request);
            }
            case ADMIN_CONVERSATION_QUERY: {
                return dataManager.handleAdminConversationQuery(request);
            }
            case JOIN_CONVERSATION: {
                return dataManager.handleJoinConversation(request);
            }
            case LOGOUT: {
                removeSession(request.getSenderId());
            }
            default:
                // Session / heartbeat concerns are handled outside DataManager handlers.
                return null;
        }
    }

    private void enqueueForBroadcast(Response response) {
        if (response == null) return;
        broadcasterQueue.offer(response);
    }

    private void startBroadcasterThread() {
        broadcasterThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Response response = broadcasterQueue.take();
                    if (response.getType() != ResponseType.MESSAGE
                            || !(response.getPayload() instanceof Message)) {
                        continue;
                    }

                    Message message = (Message) response.getPayload();
                    ArrayList<UserInfo> participants = dataManager.getParticipantList(message.getConversationId());
                    Set<String> activeUserIds = activeSessions.keySet();

                    for (UserInfo participant : participants) {
                        if (participant == null || participant.getUserId() == null) {
                            continue;
                        }
                        String participantId = participant.getUserId();
                        if (activeUserIds.contains(participantId)) {
                            ConnectionHandler handler = activeSessions.get(participantId);
                            if (handler != null) {
                                handler.sendResponse(response);
                            }
                        }
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

    private void startConnectionListenerThread() {
        Thread t = new Thread(() -> connectionListener.listen(), "server-listener");
        t.setDaemon(true);
        t.start();
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
