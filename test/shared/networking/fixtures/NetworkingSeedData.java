package shared.networking.fixtures;

import shared.enums.LoginStatus;
import shared.enums.RegisterStatus;
import shared.enums.RequestType;
import shared.enums.ResponseType;
import shared.enums.UserType;
import shared.networking.Request;
import shared.networking.Response;
import shared.networking.User;
import shared.networking.User.UserInfo;
import shared.payload.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hard-coded, deterministic test data for networking-layer tests.
 *
 * All values are fixed constants so tests are reproducible without any
 * database, file I/O, or DataManager dependency.
 *
 * <h3>Employee-ID model</h3>
 * In this project every {@code userId} is a company-issued employee ID.
 * {@code DataManager.authorizedUsers} is a server-side map
 * {@code employeeId → legalName} preloaded before the server starts.
 * Registration succeeds only when the submitted {@code (userId, name)} pair
 * matches an entry in that map.  {@link #PRELOADED_EMPLOYEES} models that
 * roster; registration test helpers drive both the happy-path and the two
 * failure modes (unknown ID → {@code USER_ID_INVALID}, duplicate account →
 * {@code USER_ID_TAKEN}).
 *
 * Public because it is referenced from {@code shared.networking.NetworkingTest}
 * (a different package from this fixtures sub-package).
 */
public final class NetworkingSeedData {

    // =========================================================================
    // Fixed user constants
    // =========================================================================

    public static final String ALICE_ID       = "1001";
    public static final String ALICE_NAME     = "Alice";
    public static final String ALICE_LOGIN    = "alice";
    public static final String ALICE_PASSWORD = "pass.alice.1";

    public static final String BOB_ID         = "1002";
    public static final String BOB_NAME       = "Bob";
    public static final String BOB_LOGIN      = "bob";
    public static final String BOB_PASSWORD   = "pass.bob.2";

    /**
     * Carol is in the pre-loaded employee roster but has no account yet.
     * Use her data to exercise the successful first-registration path.
     */
    public static final String CAROL_ID       = "1003";
    public static final String CAROL_NAME     = "Carol White";
    public static final String CAROL_LOGIN    = "carol";
    public static final String CAROL_PASSWORD = "pass.carol.3";

    public static final String ADMIN_ID       = "9001";
    public static final String ADMIN_NAME     = "AdminUser";
    public static final String ADMIN_LOGIN    = "admin";
    public static final String ADMIN_PASSWORD = "admin.secure.99";

    public static final String CONV_ID_1 = "conv-001";
    public static final String CONV_ID_2 = "conv-002";

    // =========================================================================
    // Pre-loaded employee roster
    // (models what DataManager.authorizedUsers would contain on startup)
    // employeeId → legal full name
    // =========================================================================

    /**
     * Unmodifiable map of employee IDs to their authorised legal names.
     * Any registration whose {@code (userId, name)} pair is NOT in this map
     * should be rejected with {@code USER_ID_INVALID}.
     */
    public static final Map<String, String> PRELOADED_EMPLOYEES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(ALICE_ID,  ALICE_NAME);
        m.put(BOB_ID,    BOB_NAME);
        m.put(CAROL_ID,  CAROL_NAME);
        m.put(ADMIN_ID,  ADMIN_NAME);
        PRELOADED_EMPLOYEES = Collections.unmodifiableMap(m);
    }

    /**
     * Unmodifiable list of employee IDs that receive admin privileges.
     * (Models {@code DataManager.authorizedAdminIds}.)
     */
    public static final List<String> PRELOADED_ADMIN_IDS =
            Collections.unmodifiableList(Arrays.asList(ADMIN_ID));

    // =========================================================================
    // UserInfo factories
    // =========================================================================

    public static UserInfo aliceInfo() {
        return User.userInfo(ALICE_ID, ALICE_NAME, UserType.USER);
    }

    public static UserInfo bobInfo() {
        return User.userInfo(BOB_ID, BOB_NAME, UserType.USER);
    }

    public static UserInfo carolInfo() {
        return User.userInfo(CAROL_ID, CAROL_NAME, UserType.USER);
    }

    public static UserInfo adminInfo() {
        return User.userInfo(ADMIN_ID, ADMIN_NAME, UserType.ADMIN);
    }

    // =========================================================================
    // Request factories — authentication
    // =========================================================================

    public static Request pingRequest() {
        return new Request(RequestType.PING, new FakePayload("ping"), null);
    }

    public static Request loginRequestAlice() {
        return new Request(RequestType.LOGIN,
                new LoginCredentials(ALICE_LOGIN, ALICE_PASSWORD), null);
    }

    public static Request loginRequestBob() {
        return new Request(RequestType.LOGIN,
                new LoginCredentials(BOB_LOGIN, BOB_PASSWORD), null);
    }

    public static Request logoutRequest() {
        return new Request(RequestType.LOGOUT, new FakePayload("logout"), null);
    }

    // =========================================================================
    // Request factories — registration
    // =========================================================================

    /**
     * Valid registration for Alice — employee ID and name match the roster,
     * but Alice's account already exists, so the server returns
     * {@code USER_ID_TAKEN}.
     */
    public static Request registerRequestAlice() {
        return new Request(RequestType.REGISTER,
                new RegisterCredentials(ALICE_ID, ALICE_LOGIN, ALICE_PASSWORD, ALICE_NAME), null);
    }

    /**
     * Valid first-time registration for Carol — employee ID and name both
     * match the roster, and no account exists yet, so this should succeed.
     */
    public static Request registerRequestCarol() {
        return new Request(RequestType.REGISTER,
                new RegisterCredentials(CAROL_ID, CAROL_LOGIN, CAROL_PASSWORD, CAROL_NAME), null);
    }

    /**
     * Invalid registration — the employee ID belongs to Alice but the name
     * does not match.  The server should return {@code USER_ID_INVALID}
     * (do not reveal whether the ID exists to prevent enumeration).
     */
    public static Request registerRequestBadName() {
        return new Request(RequestType.REGISTER,
                new RegisterCredentials(ALICE_ID, ALICE_LOGIN, ALICE_PASSWORD, "Wrong Name"), null);
    }

    /**
     * Invalid registration — the employee ID has no entry in the roster.
     * The server should return {@code USER_ID_INVALID}.
     */
    public static Request registerRequestUnknownId() {
        return new Request(RequestType.REGISTER,
                new RegisterCredentials("9999", "nobody", "p@ss", "Nobody"), null);
    }

    // =========================================================================
    // Request factories — messaging
    // =========================================================================

    public static Request messageRequest(long conversationId, String text) {
        return new Request(RequestType.MESSAGE, new RawMessage(text, conversationId), null);
    }

    // =========================================================================
    // Response factories — authentication
    // =========================================================================

    public static Response pongResponse() {
        return new Response(ResponseType.PONG, new FakePayload("pong"));
    }

    public static Response loginSuccessResponseAlice() {
        LoginResult lr = new LoginResult(
                LoginStatus.SUCCESS, aliceInfo(), new ArrayList<>());
        return new Response(ResponseType.LOGIN_RESULT, lr);
    }

    public static Response loginFailedResponse() {
        LoginResult lr = new LoginResult(LoginStatus.INVALID_CREDENTIALS);
        return new Response(ResponseType.LOGIN_RESULT, lr);
    }

    // =========================================================================
    // Response factories — registration
    // =========================================================================

    /** Registration accepted; account created for the submitted employee. */
    public static Response registerSuccessResponse() {
        return new Response(ResponseType.REGISTER_RESULT,
                new RegisterResult(RegisterStatus.SUCCESS));
    }

    /**
     * Registration rejected: the employee ID is not in the roster, or the
     * name does not match (same error code — avoids ID enumeration).
     */
    public static Response registerUserIdInvalidResponse() {
        return new Response(ResponseType.REGISTER_RESULT,
                new RegisterResult(RegisterStatus.USER_ID_INVALID));
    }

    /** Registration rejected: an account for this employee ID already exists. */
    public static Response registerUserIdTakenResponse() {
        return new Response(ResponseType.REGISTER_RESULT,
                new RegisterResult(RegisterStatus.USER_ID_TAKEN));
    }

    // =========================================================================
    // Batch helpers
    // =========================================================================

    /** Ordered batch of distinct responses — used to verify FIFO delivery. */
    public static Response[] orderedBatch() {
        return new Response[] {
                new Response(ResponseType.PONG, new FakePayload("batch-0")),
                new Response(ResponseType.PONG, new FakePayload("batch-1")),
                new Response(ResponseType.PONG, new FakePayload("batch-2")),
                new Response(ResponseType.PONG, new FakePayload("batch-3")),
                new Response(ResponseType.PONG, new FakePayload("batch-4")),
        };
    }

    // =========================================================================

    private NetworkingSeedData() { /* no instances */ }
}
