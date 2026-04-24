package shared.networking.fixtures;

import server.ServerController;
import shared.networking.Request;
import shared.networking.Response;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Controllable stub of {@link ServerController} for networking tests.
 *
 * Overrides {@link #processRequest} to record every incoming request and
 * return whatever the test configures via {@link #setRequestHandler}.
 * All session management methods ({@code addSession}, {@code removeSession},
 * {@code hasActiveSession}) are inherited from {@link ServerController} and
 * operate on the live {@code ConcurrentHashMap}, so auth-transition tests can
 * call {@code hasActiveSession()} just like the real server would.
 *
 * Constructor note: passes a path under {@code java.io.tmpdir} so project {@code data/} is not
 * touched; {@code ConnectionListener(0, this)} never calls {@code listen()}.
 *
 * Public because it is referenced from {@code shared.networking.NetworkingTest}
 * (a different package from this fixtures sub-package).
 */
public class FakeServerController extends ServerController {

    /** Default handler: reply with PONG for every request. */
    private volatile Function<Request, Response> requestHandler =
            req -> NetworkingSeedData.pongResponse();

    private final List<Request> received = new CopyOnWriteArrayList<>();

    public FakeServerController() {
        super(new File(System.getProperty("java.io.tmpdir"), "cs401-fake-server-data").getPath(), 0);
    }

    // -------------------------------------------------------------------------

    @Override
    public Response processRequest(Request request) {
        received.add(request);
        return requestHandler.apply(request);
    }

    // -------------------------------------------------------------------------
    // Test-control helpers
    // -------------------------------------------------------------------------

    /** Replace the response logic for the duration of one test. */
    public void setRequestHandler(Function<Request, Response> handler) {
        this.requestHandler = handler;
    }

    /** Returns an unmodifiable snapshot of all requests received so far. */
    public List<Request> getReceived() {
        return Collections.unmodifiableList(received);
    }

    /** Clears the recorded request list between test assertions. */
    public void clearReceived() {
        received.clear();
    }
}
