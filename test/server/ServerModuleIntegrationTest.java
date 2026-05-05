package server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import shared.enums.LoginStatus;
import shared.enums.RegisterStatus;
import shared.enums.RequestType;
import shared.enums.ResponseType;
import shared.enums.UserType;
import shared.networking.ConnectionListener;
import shared.networking.Request;
import shared.networking.Response;
import shared.networking.User;
import shared.payload.AdminConversationQuery;
import shared.payload.AdminConversationResult;
import shared.payload.AdminViewConversationQuery;
import shared.payload.Conversation;
import shared.payload.ConversationMetadata;
import shared.payload.CreateConversationPayload;
import shared.payload.JoinConversationPayload;
import shared.payload.LeaveConversationPayload;
import shared.payload.LoginCredentials;
import shared.payload.Message;
import shared.payload.LoginResult;
import shared.payload.RawMessage;
import shared.payload.RegisterCredentials;
import shared.payload.RegisterResult;

/**
 * Integration coverage for the server JVM stack: real {@link ServerController} with real
 * {@link DataManager}, {@link ConnectionListener} (ephemeral port), and broadcaster thread.
 * Flows follow {@code docs/Product-Demo-Procedure.md} (Jon / Quan / Harumi / John Doe IDs).
 */
@Timeout(value = 45, unit = TimeUnit.SECONDS)
class ServerModuleIntegrationTest {

    @TempDir
    Path tempRoot;

    private ServerController server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    private static void preparePresentationProcedureRoot(Path root) throws IOException {
        Path serverData = root.resolve("server_data");
        Path authorizedIds = serverData.resolve("authorized_ids");
        Files.createDirectories(authorizedIds);
        Files.writeString(authorizedIds.resolve("authorized_users.txt"), """
                J8D3K7P1LX,John Doe
                Q7M2X9K4LP,Jon Yoon
                N4R8T1BZQK,Quan Li
                V3P6L0WQ9D,Harumi Sato
                """);
        Files.writeString(authorizedIds.resolve("authorized_admins.txt"), "Q7M2X9K4LP\n");
        Files.writeString(serverData.resolve("server_config.txt"), "");
        Files.createDirectories(root.resolve("conversation_data"));
        Files.createDirectories(root.resolve("user_data"));
    }

    private static ConnectionListener readListener(ServerController c) throws Exception {
        Field f = ServerController.class.getDeclaredField("connectionListener");
        f.setAccessible(true);
        return (ConnectionListener) f.get(c);
    }

