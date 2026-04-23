package server;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import shared.networking.*;

public class ServerController {
    private final File dataRoot;
    private Map<String, ConnectionHandler> activeSessions;
    private DataManager dataManager;
    private ConnectionListener connectionListener;

    public static void main(String[] args) {
        // TODO: parse args, instantiate ServerController
    }

    public ServerController(String dataRootPath, int port) {
        this.dataRoot = new File(dataRootPath);
        this.activeSessions = new ConcurrentHashMap<>();
        this.dataManager = new DataManager(dataRootPath);
        this.connectionListener = new ConnectionListener(port, this);
        // TODO: start listening
    }

    /** Directory passed at construction; same root given to {@link DataManager}. */
    public File getDataRoot() {
        return dataRoot;
    }

    public void close() {
        if (dataManager != null) {
            dataManager.close();
        }
        // TODO: shut down listener and active sessions
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
