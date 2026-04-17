package shared.networking;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import server.ServerController;

/**
 * Listens on a TCP port and dispatches each incoming connection to a
 * {@link ConnectionHandler} running in a cached thread pool.
 *
 * Threading model
 * ---------------
 * - {@link #listen()} must be called from a dedicated thread; it blocks until
 *   {@link #close()} is called.
 * - Each accepted {@link Socket} is submitted to {@code threadPool}, which
 *   creates or reuses a thread per handler.
 * - {@link #close()} is safe to call from any thread.
 */
public class ConnectionListener {

    private static final Logger logger =
            Logger.getLogger(ConnectionListener.class.getName());

    private final int hostPort;
    private final ServerController serverController;
    private final ExecutorService threadPool;

    /** Signals that {@link #serverSocket} has been bound and is ready to accept. */
    private final CountDownLatch bindLatch = new CountDownLatch(1);

    private volatile boolean running = false;
    private volatile ServerSocket serverSocket;

    public ConnectionListener(int hostPort, ServerController serverController) {
        this.hostPort = hostPort;
        this.serverController = serverController;
        this.threadPool = Executors.newCachedThreadPool();
    }

    /**
     * Binds the server socket and accepts client connections indefinitely.
     * Blocks until {@link #close()} is called or the socket is closed externally.
     * Run this in a dedicated thread.
     */
    public void listen() {
        running = true;
        try {
            serverSocket = new ServerSocket(hostPort);
            bindLatch.countDown(); // notify getLocalPort() waiters
            logger.info("Listening on port " + serverSocket.getLocalPort());

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ConnectionHandler handler =
                            new ConnectionHandler(clientSocket, serverController);
                    threadPool.submit(handler);
                } catch (IOException e) {
                    if (running) {
                        logger.warning("Accept error: " + e.getMessage());
                    }
                    // When running == false the socket was closed by close() — exit loop.
                }
            }
        } catch (IOException e) {
            if (running) {
                logger.severe("Cannot open server socket on port " + hostPort
                        + ": " + e.getMessage());
            }
        } finally {
            bindLatch.countDown(); // ensure waiters are never stuck if listen() fails
        }
    }

    /**
     * Returns the TCP port the server socket is actually bound to.
     * Blocks up to 5 seconds for {@link #listen()} to bind the socket.
     * Useful for tests that use port 0 (OS-assigned port).
     *
     * @return bound port number, or -1 on timeout / bind failure
     */
    public int getLocalPort() {
        try {
            bindLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return (serverSocket != null) ? serverSocket.getLocalPort() : -1;
    }

    /**
     * Stops the listener gracefully.
     * Closes the server socket (unblocking any pending {@code accept()} call),
     * then shuts down the handler thread pool.
     */
    public void close() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.warning("Error closing server socket: " + e.getMessage());
            }
        }
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
