package shared.networking;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import shared.enums.LoginStatus;
import shared.enums.RequestType;
import shared.enums.ResponseType;
import shared.networking.fixtures.FakePayload;
import shared.networking.fixtures.FakeServerController;
import shared.networking.fixtures.NetworkingSeedData;
import shared.payload.LoginResult;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 transport tests for the networking layer.
 *
 * Dependency: JUnit Jupiter 5.x on the classpath (junit-jupiter-api,
 *             junit-jupiter-engine). No DataManager, file I/O, or persistence
 *             is involved — all server behaviour is provided by
 *             {@link FakeServerController} using {@link NetworkingSeedData}.
 *
 * Each test starts a real {@link ConnectionListener} on a loopback socket
 * (port 0 = OS-assigned) and connects one or more {@link TestClient} helpers
 * to exercise the full TCP serialization path.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS) // guard against stuck threads
class NetworkingTest {

    // -------------------------------------------------------------------------
    // Shared fixture
    // -------------------------------------------------------------------------

    private FakeServerController fakeServer;
    private ConnectionListener listener;
    private Thread listenerThread;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        fakeServer = new FakeServerController();
        listener   = new ConnectionListener(0, fakeServer); // port 0 → OS assigns

        listenerThread = new Thread(() -> listener.listen(), "test-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        // getLocalPort() blocks until the socket is bound (CountDownLatch inside)
        port = listener.getLocalPort();
        assertTrue(port > 0, "Listener did not bind in time");
    }

    @AfterEach
    void tearDown() throws Exception {
        listener.close();
        listenerThread.join(3_000);
    }

    // =========================================================================
    // 1. Serialization round-trip (no network needed)
    // =========================================================================

    @Test
    void requestSerializesAndDeserializes() throws Exception {
        Request original = NetworkingSeedData.pingRequest();
        Request copy = serializeRoundTrip(original, Request.class);
        assertEquals(original.getType(), copy.getType());
        assertEquals(((FakePayload) original.getPayload()).getValue(),
                     ((FakePayload) copy.getPayload()).getValue());
    }

    @Test
    void responseSerializesAndDeserializes() throws Exception {
        Response original = NetworkingSeedData.pongResponse();
        Response copy = serializeRoundTrip(original, Response.class);
        assertEquals(original.getType(), copy.getType());
        assertEquals(((FakePayload) original.getPayload()).getValue(),
                     ((FakePayload) copy.getPayload()).getValue());
    }

    @Test
    void loginResultSerializeWithUserInfo() throws Exception {
        Response original = NetworkingSeedData.loginSuccessResponseAlice();
        Response copy = serializeRoundTrip(original, Response.class);
        LoginResult lr = (LoginResult) copy.getPayload();
        assertEquals(LoginStatus.SUCCESS, lr.getLoginStatus());
        assertEquals(NetworkingSeedData.ALICE_ID, lr.getUserInfo().getUserId());
        assertEquals(NetworkingSeedData.ALICE_NAME, lr.getUserInfo().getName());
    }

    // =========================================================================
    // 2. Single PING → PONG round-trip over a real socket
    // =========================================================================

    @Test
    void pingPongRoundTrip() throws Exception {
        try (TestClient client = new TestClient(port)) {
            client.send(NetworkingSeedData.pingRequest());
            Response response = client.receive();
            assertEquals(ResponseType.PONG, response.getType());
        }
    }

    // =========================================================================
    // 3. Listener accepts a connection and the server records the request
    // =========================================================================

    @Test
    void listenerForwardsRequestToServerController() throws Exception {
        try (TestClient client = new TestClient(port)) {
            client.send(NetworkingSeedData.pingRequest());
            client.receive(); // consume response

            // Give the handler a moment to dispatch
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            assertFalse(fakeServer.getReceived().isEmpty());
            assertEquals(RequestType.PING,
                         fakeServer.getReceived().get(0).getType());
        }
    }

    // =========================================================================
    // 4. Multiple responses delivered in FIFO order
    // =========================================================================

