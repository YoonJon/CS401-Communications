package shared.networking;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    private final int hostPort;
    private final String hostAddress;
    private final ServerController serverController;
    private final ExecutorService threadPool;

    /** Signals that {@link #serverSocket} has been bound and is ready to accept. */
    private final CountDownLatch bindLatch = new CountDownLatch(1);

    private volatile boolean running = false;
    private volatile ServerSocket serverSocket;

    public ConnectionListener(String hostAddress, int hostPort, ServerController serverController) {
        this.hostPort = hostPort;
        this.hostAddress = hostAddress;
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
            if (hostAddress == null || hostAddress.isBlank()) {
                serverSocket = new ServerSocket(hostPort);
            } else {
                serverSocket = new ServerSocket(hostPort, 50, InetAddress.getByName(hostAddress));
            }
            bindLatch.countDown(); // notify getLocalPort() waiters

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ConnectionHandler handler =
                            new ConnectionHandler(clientSocket, serverController);
                    threadPool.submit(handler);
                } catch (IOException e) {
                    // Keep listener quiet unless caller inspects behavior directly.
                    // When running == false the socket was closed by close() — exit loop.
                }
            }
        } catch (IOException e) {
            // Keep listener quiet; startup failures surface via calling flows/tests.
        } finally {
            bindLatch.countDown(); // ensure waiters are never stuck if listen() fails
        }
    }

    /**
     * Returns the TCP port the server socket is actually bound to.
     * Blocks up to 5 seconds for {@link #listen()} to bind the socket.
     * Primarily a test-support seam (non-production flow), especially for port 0 (OS-assigned port).
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
                // Best-effort close; ignore shutdown-time close noise.
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
