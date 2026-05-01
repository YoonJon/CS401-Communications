package server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import shared.enums.LoginStatus;
import shared.enums.RegisterStatus;
import shared.enums.RequestType;
import shared.enums.ResponseType;
import shared.enums.UserType;
import shared.networking.Request;
import shared.networking.Response;
import shared.networking.User;
import shared.payload.Conversation;
import shared.payload.CreateConversationPayload;
import shared.payload.LoginCredentials;
import shared.payload.LoginResult;
import shared.payload.Message;
import shared.payload.RawMessage;
import shared.payload.RegisterCredentials;
import shared.payload.RegisterResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for issue #214 — verifies {@link DataManager#handleLogin} returns the
 * caller's conversation list in a deterministic, most-recent-first order. The pre-fix path
 * iterated a {@code Set<Long>} so order varied across JVM runs and across successive logins.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class DataManagerLoginConversationOrderTest {

    private static final String TEST_DATA_DIR_NAME = "test_data";

    @TempDir
    Path tempRoot;

    private DataManager dm;

    @BeforeEach
    void initLayout() throws IOException {
        Path testDataRoot = tempRoot.resolve(TEST_DATA_DIR_NAME);
        prepareMinimalDataTree(testDataRoot);
        dm = new DataManager(testDataRoot.toString());
    }

    @AfterEach
    void shutdown() {
        if (dm != null) {
            dm.close();
            dm = null;
        }
    }

    private static void prepareMinimalDataTree(Path root) throws IOException {
        Path serverData = root.resolve("server_data");
        Path authorizedIds = serverData.resolve("authorized_ids");
        Files.createDirectories(authorizedIds);
        Files.writeString(authorizedIds.resolve("authorized_users.txt"), """
                u1,Alice
                u2,Bob
                u3,Carol
                u4,Dan
                u5,Eve
                """);
        Files.writeString(authorizedIds.resolve("authorized_admins.txt"), "");
        Files.writeString(serverData.resolve("server_config.txt"), "");
        Files.createDirectories(root.resolve("conversation_data"));
        Files.createDirectories(root.resolve("user_data"));
    }

    private static User.UserInfo ui(String userId, String displayName) {
        return new User(userId, displayName, userId + "_login", "pw", UserType.USER).toUserInfo();
    }

    private static ArrayList<User.UserInfo> roster(User.UserInfo... parts) {
        ArrayList<User.UserInfo> list = new ArrayList<>();
        for (User.UserInfo u : parts) list.add(u);
        return list;
    }

    private void registerAll() {
        RegisterCredentials[] regs = new RegisterCredentials[]{
                new RegisterCredentials("u1", "alice", "p1", "Alice"),
                new RegisterCredentials("u2", "bob",   "p2", "Bob"),
                new RegisterCredentials("u3", "carol", "p3", "Carol"),
                new RegisterCredentials("u4", "dan",   "p4", "Dan"),
                new RegisterCredentials("u5", "eve",   "p5", "Eve"),
        };
        for (RegisterCredentials rc : regs) {
            Response r = dm.handleRegister(new Request(RequestType.REGISTER, rc, null));
            assertEquals(RegisterStatus.SUCCESS,
                    ((RegisterResult) r.getPayload()).getRegisterStatus(),
                    "register " + rc.getUserId());
        }
    }

    private LoginResult loginAlice() {
        Response r = dm.handleLogin(new Request(RequestType.LOGIN,
                new LoginCredentials("alice", "p1"), null));
        assertEquals(ResponseType.LOGIN_RESULT, r.getType());
        LoginResult lr = (LoginResult) r.getPayload();
        assertEquals(LoginStatus.SUCCESS, lr.getLoginStatus());
        return lr;
    }

    private long createGroup(User.UserInfo... members) {
        Response r = dm.handleCreateConversation(new Request(RequestType.CREATE_CONVERSATION,
                new CreateConversationPayload(roster(members)), members[0].getUserId()));
        assertEquals(ResponseType.CONVERSATION, r.getType(), "create group");
        return ((Conversation) r.getPayload()).getConversationId();
    }

    private long sendMessage(String senderId, long conversationId, String text) {
        Response r = dm.handleSendMessage(new Request(RequestType.MESSAGE,
                new RawMessage(text, conversationId), senderId));
        assertEquals(ResponseType.MESSAGE, r.getType());
        return ((Message) r.getPayload()).getSequenceNumber();
    }

    @Test
    void login_returnsConversationsSortedByLatestMessageDesc() {
        registerAll();

        long c1 = createGroup(ui("u1", "Alice"), ui("u2", "Bob"),   ui("u3", "Carol"));
        long s1 = sendMessage("u1", c1, "first in c1");

        long c2 = createGroup(ui("u1", "Alice"), ui("u2", "Bob"),   ui("u4", "Dan"));
        long s2 = sendMessage("u1", c2, "first in c2");

        long c3 = createGroup(ui("u1", "Alice"), ui("u3", "Carol"), ui("u4", "Dan"));
        long s3 = sendMessage("u1", c3, "first in c3");

        // Sanity: counters monotonic, so s3 > s2 > s1 — c3 must come first, c1 last.
        assertTrue(s3 > s2 && s2 > s1, "sequence numbers must be strictly increasing");

        List<Conversation> list = loginAlice().getConversationList();
        assertEquals(3, list.size(), "all three conversations returned");
        assertEquals(c3, list.get(0).getConversationId(), "newest activity first");
        assertEquals(c2, list.get(1).getConversationId(), "middle activity second");
        assertEquals(c1, list.get(2).getConversationId(), "oldest activity last");
    }

    @Test
    void login_emptyConversationsYieldsEmptyList() {
        registerAll();
        // Alice has no conversations.
        LoginResult lr = loginAlice();
        assertNotNull(lr.getConversationList(), "list never null");
        assertTrue(lr.getConversationList().isEmpty(), "no conversations → empty list");
    }

    @Test
    void login_messagelessConversations_orderedByConversationIdDesc() {
        registerAll();

        // Three groups, no messages sent. Tie-break is conversationId desc.
        long c1 = createGroup(ui("u1", "Alice"), ui("u2", "Bob"),   ui("u3", "Carol"));
        long c2 = createGroup(ui("u1", "Alice"), ui("u2", "Bob"),   ui("u4", "Dan"));
        long c3 = createGroup(ui("u1", "Alice"), ui("u3", "Carol"), ui("u4", "Dan"));
        assertTrue(c3 > c2 && c2 > c1, "conversation ids must be strictly increasing");

        List<Conversation> list = loginAlice().getConversationList();
        assertEquals(3, list.size());
        assertEquals(c3, list.get(0).getConversationId(), "highest convId first");
        assertEquals(c2, list.get(1).getConversationId());
        assertEquals(c1, list.get(2).getConversationId(), "lowest convId last");
    }

    @Test
    void login_isStableAcrossInvocations() {
        registerAll();

        long c1 = createGroup(ui("u1", "Alice"), ui("u2", "Bob"),   ui("u3", "Carol"));
        sendMessage("u1", c1, "hello c1");
        long c2 = createGroup(ui("u1", "Alice"), ui("u2", "Bob"),   ui("u4", "Dan"));
        sendMessage("u1", c2, "hello c2");
        long c3 = createGroup(ui("u1", "Alice"), ui("u3", "Carol"), ui("u4", "Dan"));
        // c3 has no messages — exercise the mixed-tie path under repeated logins.

        List<Long> first  = idsOf(loginAlice().getConversationList());
        List<Long> second = idsOf(loginAlice().getConversationList());
        List<Long> third  = idsOf(loginAlice().getConversationList());

        assertEquals(first, second, "two successive logins must return identical order");
        assertEquals(second, third, "three successive logins must return identical order");
        assertEquals(3, first.size());
        // Sanity: c2's last message seq > c1's last message seq, so c2 precedes c1.
        assertTrue(first.indexOf(c2) < first.indexOf(c1), "c2 (newer activity) precedes c1");
        assertTrue(first.contains(c3), "messageless c3 still appears in the list");
    }

    private static List<Long> idsOf(List<Conversation> convs) {
        List<Long> ids = new ArrayList<>();
        for (Conversation c : convs) ids.add(c.getConversationId());
        return ids;
    }
}
