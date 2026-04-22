package client;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import shared.enums.*;
import shared.networking.*;
import shared.networking.User.UserInfo;
import shared.networking.fixtures.FakeServerController;
import shared.networking.fixtures.NetworkingSeedData;
import shared.payload.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for {@link ClientController}.
 *
 * <h3>Structure</h3>
 * <ul>
 *   <li>Groups 1–12 (outer class): pure unit tests — call {@code processResponse()}
 *       directly (package-private), no socket, null GUI. Fast and deterministic.</li>
 *   <li>Group 13 ({@link RequestConstructionTests}): loopback integration tests —
 *       verifies each action method sends the correct {@link Request} to a
 *       {@link FakeServerController} over a real TCP socket.</li>
 * </ul>
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class ClientControllerTest {

    // =========================================================================
    // Helpers — build test data without a live server
    // =========================================================================

    private static UserInfo alice() { return NetworkingSeedData.aliceInfo(); }
    private static UserInfo bob()   { return NetworkingSeedData.bobInfo(); }
    private static UserInfo carol() { return NetworkingSeedData.carolInfo(); }

    /** Builds a Conversation with a fixed id and the given members. */
    private static Conversation conv(long id, UserInfo... members) {
        ArrayList<UserInfo> p = new ArrayList<>();
        for (UserInfo u : members) p.add(u);
        return new Conversation(id, p);
    }

    /** LOGIN_RESULT/SUCCESS for Alice, containing one conversation (id=100). */
    private static Response loginSuccessAliceWithConv() {
        ArrayList<Conversation> convs = new ArrayList<>();
        convs.add(conv(100L, alice(), bob()));
        return new Response(ResponseType.LOGIN_RESULT,
                new LoginResult(LoginStatus.SUCCESS, alice(), convs));
    }

    /** LOGIN_RESULT/SUCCESS for Alice with a null conversation list. */
    private static Response loginSuccessAliceNullConvs() {
        return new Response(ResponseType.LOGIN_RESULT,
                new LoginResult(LoginStatus.SUCCESS, alice(), null));
    }

    /** LOGIN_RESULT/INVALID_CREDENTIALS */
    private static Response loginFailed() {
        return new Response(ResponseType.LOGIN_RESULT,
                new LoginResult(LoginStatus.INVALID_CREDENTIALS));
    }

    private static Response registerSuccess() {
        return new Response(ResponseType.REGISTER_RESULT,
                new RegisterResult(RegisterStatus.SUCCESS));
    }

    private static Response registerFailed() {
        return new Response(ResponseType.REGISTER_RESULT,
                new RegisterResult(RegisterStatus.USER_ID_TAKEN));
    }

    private static Response logoutResult() {
        return new Response(ResponseType.LOGOUT_RESULT);
    }

    private static Response messageFor(long convId, String text) {
        Message m = new Message(text, 1L, new Date(), NetworkingSeedData.ALICE_ID, convId);
        return new Response(ResponseType.MESSAGE, m);
    }

    private static Response conversationResponse(long id, UserInfo... members) {
        return new Response(ResponseType.CONVERSATION, conv(id, members));
    }

    private static Response leaveResult(long convId) {
        return new Response(ResponseType.LEAVE_RESULT, new LeaveResult(convId));
    }

    private static Response adminConversationResult() {
        ArrayList<UserInfo> p = new ArrayList<>();
        p.add(alice()); p.add(bob());
        ArrayList<ConversationMetadata> list = new ArrayList<>();
        list.add(new ConversationMetadata(200L, p, p, ConversationType.PRIVATE));
        return new Response(ResponseType.ADMIN_CONVERSATION_RESULT,
                new AdminConversationResult(list));
    }

    /** Creates a no-GUI controller — suitable for pure unit tests (no Swing window). */
    private static ClientController headless() {
        return new ClientController("localhost", 0, null);
    }

    // =========================================================================
    // 1. LOGIN_RESULT — success path (5 tests)
    // =========================================================================

    @Test
    void loginSuccess_setsLoggedInFlag() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        assertTrue(c.isLoggedIn());
    }

    @Test
    void loginSuccess_setsCurrentUser() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        assertNotNull(c.getCurrentUserInfo());
        assertEquals(NetworkingSeedData.ALICE_ID,   c.getCurrentUserInfo().getUserId());
        assertEquals(NetworkingSeedData.ALICE_NAME, c.getCurrentUserInfo().getName());
    }

    @Test
    void loginSuccess_populatesConversationList() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        List<Conversation> convs = c.getFilteredConversationList(null);
        assertEquals(1, convs.size());
        assertEquals(100L, convs.get(0).getConversationId());
    }

    @Test
    void loginSuccess_nullConversationListTreatedAsEmpty() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceNullConvs());
        assertTrue(c.isLoggedIn());
        assertEquals(0, c.getFilteredConversationList(null).size());
    }

    @Test
    void loginFailed_doesNotSetLoggedIn() {
        ClientController c = headless();
        c.processResponse(loginFailed());
        assertFalse(c.isLoggedIn());
        assertNull(c.getCurrentUserInfo());
    }

    // =========================================================================
    // 2. REGISTER_RESULT (2 tests)
    // =========================================================================

    @Test
    void registerSuccess_doesNotAutoLogin() {
        ClientController c = headless();
        c.processResponse(registerSuccess());
        assertFalse(c.isLoggedIn());
        assertNull(c.getCurrentUserInfo());
    }

    @Test
    void registerFailed_stateUnchanged() {
        ClientController c = headless();
        c.processResponse(registerFailed());
        assertFalse(c.isLoggedIn());
        assertNull(c.getCurrentUserInfo());
    }

    // =========================================================================
    // 3. LOGOUT_RESULT (4 tests)
    // =========================================================================

    @Test
    void logoutResult_clearsLoggedInFlag() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        c.processResponse(logoutResult());
        assertFalse(c.isLoggedIn());
    }

    @Test
    void logoutResult_clearsCurrentUser() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        c.processResponse(logoutResult());
        assertNull(c.getCurrentUserInfo());
    }

    @Test
    void logoutResult_clearsConversationList() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        c.processResponse(logoutResult());
        assertEquals(0, c.getFilteredConversationList(null).size());
    }

    @Test
    void logoutResult_resetsCurrentConversationId() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        c.setCurrentConversationId(100L);
        c.processResponse(logoutResult());
        assertEquals(0L, c.getCurrentConversationId());
    }

    // =========================================================================
    // 4. MESSAGE response (2 tests)
    // =========================================================================

    @Test
    void message_appendedToMatchingConversation() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv()); // adds conv 100
        c.processResponse(messageFor(100L, "Hello"));
        Conversation conv = c.getFilteredConversationList(null).get(0);
        assertEquals(1, conv.getMessages().size());
        assertEquals("Hello", conv.getMessages().get(0).getText());
    }

    @Test
    void message_ignoredForUnknownConversation() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        assertDoesNotThrow(() -> c.processResponse(messageFor(999L, "orphan")));
        // count of conversations must not change
        assertEquals(1, c.getFilteredConversationList(null).size());
    }

    // =========================================================================
    // 5. CONVERSATION response (2 tests)
    // =========================================================================

    @Test
    void conversation_newConversationAddedToList() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv()); // conv 100
        c.processResponse(conversationResponse(200L, alice(), carol())); // new
        assertEquals(2, c.getFilteredConversationList(null).size());
    }

    @Test
    void conversation_existingConversationUpdatedInPlace() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv()); // conv 100: alice+bob (2 people)
        // Replace with a version that also has carol (3 people)
        c.processResponse(conversationResponse(100L, alice(), bob(), carol()));
        List<Conversation> convs = c.getFilteredConversationList(null);
        assertEquals(1, convs.size());                           // still 1 conversation
        assertEquals(3, convs.get(0).getParticipants().size()); // updated to 3 participants
    }

    // =========================================================================
    // 6. LEAVE_RESULT response (3 tests)
    // =========================================================================

    @Test
    void leaveResult_removesConversationFromList() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv()); // conv 100
        c.processResponse(leaveResult(100L));
        assertEquals(0, c.getFilteredConversationList(null).size());
    }

    @Test
    void leaveResult_clearsCurrentConversationIdWhenMatching() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        c.setCurrentConversationId(100L);
        c.processResponse(leaveResult(100L));
        assertEquals(0L, c.getCurrentConversationId());
        assertNull(c.getCurrentConversation());
    }

    @Test
    void leaveResult_preservesCurrentConversationIdWhenDifferent() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());              // conv 100
        c.processResponse(conversationResponse(200L, alice(), bob())); // conv 200
        c.setCurrentConversationId(200L);
        c.processResponse(leaveResult(100L)); // leave a different conv
        assertEquals(200L, c.getCurrentConversationId());
    }

    // =========================================================================
    // 7. ADMIN_CONVERSATION_RESULT response (1 test)
    // =========================================================================

    @Test
    void adminResult_populatesAdminSearchList() {
        ClientController c = headless();
        c.processResponse(adminConversationResult()); // 1 entry: conv 200
        List<Conversation> results = c.getFilteredAdminConversationSearch(null);
        assertEquals(1, results.size());
        assertEquals(200L, results.get(0).getConversationId());
    }

    // =========================================================================
    // 8. PONG / CONNECTED / null edge cases (3 tests)
    // =========================================================================

    @Test
    void pong_handledWithoutException() {
        assertDoesNotThrow(() -> headless().processResponse(
                new Response(ResponseType.PONG)));
    }

    @Test
    void connected_handledWithoutException() {
        assertDoesNotThrow(() -> headless().processResponse(
                new Response(ResponseType.CONNECTED)));
    }

    @Test
    void nullResponse_ignoredSafely() {
        assertDoesNotThrow(() -> headless().processResponse(null));
    }

    // =========================================================================
    // 9. getCurrentConversation (3 tests)
    // =========================================================================

    @Test
    void getCurrentConversation_returnsNullWhenNoneSelected() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        // default currentConversationId == 0
        assertNull(c.getCurrentConversation());
    }

    @Test
    void getCurrentConversation_returnsCorrectObject() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv()); // conv 100
        c.setCurrentConversationId(100L);
        assertNotNull(c.getCurrentConversation());
        assertEquals(100L, c.getCurrentConversation().getConversationId());
    }

    @Test
    void getCurrentConversation_returnsNullForUnknownId() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        c.setCurrentConversationId(999L); // non-existent
        assertNull(c.getCurrentConversation());
    }

    // =========================================================================
    // 10. getFilteredDirectory (6 tests)
    // =========================================================================

    private ClientController headlessWithDirectory() {
        ClientController c = headless();
        ArrayList<UserInfo> dir = new ArrayList<>();
        dir.add(alice()); dir.add(bob());
        c.setCurrentDirectoryForTesting(dir);
        return c;
    }

    @Test
    void filteredDirectory_blankQueryReturnsAll() {
        assertEquals(2, headlessWithDirectory().getFilteredDirectory("").size());
    }

    @Test
    void filteredDirectory_nullQueryReturnsAll() {
        assertEquals(2, headlessWithDirectory().getFilteredDirectory(null).size());
    }

    @Test
    void filteredDirectory_matchesByExactUserId() {
        List<UserInfo> r = headlessWithDirectory().getFilteredDirectory(NetworkingSeedData.ALICE_ID);
        assertEquals(1, r.size());
        assertEquals(NetworkingSeedData.ALICE_ID, r.get(0).getUserId());
    }

    @Test
    void filteredDirectory_matchesByPartialName() {
        // "lic" is a substring of "Alice"
        List<UserInfo> r = headlessWithDirectory().getFilteredDirectory("lic");
        assertEquals(1, r.size());
        assertEquals(NetworkingSeedData.ALICE_NAME, r.get(0).getName());
    }

    @Test
    void filteredDirectory_caseInsensitiveMatch() {
        assertEquals(1, headlessWithDirectory().getFilteredDirectory("ALICE").size());
    }

    @Test
    void filteredDirectory_noMatchReturnsEmpty() {
        assertEquals(0, headlessWithDirectory().getFilteredDirectory("xyz99noone").size());
    }

    // =========================================================================
    // 11. getFilteredConversationList (4 tests)
    // =========================================================================

    @Test
    void filteredConversationList_blankQueryReturnsAll() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        assertEquals(1, c.getFilteredConversationList("").size());
    }

    @Test
    void filteredConversationList_matchesByParticipantName() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv()); // alice+bob
        assertEquals(1, c.getFilteredConversationList("ali").size());
    }

    @Test
    void filteredConversationList_matchesByParticipantId() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv()); // alice id=1001, bob id=1002
        assertEquals(1, c.getFilteredConversationList(NetworkingSeedData.BOB_ID).size());
    }

    @Test
    void filteredConversationList_noMatchReturnsEmpty() {
        ClientController c = headless();
        c.processResponse(loginSuccessAliceWithConv());
        assertEquals(0, c.getFilteredConversationList("nosuchmatch999").size());
    }

    // =========================================================================
    // 12. getFilteredAdminConversationSearch (3 tests)
    // =========================================================================

    @Test
    void filteredAdminSearch_blankQueryReturnsAll() {
        ClientController c = headless();
        c.processResponse(adminConversationResult());
        assertEquals(1, c.getFilteredAdminConversationSearch("").size());
    }

    @Test
    void filteredAdminSearch_matchesByParticipantName() {
        ClientController c = headless();
        c.processResponse(adminConversationResult()); // alice+bob in conv 200
        assertEquals(1, c.getFilteredAdminConversationSearch("alice").size());
    }

    @Test
    void filteredAdminSearch_noMatchReturnsEmpty() {
        ClientController c = headless();
        c.processResponse(adminConversationResult());
        assertEquals(0, c.getFilteredAdminConversationSearch("nosuch99").size());
    }

    // =========================================================================
    // 13. Integration — request construction over real loopback socket (14 tests)
    // =========================================================================

    @Nested
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    class RequestConstructionTests {

        private FakeServerController fakeServer;
        private ConnectionListener   listener;
        private Thread               listenerThread;
        private int                  port;

        /**
         * FakeServerController passes a temp path to DataManager, which warns about
         * missing server_data/authorized_ids/ but then tries to createNewFile() for
         * server_config.txt — that fails if the parent dir doesn't exist.
         * Pre-create the minimum required structure once per class.
         */
        @BeforeAll
        static void createTempServerDirs() throws Exception {
            java.io.File base = new java.io.File(
                    System.getProperty("java.io.tmpdir"), "cs401-fake-server-data");
            new java.io.File(base, "server_data/authorized_ids").mkdirs();
            new java.io.File(base, "server_data/authorized_ids/authorized_users.txt").createNewFile();
            new java.io.File(base, "server_data/authorized_ids/authorized_admins.txt").createNewFile();
        }

        @BeforeEach
        void startServer() throws Exception {
            fakeServer     = new FakeServerController();
            listener       = new ConnectionListener(0, fakeServer);
            listenerThread = new Thread(() -> listener.listen(), "test-listener");
            listenerThread.setDaemon(true);
            listenerThread.start();
            port = listener.getLocalPort();
            assertTrue(port > 0, "Listener did not bind in time");
        }

        @AfterEach
        void stopServer() throws Exception {
            listener.close();
            listenerThread.join(3_000);
        }

        private void waitFor(BooleanSupplier cond, long maxMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + maxMs;
            while (!cond.getAsBoolean() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
        }

        /**
         * Connects a controller, performs login, waits until isLoggedIn() is true,
         * then clears the received-request log so tests only see their own action.
         */
        private ClientController loggedIn() throws Exception {
            fakeServer.setRequestHandler(req -> {
                if (req.getType() == RequestType.LOGIN)
                    return NetworkingSeedData.loginSuccessResponseAlice();
                return NetworkingSeedData.pongResponse();
            });
            ClientController c = new ClientController("localhost", port, null);
            c.login(NetworkingSeedData.ALICE_LOGIN,
                    new char[]{'p','a','s','s'});
            waitFor(c::isLoggedIn, 3_000);
            assertTrue(c.isLoggedIn(), "Controller did not reach logged-in state in time");
            fakeServer.clearReceived();
            return c;
        }

        // --- register ---

        @Test
        void register_sendsRegisterRequestType() throws Exception {
            ClientController c = new ClientController("localhost", port, null);
            c.register(NetworkingSeedData.CAROL_ID, NetworkingSeedData.CAROL_NAME,
                       NetworkingSeedData.CAROL_LOGIN, NetworkingSeedData.CAROL_PASSWORD.toCharArray());
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            assertEquals(RequestType.REGISTER, fakeServer.getReceived().get(0).getType());
        }

        @Test
        void register_payloadHasCorrectFields() throws Exception {
            ClientController c = new ClientController("localhost", port, null);
            c.register(NetworkingSeedData.CAROL_ID, NetworkingSeedData.CAROL_NAME,
                       NetworkingSeedData.CAROL_LOGIN, NetworkingSeedData.CAROL_PASSWORD.toCharArray());
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            RegisterCredentials creds =
                    (RegisterCredentials) fakeServer.getReceived().get(0).getPayload();
            assertEquals(NetworkingSeedData.CAROL_ID,    creds.getUserId());
            assertEquals(NetworkingSeedData.CAROL_LOGIN, creds.getLoginName());
            assertEquals(NetworkingSeedData.CAROL_NAME,  creds.getName());
        }

        // --- login ---

        @Test
        void login_sendsLoginRequestType() throws Exception {
            ClientController c = new ClientController("localhost", port, null);
            c.login(NetworkingSeedData.ALICE_LOGIN, new char[]{'p','a'});
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            assertEquals(RequestType.LOGIN, fakeServer.getReceived().get(0).getType());
        }

        @Test
        void login_payloadHasCorrectLoginName() throws Exception {
            ClientController c = new ClientController("localhost", port, null);
            c.login(NetworkingSeedData.ALICE_LOGIN, new char[]{'p','a'});
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            LoginCredentials creds =
                    (LoginCredentials) fakeServer.getReceived().get(0).getPayload();
            assertEquals(NetworkingSeedData.ALICE_LOGIN, creds.getLoginName());
        }

        // --- logout ---

        @Test
        void logout_sendsLogoutRequestType() throws Exception {
            ClientController c = loggedIn();
            c.logout();
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            assertEquals(RequestType.LOGOUT, fakeServer.getReceived().get(0).getType());
        }

        @Test
        void logout_senderIdIsCurrentUserId() throws Exception {
            ClientController c = loggedIn();
            c.logout();
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            assertEquals(NetworkingSeedData.ALICE_ID,
                    fakeServer.getReceived().get(0).getSenderId());
        }

        // --- sendMessage ---

        @Test
        void sendMessage_sendsMessageRequestType() throws Exception {
            ClientController c = loggedIn();
            c.sendMessage(100L, "hello");
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            assertEquals(RequestType.MESSAGE, fakeServer.getReceived().get(0).getType());
        }

        @Test
        void sendMessage_payloadHasCorrectTextAndConversationId() throws Exception {
            ClientController c = loggedIn();
            c.sendMessage(100L, "hello");
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            RawMessage rm = (RawMessage) fakeServer.getReceived().get(0).getPayload();
            assertEquals("hello", rm.getText());
            assertEquals(100L,    rm.getTargetConversationId());
        }

        // --- createConversation ---

        @Test
        void createConversation_sendsCreateConversationRequestType() throws Exception {
            ClientController c = loggedIn();
            ArrayList<UserInfo> p = new ArrayList<>();
            p.add(NetworkingSeedData.bobInfo());
            c.createConversation(p);
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            assertEquals(RequestType.CREATE_CONVERSATION,
                    fakeServer.getReceived().get(0).getType());
        }

        // --- addToConversation ---

        @Test
        void addToConversation_sendsAddParticipantRequestType() throws Exception {
            ClientController c = loggedIn();
            ArrayList<UserInfo> p = new ArrayList<>();
            p.add(NetworkingSeedData.carolInfo());
            c.addToConversation(p, 100L);
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            assertEquals(RequestType.ADD_PARTICIPANT,
                    fakeServer.getReceived().get(0).getType());
        }

        // --- leaveConversation ---

        @Test
        void leaveConversation_sendsLeaveConversationRequestType() throws Exception {
            ClientController c = loggedIn();
            c.leaveConversation(100L);
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            assertEquals(RequestType.LEAVE_CONVERSATION,
                    fakeServer.getReceived().get(0).getType());
        }

        @Test
        void leaveConversation_payloadHasCorrectConversationId() throws Exception {
            ClientController c = loggedIn();
            c.leaveConversation(100L);
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            LeaveConversationPayload lp =
                    (LeaveConversationPayload) fakeServer.getReceived().get(0).getPayload();
            assertEquals(100L, lp.getTargetConversationId());
        }

        // --- joinConversation ---

        @Test
        void joinConversation_sendsJoinConversationRequestType() throws Exception {
            ClientController c = loggedIn();
            c.joinConversation(300L);
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            assertEquals(RequestType.JOIN_CONVERSATION,
                    fakeServer.getReceived().get(0).getType());
        }

        // --- adminGetUserConversations ---

        @Test
        void adminGetUserConversations_sendsAdminConversationQueryType() throws Exception {
            ClientController c = loggedIn();
            c.adminGetUserConversations(NetworkingSeedData.BOB_ID);
            waitFor(() -> !fakeServer.getReceived().isEmpty(), 2_000);
            assertEquals(RequestType.ADMIN_CONVERSATION_QUERY,
                    fakeServer.getReceived().get(0).getType());
        }

        // --- guard: unauthenticated actions are no-ops ---

        @Test
        void actionMethods_noOpWhenNotLoggedIn() throws Exception {
            ClientController c = new ClientController("localhost", port, null);
            // none of these should send a request
            c.logout();
            c.sendMessage(100L, "text");
            c.createConversation(new ArrayList<>());
            c.leaveConversation(100L);
            c.joinConversation(100L);
            c.adminGetUserConversations("anyId");
            Thread.sleep(300);
            assertTrue(fakeServer.getReceived().isEmpty(),
                    "Unauthenticated action methods must not send any request");
        }
    }
}
