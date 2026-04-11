package shared.networking;

import server.ServerController;
import shared.payload.UserInfo;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class ConnectionHandler implements Runnable {
    private UserInfo userInfo;
    private boolean authenticated;
    private Socket socket;
    private ServerController serverController;
    private Queue<Response> responseQueue;
    private Thread requestThread;
    private Thread responseThread;
    private long lastPingReceived;

    public ConnectionHandler(Socket socket, ServerController serverController) {
        this.socket = socket;
        this.serverController = serverController;
        this.authenticated = false;
        this.responseQueue = new LinkedBlockingQueue<>();
    }

    @Override
    public void run() {
        // TODO: start RequestListener and ResponseSender threads
    }

    public void close() {
        // TODO: clean up socket and threads
    }

    public void sendResponse(Response res) {
        responseQueue.add(res);
    }

    public UserInfo getUserInfo() { return userInfo; }
    public boolean isAuthenticated() { return authenticated; }

    // -------------------------------------------------------------------------
    class RequestListener implements Runnable {
        @Override
        public void run() {
            // TODO: read Requests from socket ObjectInputStream, forward to serverController
        }
    }

    // -------------------------------------------------------------------------
    class ResponseSender implements Runnable {
        @Override
        public void run() {
            // TODO: drain responseQueue and write Responses to socket ObjectOutputStream
        }
    }
}