    @Test
    void presentationProcedure_fullServerStack() throws Exception {
        Path dataRoot = tempRoot.resolve("presentation_procedure");
        preparePresentationProcedureRoot(dataRoot);

        server = ServerController.getInstance(null, 0, dataRoot.toString());
        assertNotNull(server, "singleton should construct on first getInstance");

        ConnectionListener listener = readListener(server);
        int port = listener.getLocalPort();
        assertTrue(port > 0, "server should bind an ephemeral TCP port (ConnectionListener)");

        // UC-01 — register John Doe; duplicate → USER_ID_TAKEN
        Response johnOk = server.processRequest(new Request(RequestType.REGISTER,
                new RegisterCredentials("J8D3K7P1LX", "user123", "a", "John Doe"), null));
        assertEquals(ResponseType.REGISTER_RESULT, johnOk.getType());
        assertEquals(RegisterStatus.SUCCESS, ((RegisterResult) johnOk.getPayload()).getRegisterStatus());

        Response johnDup = server.processRequest(new Request(RequestType.REGISTER,
                new RegisterCredentials("J8D3K7P1LX", "user123b", "a", "John Doe"), null));
        assertEquals(RegisterStatus.USER_ID_TAKEN, ((RegisterResult) johnDup.getPayload()).getRegisterStatus());

        // Register demo heroes (seeded identities in procedure)
        assertSuccessRegister(server, new RegisterCredentials("Q7M2X9K4LP", "jon", "a", "Jon Yoon"));
        assertSuccessRegister(server, new RegisterCredentials("N4R8T1BZQK", "quan", "a", "Quan Li"));
        assertSuccessRegister(server, new RegisterCredentials("V3P6L0WQ9D", "harumi", "a", "Harumi Sato"));

        // UC-02 — login Jon and Quan
        assertSuccessLogin(server, "jon", "a");
        assertSuccessLogin(server, "quan", "a");

        // UC-07 — private Jon + Quan
        ArrayList<User.UserInfo> pair = new ArrayList<>();
        pair.add(new User("Q7M2X9K4LP", "Jon Yoon", "jon", "a", UserType.ADMIN).toUserInfo());
        pair.add(new User("N4R8T1BZQK", "Quan Li", "quan", "a", UserType.USER).toUserInfo());
        Response createDm = server.processRequest(new Request(RequestType.CREATE_CONVERSATION,
                new CreateConversationPayload(pair), "Q7M2X9K4LP"));
        assertEquals(ResponseType.CONVERSATION, createDm.getType());
        long dmId = ((Conversation) createDm.getPayload()).getConversationId();

        // UC-03 — message in DM
        Response msg = server.processRequest(new Request(RequestType.MESSAGE,
                new RawMessage("Hi Quan (integration)", dmId), "Q7M2X9K4LP"));
        assertEquals(ResponseType.MESSAGE, msg.getType());
        assertEquals("Q7M2X9K4LP", ((Message) msg.getPayload()).getSenderId());

        // UC-11 — admin search, read-only view, silent join
        Response adminList = server.processRequest(new Request(RequestType.ADMIN_CONVERSATION_QUERY,
                new AdminConversationQuery("N4R8T1BZQK"), "Q7M2X9K4LP"));
        assertEquals(ResponseType.ADMIN_CONVERSATION_RESULT, adminList.getType());
        AdminConversationResult meta = (AdminConversationResult) adminList.getPayload();
        assertTrue(conversationIds(meta).contains(dmId), "admin list should include Jon/Quan DM");

        Response adminView = server.processRequest(new Request(RequestType.ADMIN_VIEW_CONVERSATION,
                new AdminViewConversationQuery(dmId), "Q7M2X9K4LP"));
        assertEquals(ResponseType.ADMIN_VIEW_CONVERSATION_RESULT, adminView.getType());
        assertNotNull(adminView.getPayload());
        assertEquals(dmId, ((Conversation) adminView.getPayload()).getConversationId());

        Response join = server.processRequest(new Request(RequestType.JOIN_CONVERSATION,
                new JoinConversationPayload(dmId), "Q7M2X9K4LP"));
        assertEquals(ResponseType.CONVERSATION, join.getType());

        // UC-09 — Quan leaves DM
        Response leave = server.processRequest(new Request(RequestType.LEAVE_CONVERSATION,
                new LeaveConversationPayload(dmId), "N4R8T1BZQK"));
        assertEquals(ResponseType.LEAVE_RESULT, leave.getType());
    }

    private static void assertSuccessRegister(ServerController server, RegisterCredentials rc) {
        Response r = server.processRequest(new Request(RequestType.REGISTER, rc, null));
        assertEquals(ResponseType.REGISTER_RESULT, r.getType());
        assertEquals(RegisterStatus.SUCCESS, ((RegisterResult) r.getPayload()).getRegisterStatus(), rc.getUserId());
    }

    private static void assertSuccessLogin(ServerController server, String login, String password) {
        Response r = server.processRequest(new Request(RequestType.LOGIN, new LoginCredentials(login, password), null));
        assertEquals(ResponseType.LOGIN_RESULT, r.getType());
        assertEquals(LoginStatus.SUCCESS, ((LoginResult) r.getPayload()).getLoginStatus(), login);
    }

    private static List<Long> conversationIds(AdminConversationResult result) {
        ArrayList<Long> ids = new ArrayList<>();
        for (ConversationMetadata m : result.getConversations()) {
            ids.add(m.getConversationId());
        }
        return ids;
    }
}
