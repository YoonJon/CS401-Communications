package shared.networking;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import server.ServerController;
import shared.enums.LoginStatus;
import shared.enums.RequestType;
import shared.enums.ResponseType;
import shared.payload.LoginResult;
import shared.networking.User.UserInfo;

/**
 * Manages the full lifecycle of one client connection.
 *
 * Threading model
 * ---------------
 *  run()           — called once by the thread pool; creates streams, starts two
 *                    child threads, then blocks until the request thread finishes.
 *  RequestListener — inner thread: reads {@link Request} objects from the socket,
 *                    forwards them to {@link ServerController#processRequest}, and
 *                    enqueues the returned {@link Response} for the writer.
 *  ResponseSender  — inner thread: drains {@link #responseQueue} and writes each
 *                    {@link Response} to the socket in FIFO order.
 *
 * Close / shutdown
 * ----------------
 *  {@link #close()} is idempotent and thread-safe. It uses an {@link AtomicBoolean}
 *  to guarantee only one shutdown sequence runs, closes the underlying socket
 *  (which unblocks any blocked readObject()), and interrupts the response thread
 *  (which unblocks any blocked queue take()).
 *
 * Auth / session wiring
 * ---------------------
 *  After a successful LOGIN response the handler marks itself authenticated,
 *  stores the {@link UserInfo}, and registers itself with
 *  {@link ServerController#addSession}.  On LOGOUT or disconnect the session is
 *  removed via {@link ServerController#removeSession}.
 */
public class ConnectionHandler implements Runnable {

    private static final Logger logger =
            Logger.getLogger(ConnectionHandler.class.getName());

    private final Socket socket;
    private final ServerController serverController;
    private final BlockingQueue<Response> responseQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile UserInfo userInfo;
    private volatile boolean authenticated = false;
    private volatile long lastPingReceived = System.currentTimeMillis();

    private ObjectInputStream input;
    private ObjectOutputStream output;
    private Thread requestThread;
    private Thread responseThread;

    public ConnectionHandler(Socket socket, ServerController serverController) {
        this.socket = socket;
        this.serverController = serverController;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void run() {
        try {
            // ObjectOutputStream MUST be created and flushed before ObjectInputStream
            // on BOTH sides to avoid stream-header deadlock.
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input  = new ObjectInputStream(socket.getInputStream());

            String tag = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            requestThread  = new Thread(new RequestListener(),  "req-"  + tag);
            responseThread = new Thread(new ResponseSender(),   "resp-" + tag);

            responseThread.setDaemon(true);
            responseThread.start();
            requestThread.start();

            requestThread.join(); // block until reader exits, then we close
        } catch (Exception e) {
            if (!closed.get()) {
                logger.warning("ConnectionHandler error: " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    /**
     * Idempotent shutdown. Closes the socket (unblocking any blocked read),
     * deregisters the session, and interrupts the response thread.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return; // already closed
        }
        // Deregister session immediately so the server never routes to a dead handler.
        if (authenticated && userInfo != null) {
            serverController.removeSession(userInfo.getUserId());
        }
        try {
            socket.close();
        } catch (IOException e) {
            logger.fine("Socket close: " + e.getMessage());
        }
        if (responseThread != null) {
            responseThread.interrupt(); // unblocks responseQueue.take()
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Enqueue a {@link Response} for delivery to this client.
     * Thread-safe; may be called from any thread.
     */
    public void sendResponse(Response res) {
        if (!closed.get()) {
            responseQueue.offer(res);
        }
    }

    public UserInfo getUserInfo()        { return userInfo; }
    public boolean isAuthenticated()     { return authenticated; }
    public long getLastPingReceived()    { return lastPingReceived; }

    // -------------------------------------------------------------------------
    // RequestListener — one thread per handler
    // -------------------------------------------------------------------------

    class RequestListener implements Runnable {
        @Override
        public void run() {
            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                // --- read ---
                Object obj;
                try {
                    obj = input.readObject();
                } catch (EOFException | SocketException e) {
                    break; // client disconnected cleanly
                } catch (IOException | ClassNotFoundException e) {
                    if (!closed.get()) {
                        logger.warning("Read error: " + e.getMessage());
                    }
                    break;
                }

                if (!(obj instanceof Request)) {
                    logger.warning("Non-Request object received; dropping connection.");
                    break;
                }

                Request request = (Request) obj;

                // Update heartbeat timestamp for PING
                if (request.getType() == RequestType.PING) {
                    lastPingReceived = System.currentTimeMillis();
                }

                // --- dispatch ---
                try {
                    Response response = serverController.processRequest(request);
                    if (response != null) {
                        handleSessionTransition(request, response);
                        sendResponse(response);

                        // After sending LOGOUT_RESULT the session is already removed;
                        // keep the socket open so the response drains before the client closes.
                    }
                } catch (Exception e) {
                    // Don't kill the connection for a server-side error on one request.
                    logger.warning("Error processing request " + request.getType()
                            + ": " + e.getMessage());
                }
            }
            close();
        }

        /**
         * Inspects the outbound response to drive auth/session state transitions:
         * <ul>
         *   <li>Successful LOGIN  → mark authenticated, register with ServerController.</li>
         *   <li>LOGOUT (any resp) → clear authenticated state, deregister session.</li>
         * </ul>
         */
        private void handleSessionTransition(Request request, Response response) {
            if (request.getType() == RequestType.LOGIN
                    && response.getType() == ResponseType.LOGIN_RESULT
                    && response.getPayload() instanceof LoginResult) {

                LoginResult lr = (LoginResult) response.getPayload();
                if (lr.getLoginStatus() == LoginStatus.SUCCESS
                        && lr.getUserInfo() != null) {
                    userInfo = lr.getUserInfo();
                    authenticated = true;
                    serverController.addSession(userInfo.getUserId(), ConnectionHandler.this);
                }

            } else if (request.getType() == RequestType.LOGOUT
                    && authenticated && userInfo != null) {
                serverController.removeSession(userInfo.getUserId());
                authenticated = false;
            }
        }
    }

    // -------------------------------------------------------------------------
    // ResponseSender — one thread per handler
    // -------------------------------------------------------------------------

    class ResponseSender implements Runnable {
        @Override
        public void run() {
            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Response response = responseQueue.take(); // blocks until available
                    output.writeObject(response);
                    output.flush();
                    output.reset(); // prevent object-reference cache memory leak
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    if (!closed.get()) {
                        logger.warning("Write error: " + e.getMessage());
                    }
                    break;
                }
            }
        }
    }
}
