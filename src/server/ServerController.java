package server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import shared.networking.*;

public class ServerController {
    private String dataFilePath;
    private Map<String, ConnectionHandler> activeSessions;
    private DataManager dataManager;
    private ConnectionListener connectionListener;

    public static void main(String[] args) {
        // TODO: parse args, instantiate ServerController
    }

    public ServerController(String dataFilePath, int port) {
        this.dataFilePath = dataFilePath;
        this.activeSessions = new ConcurrentHashMap<>();
        this.dataManager = new DataManager(dataFilePath);
        this.connectionListener = new ConnectionListener(port, this);
        // TODO: start listening
    }

    public void close() {
        // TODO: shut down gracefully
    }

    public Response processRequest(Request request) {
        // TODO: dispatch to DataManager based on request type
        return null;
    }

    private void distributeResponse(Response r, Set<String> userIDs) {
        // TODO: send response to all active sessions in userIDs
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