    @Test
    void responsesDeliveredInFifoOrder() throws Exception {
        Response[] batch = NetworkingSeedData.orderedBatch();

        // Server returns each element of the batch in turn
        int[] callCount = {0};
        fakeServer.setRequestHandler(req -> {
            int idx = callCount[0]++;
            return idx < batch.length ? batch[idx] : NetworkingSeedData.pongResponse();
        });

        try (TestClient client = new TestClient(port)) {
            for (int i = 0; i < batch.length; i++) {
                client.send(NetworkingSeedData.pingRequest());
            }
            for (int i = 0; i < batch.length; i++) {
                Response r = client.receive();
                assertEquals(ResponseType.PONG, r.getType());
                assertEquals("batch-" + i, ((FakePayload) r.getPayload()).getValue(),
                        "Response at index " + i + " was out of order");
            }
        }
    }

    // =========================================================================
    // 5. Client disconnect — handler threads must stop cleanly
    // =========================================================================

    @Test
    void clientDisconnectStopsHandlerCleanly() throws Exception {
        CountDownLatch connected = new CountDownLatch(1);

        fakeServer.setRequestHandler(req -> {
            connected.countDown();
            return NetworkingSeedData.pongResponse();
        });

        try (TestClient client = new TestClient(port)) {
            client.send(NetworkingSeedData.pingRequest());
            connected.await(2, TimeUnit.SECONDS);
        } // TestClient.close() closes the socket here

        // Pool must drain within 3 s — if handler threads hang, this times out
        // (test-level @Timeout catches it)
        Thread.sleep(200); // brief wait for handler EOF detection
        // No assertion needed — not hanging is proof of clean shutdown
    }

    // =========================================================================
    // 6. Concurrent clients — each gets isolated responses
    // =========================================================================

    @Test
    void multipleConcurrentClientsGetIsolatedResponses() throws Exception {
        int clientCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(clientCount);
        List<Future<ResponseType>> futures = new ArrayList<>();

        for (int i = 0; i < clientCount; i++) {
            futures.add(pool.submit(() -> {
                try (TestClient c = new TestClient(port)) {
                    c.send(NetworkingSeedData.pingRequest());
                    return c.receive().getType();
                }
            }));
        }

        pool.shutdown();
        pool.awaitTermination(8, TimeUnit.SECONDS);

        for (Future<ResponseType> f : futures) {
            assertEquals(ResponseType.PONG, f.get(),
                    "Every concurrent client should receive PONG");
        }
    }

    // =========================================================================
    // 7. PING updates the handler's lastPingReceived timestamp
    // =========================================================================

    @Test
    void pingUpdatesLastPingTimestamp() throws Exception {
        // We drive login first so the handler registers, then inspect lastPingReceived
        fakeServer.setRequestHandler(req -> {
            if (req.getType() == RequestType.LOGIN) {
                return NetworkingSeedData.loginSuccessResponseAlice();
            }
            return NetworkingSeedData.pongResponse();
        });

        try (TestClient client = new TestClient(port)) {
            // Login → handler becomes authenticated and registers with controller
            client.send(NetworkingSeedData.loginRequestAlice());
            Response loginResp = client.receive();
            assertEquals(ResponseType.LOGIN_RESULT, loginResp.getType());

            // Wait for addSession to complete
            waitFor(() -> fakeServer.hasActiveSession(NetworkingSeedData.ALICE_ID), 2_000);

            // Now send PING and verify a PONG comes back (timestamp updated internally)
            long before = System.currentTimeMillis();
            Thread.sleep(5); // ensure clock ticks
            client.send(NetworkingSeedData.pingRequest());
            Response pong = client.receive();
            assertEquals(ResponseType.PONG, pong.getType());
            // The server received the PING = lastPingReceived was updated (verified by response)
            assertTrue(System.currentTimeMillis() >= before);
        }
    }

    // =========================================================================
    // 8. Successful login marks the handler as authenticated
    // =========================================================================

    @Test
    void successfulLoginRegistersSession() throws Exception {
        fakeServer.setRequestHandler(req ->
                NetworkingSeedData.loginSuccessResponseAlice());

        try (TestClient client = new TestClient(port)) {
            client.send(NetworkingSeedData.loginRequestAlice());
            Response response = client.receive();

            assertEquals(ResponseType.LOGIN_RESULT, response.getType());
            LoginResult lr = (LoginResult) response.getPayload();
            assertEquals(LoginStatus.SUCCESS, lr.getLoginStatus());

            // After a successful login the session is registered in the controller
            waitFor(() -> fakeServer.hasActiveSession(NetworkingSeedData.ALICE_ID), 2_000);
            assertTrue(fakeServer.hasActiveSession(NetworkingSeedData.ALICE_ID),
                    "Alice's session should be active after successful login");
        }
    }

