package server;

import server.DataManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import shared.enums.ConversationType;
import shared.enums.LoginStatus;
import shared.enums.RegisterStatus;
import shared.enums.RequestType;
import shared.enums.ResponseType;
import shared.enums.UserType;
import shared.networking.Request;
import shared.networking.Response;
import shared.networking.User;
import shared.payload.AddToConversationPayload;
import shared.payload.AdminConversationQuery;
import shared.payload.AdminConversationResult;
import shared.payload.Conversation;
import shared.payload.ConversationMetadata;
import shared.payload.CreateConversationPayload;
import shared.payload.JoinConversationPayload;
import shared.payload.LeaveConversationPayload;
import shared.payload.LoginCredentials;
import shared.payload.LoginResult;
import shared.payload.Message;
import shared.payload.RawMessage;
import shared.payload.RegisterCredentials;
import shared.payload.RegisterResult;
import shared.payload.UpdateReadMessages;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit acceptance-style coverage for {@link DataManager}: each handler is exercised through
 * {@link Request}/{@link Response} the same way production request handling delegates to it.
 *
 * <p>A minimal on-disk layout is seeded under {@link TempDir}{@code /test_data/} (same relative paths
 * as production under the data root): {@code server_data/authorized_ids}, blank {@code server_config.txt},
 * empty {@code conversation_data} / {@code user_data}.
 *
 * <p><b>Note:</b> conversation and message sequence counters inside {@link DataManager} use
 * JVM-static {@link java.util.concurrent.atomic.AtomicLong}s. An empty {@code server_config.txt}
 * does not reset those values. Tests avoid hard-coding conversation ids unless they assume a
 * fresh JVM or rely only on ordering within one {@link Test}.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class DataManagerTest {

    /** Matches the conventional test root folder; {@link DataManager} receives this path as its data root. */
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

    // -------------------------------------------------------------------------
    // Minimal disk layout matching DataManager expectations
    // -------------------------------------------------------------------------

    /**
     * Seeds {@code server_data}, {@code conversation_data}, and {@code user_data} under {@code root}.
     * {@code root} should be the directory passed to {@link DataManager}'s constructor (e.g. a
     * {@code test_data} folder).
     *
     * <p>Authorized rows are {@code userId,displayName} - names must match registration
     * ({@link RegisterCredentials#getName()}) for {@link DataManager#handleRegister}.
     */
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
        for (User.UserInfo u : parts) {
            list.add(u);
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // Individual handler / edge behaviours
    // -------------------------------------------------------------------------

    @Test
    void handleRegister_successAndDuplicateAndInvalid() {
        Request ok = new Request(RequestType.REGISTER,
                new RegisterCredentials("u1", "alice", "secret", "Alice"),
                null);
        Response r1 = dm.handleRegister(ok);
        assertEquals(ResponseType.REGISTER_RESULT, r1.getType());
        assertEquals(RegisterStatus.SUCCESS,
                ((RegisterResult) r1.getPayload()).getRegisterStatus());

        Response r2 = dm.handleRegister(ok);
        assertEquals(RegisterStatus.USER_ID_TAKEN,
                ((RegisterResult) r2.getPayload()).getRegisterStatus());

        Request bad = new Request(RequestType.REGISTER,
                new RegisterCredentials("unknown", "nobody", "x", "Nobody"),
                null);
        Response r3 = dm.handleRegister(bad);
        assertEquals(RegisterStatus.USER_ID_INVALID,
                ((RegisterResult) r3.getPayload()).getRegisterStatus());
    }

    @Test
    void handleLogin_successAndBadPassword() {
        dm.handleRegister(new Request(RequestType.REGISTER,
                new RegisterCredentials("u1", "alice", "goodpass", "Alice"),
                null));

        Response ok = dm.handleLogin(new Request(RequestType.LOGIN,
                new LoginCredentials("alice", "goodpass"),
                null));
        assertEquals(ResponseType.LOGIN_RESULT, ok.getType());
        LoginResult lr = (LoginResult) ok.getPayload();
        assertEquals(LoginStatus.SUCCESS, lr.getLoginStatus());
        assertNotNull(lr.getUserInfo());
        assertEquals("u1", lr.getUserInfo().getUserId());

        Response bad = dm.handleLogin(new Request(RequestType.LOGIN,
                new LoginCredentials("alice", "wrong"),
                null));
        assertEquals(LoginStatus.INVALID_CREDENTIALS,
                ((LoginResult) bad.getPayload()).getLoginStatus());
    }

    @Test
    void handleCreateConversation_emptyParticipants_returnsNull() {
        Response r = dm.handleCreateConversation(new Request(RequestType.CREATE_CONVERSATION,
                new CreateConversationPayload(new ArrayList<>()),
                "u1"));
        assertNull(r);
    }

    @Test
    void handleSendMessage_unknownConversation_returnsNull() {
        Response r = dm.handleSendMessage(new Request(RequestType.MESSAGE,
                new RawMessage("hi", 9_999_999L),
                "u1"));
        assertNull(r);
    }

    @Test
    void handleAddToConversation_unknownConversation_returnsNull() {
        Response r = dm.handleAddToConversation(new Request(RequestType.ADD_PARTICIPANT,
                new AddToConversationPayload(roster(ui("u9", "Zed")), 9_999_999L),
                "u1"));
        assertNull(r);
    }

    // -------------------------------------------------------------------------
    // End-to-end smoke: walks every handler once in a realistic order
    // -------------------------------------------------------------------------

    @Test
    void smoke_allHandlers_inOrder() {
        // --- Register u1-u5
        RegisterCredentials[] regs = new RegisterCredentials[]{
                new RegisterCredentials("u1", "alice", "p1", "Alice"),
                new RegisterCredentials("u2", "bob", "p2", "Bob"),
                new RegisterCredentials("u3", "carol", "p3", "Carol"),
                new RegisterCredentials("u4", "dan", "p4", "Dan"),
                new RegisterCredentials("u5", "eve", "p5", "Eve"),
        };
        for (RegisterCredentials rc : regs) {
            Response reg = dm.handleRegister(new Request(RequestType.REGISTER, rc, null));
            assertEquals(RegisterStatus.SUCCESS,
                    ((RegisterResult) reg.getPayload()).getRegisterStatus(),
                    "register " + rc.getUserId());
        }

        // --- Login
        Response login1 = dm.handleLogin(new Request(RequestType.LOGIN,
                new LoginCredentials("alice", "p1"), null));
        assertEquals(LoginStatus.SUCCESS, ((LoginResult) login1.getPayload()).getLoginStatus(), "login1");

        // --- Private conversation u1-u2
        Response createPrivate = dm.handleCreateConversation(new Request(RequestType.CREATE_CONVERSATION,
                new CreateConversationPayload(roster(ui("u1", "Alice"), ui("u2", "Bob"))),
                "u1"));
        assertEquals(ResponseType.CONVERSATION, createPrivate.getType(), "create private");
        Conversation private12 = (Conversation) createPrivate.getPayload();
        assertEquals(ConversationType.PRIVATE, private12.getType(), "private type");
        long convPrivate12 = private12.getConversationId();

        // --- Send message
        Response msgResp = dm.handleSendMessage(new Request(RequestType.MESSAGE,
                new RawMessage("hello world", convPrivate12),
                "u1"));
        assertEquals(ResponseType.MESSAGE, msgResp.getType(), "send message");
        Message sent = (Message) msgResp.getPayload();
        assertTrue(sent.getSequenceNumber() > 0, "sequence number");
        assertEquals(Long.valueOf(convPrivate12), Long.valueOf(sent.getConversationId()), "message conv id");
        assertEquals("u1", sent.getSenderId(), "sender");

        // --- Update read (u2 marks read)
        Response readResp = dm.handleUpdateReadMessages(new Request(RequestType.UPDATE_READ_MESSAGES,
                new UpdateReadMessages(convPrivate12, sent.getSequenceNumber()),
                "u2"));
        assertEquals(ResponseType.READ_MESSAGES_UPDATED, readResp.getType(), "read updated");

        // --- Group conversation u1,u2,u3
        Response createGroup = dm.handleCreateConversation(new Request(RequestType.CREATE_CONVERSATION,
                new CreateConversationPayload(roster(ui("u1", "Alice"), ui("u2", "Bob"), ui("u3", "Carol"))),
                "u1"));
        Conversation group123 = (Conversation) createGroup.getPayload();
        assertEquals(ConversationType.GROUP, group123.getType(), "group type");
        long convGroup = group123.getConversationId();

        // --- Add u4 to group (in place)
        Response addToGroup = dm.handleAddToConversation(new Request(RequestType.ADD_PARTICIPANT,
                new AddToConversationPayload(roster(ui("u4", "Dan")), convGroup),
                "u1"));
        assertEquals(ResponseType.CONVERSATION, addToGroup.getType(), "add to group");
        assertEquals(Integer.valueOf(4), Integer.valueOf(((Conversation) addToGroup.getPayload()).getParticipants().size()),
                "group size after add");

        // --- Private fork: add Carol to existing private u2-u3 thread (only if we create private 23 first)
        Response createPrivate23 = dm.handleCreateConversation(new Request(RequestType.CREATE_CONVERSATION,
                new CreateConversationPayload(roster(ui("u2", "Bob"), ui("u3", "Carol"))),
                "u2"));
        long convPrivate23 = ((Conversation) createPrivate23.getPayload()).getConversationId();

        Response forkResp = dm.handleAddToConversation(new Request(RequestType.ADD_PARTICIPANT,
                new AddToConversationPayload(roster(ui("u1", "Alice")), convPrivate23),
                "u2"));
        assertEquals(ResponseType.CONVERSATION, forkResp.getType(), "fork response");
        Conversation forked = (Conversation) forkResp.getPayload();
        assertNotEquals(Long.valueOf(convPrivate23), Long.valueOf(forked.getConversationId()), "fork new id");
        assertEquals(ConversationType.GROUP, forked.getType(), "fork type");
        assertEquals(Integer.valueOf(3), Integer.valueOf(forked.getParticipants().size()), "fork roster");

        // --- Leave (u2 leaves original private u1-u2)
        Response leaveResp = dm.handleLeaveConversation(new Request(RequestType.LEAVE_CONVERSATION,
                new LeaveConversationPayload(convPrivate12),
                "u2"));
        assertEquals(ResponseType.LEAVE_RESULT, leaveResp.getType(), "leave");

        // --- Admin: conversations for u3 (involved in group + private23 + fork)
        Response adminResp = dm.handleAdminConversationQuery(new Request(RequestType.ADMIN_CONVERSATION_QUERY,
                new AdminConversationQuery("u3"),
                "u1"));
        assertEquals(ResponseType.ADMIN_CONVERSATION_RESULT, adminResp.getType(), "admin");
        AdminConversationResult adminPayload = (AdminConversationResult) adminResp.getPayload();
        List<Long> adminIds = new ArrayList<>();
        for (ConversationMetadata m : adminPayload.getConversations()) {
            adminIds.add(m.getConversationId());
        }
        assertTrue(adminIds.contains(convGroup), "admin list contains group");
        assertTrue(adminIds.contains(forked.getConversationId()), "admin list contains fork");

        // --- Join u5 to group
        Response joinResp = dm.handleJoinConversation(new Request(RequestType.JOIN_CONVERSATION,
                new JoinConversationPayload(convGroup),
                "u5"));
        assertEquals(ResponseType.CONVERSATION, joinResp.getType(), "join");
        assertEquals(Long.valueOf(convGroup), Long.valueOf(((Conversation) joinResp.getPayload()).getConversationId()),
                "join conv id");

        // --- Participant query helper (used later by ServerController for fan-out)
        ArrayList<User.UserInfo> participants = dm.getParticipantList(convGroup);
        assertEquals(Integer.valueOf(4), Integer.valueOf(participants.size()), "participant list size");
    }
}
