package shared.networking;

import server.ServerController;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectionListener {
    private ExecutorService threadPool;
    private int hostPort;
    private ServerController serverController;

    public ConnectionListener(int hostPort, ServerController serverController) {
        this.hostPort = hostPort;
        this.serverController = serverController;
        this.threadPool = Executors.newCachedThreadPool();
    }

    public void listen() {
        // TODO: accept incoming connections in a loop, hand off to ConnectionHandler via threadPool
    }

    public void close() {
        // TODO: stop accepting connections, shut down thread pool
    }
}