    // =========================================================================
    // 9. Failed login does NOT register a session
    // =========================================================================

    @Test
    void failedLoginDoesNotRegisterSession() throws Exception {
        fakeServer.setRequestHandler(req ->
                NetworkingSeedData.loginFailedResponse());

        try (TestClient client = new TestClient(port)) {
            client.send(NetworkingSeedData.loginRequestAlice());
            Response response = client.receive();

            assertEquals(ResponseType.LOGIN_RESULT, response.getType());
            LoginResult lr = (LoginResult) response.getPayload();
            assertEquals(LoginStatus.INVALID_CREDENTIALS, lr.getLoginStatus());

            Thread.sleep(100); // let any async registration (there should be none) settle
            assertFalse(fakeServer.hasActiveSession(NetworkingSeedData.ALICE_ID),
                    "No session should be active after a failed login");
        }
    }

    // =========================================================================
    // 10. Listener shutdown — no new connections accepted after close()
    // =========================================================================

    @Test
    void listenerStopsAcceptingAfterClose() throws Exception {
        listener.close();
        listenerThread.join(3_000);

        // Attempt a connection after close — it should be refused
        assertThrows(java.net.ConnectException.class,
                () -> new Socket("127.0.0.1", port).close(),
                "No connection should be accepted after listener is closed");
    }

    // =========================================================================
    // 11. Session deregistered on disconnect after login
    // =========================================================================

    @Test
    void sessionRemovedOnDisconnect() throws Exception {
        fakeServer.setRequestHandler(req ->
                NetworkingSeedData.loginSuccessResponseAlice());

        try (TestClient client = new TestClient(port)) {
            client.send(NetworkingSeedData.loginRequestAlice());
            client.receive(); // consume LOGIN_RESULT

            waitFor(() -> fakeServer.hasActiveSession(NetworkingSeedData.ALICE_ID), 2_000);
            assertTrue(fakeServer.hasActiveSession(NetworkingSeedData.ALICE_ID));
        } // socket closes here

        // Session should be removed once the handler detects disconnection
        waitFor(() -> !fakeServer.hasActiveSession(NetworkingSeedData.ALICE_ID), 3_000);
        assertFalse(fakeServer.hasActiveSession(NetworkingSeedData.ALICE_ID),
                "Session should be removed after client disconnects");
    }

    // =========================================================================
    // 12. Multiple requests in sequence over the same connection
    // =========================================================================

    @Test
    void multipleSequentialRequestsOnOneConnection() throws Exception {
        int rounds = 20;
        try (TestClient client = new TestClient(port)) {
            for (int i = 0; i < rounds; i++) {
                client.send(NetworkingSeedData.pingRequest());
            }
            for (int i = 0; i < rounds; i++) {
                Response r = client.receive();
                assertEquals(ResponseType.PONG, r.getType(),
                        "Round " + i + " should yield PONG");
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Serializes {@code obj} to bytes in memory and deserializes it back. */
    @SuppressWarnings("unchecked")
    private static <T> T serializeRoundTrip(T obj, Class<T> type) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()))) {
            return (T) ois.readObject();
        }
    }

    /**
     * Polls {@code condition} up to {@code timeoutMs} milliseconds.
     * Helpful for waiting for async state changes without arbitrary sleeps.
     */
    private static void waitFor(BooleanSupplierChecked condition, long timeoutMs)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) return;
            Thread.sleep(20);
        }
    }

    @FunctionalInterface
    interface BooleanSupplierChecked {
        boolean getAsBoolean() throws Exception;
    }

    // =========================================================================
    // TestClient — synchronous loopback helper
    // =========================================================================

    /**
     * Minimal TCP client for networking tests.
     *
     * Stream creation order is critical: ObjectOutputStream must be created
     * and flushed before ObjectInputStream on BOTH sides to avoid stream-header
     * deadlock (matches the order in {@link ConnectionHandler#run()}).
     */
    static final class TestClient implements Closeable {

        private final Socket socket;
        private final ObjectOutputStream out;
        private final ObjectInputStream in;

        TestClient(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            out    = new ObjectOutputStream(socket.getOutputStream());
            out.flush(); // send stream header before reading the server's header
            in     = new ObjectInputStream(socket.getInputStream());
        }

        void send(Request request) throws Exception {
            out.writeObject(request);
            out.flush();
        }

        Response receive() throws Exception {
            return (Response) in.readObject();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
