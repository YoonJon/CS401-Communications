package server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import shared.enums.RequestType;
import shared.enums.ResponseType;
import shared.enums.LoginStatus;
import shared.enums.RegisterStatus;
import shared.enums.UserType;
import shared.networking.Request;
import shared.networking.Response;
import shared.networking.User;
import shared.payload.AdminConversationQuery;
import shared.payload.AddToConversationPayload;
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
import shared.payload.RequestPayload;
import shared.payload.UpdateReadMessages;

@Timeout(value = 20, unit = TimeUnit.SECONDS)
class ServerControllerTest {

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

    @Test
    void nullRequestReturnsNull() throws Exception {
        server = buildServerWithStub();

        assertNull(server.processRequest(null));
        assertNull(server.processRequest(new Request(null, (RequestPayload) null, "u1")));
    }

    @Test
    void registerDispatchesWithStatuses() throws Exception {
        StubDataManager stub = new StubDataManager(testDataRoot().toString());
        stub.responses.put(RequestType.REGISTER,
                new Response(ResponseType.REGISTER_RESULT, new RegisterResult(RegisterStatus.SUCCESS)));
        server = buildServerWithStub(stub);

        Response registerResponse = server.processRequest(new Request(
                RequestType.REGISTER,
                new RegisterCredentials("u1", "login1", "pw", "Alice"),
                null));

        assertEquals(ResponseType.REGISTER_RESULT, registerResponse.getType());
        assertTrue(registerResponse.getPayload() instanceof RegisterResult);
        assertEquals(RegisterStatus.SUCCESS, ((RegisterResult) registerResponse.getPayload()).getRegisterStatus());
        assertEquals(RequestType.REGISTER, stub.lastCalled);

        stub.responses.put(RequestType.REGISTER,
                new Response(ResponseType.REGISTER_RESULT, new RegisterResult(RegisterStatus.USER_ID_TAKEN)));
        Response registerTakenResponse = server.processRequest(new Request(
                RequestType.REGISTER,
                new RegisterCredentials("u1", "login1", "pw", "Alice"),
                null));
        assertEquals(RegisterStatus.USER_ID_TAKEN,
                ((RegisterResult) registerTakenResponse.getPayload()).getRegisterStatus());
    }

    @Test
    void loginDispatchesWithStatuses() throws Exception {
        StubDataManager stub = new StubDataManager(testDataRoot().toString());
        stub.responses.put(RequestType.LOGIN,
                new Response(ResponseType.LOGIN_RESULT, new LoginResult(LoginStatus.SUCCESS, null, null)));
        server = buildServerWithStub(stub);

        Response loginResponse = server.processRequest(
                new Request(RequestType.LOGIN, new LoginCredentials("login1", "pw"), null));
        assertEquals(ResponseType.LOGIN_RESULT, loginResponse.getType());
        assertTrue(loginResponse.getPayload() instanceof LoginResult);
        assertEquals(LoginStatus.SUCCESS, ((LoginResult) loginResponse.getPayload()).getLoginStatus());
        assertEquals(RequestType.LOGIN, stub.lastCalled);

        stub.responses.put(RequestType.LOGIN,
                new Response(ResponseType.LOGIN_RESULT, new LoginResult(LoginStatus.INVALID_CREDENTIALS, null, null)));
        Response loginInvalidResponse = server.processRequest(
                new Request(RequestType.LOGIN, new LoginCredentials("login1", "pw"), null));
        assertEquals(LoginStatus.INVALID_CREDENTIALS,
                ((LoginResult) loginInvalidResponse.getPayload()).getLoginStatus());
    }

    @Test
    void updateReadDispatches() throws Exception {
        StubDataManager stub = new StubDataManager(testDataRoot().toString());
        stub.responses.put(RequestType.UPDATE_READ, new Response(ResponseType.READ_UPDATED));
        server = buildServerWithStub(stub);

        Response response = server.processRequest(
                new Request(RequestType.UPDATE_READ, new UpdateReadMessages(1L, 2L), "u1"));
        assertEquals(ResponseType.READ_UPDATED, response.getType());
        assertEquals(RequestType.UPDATE_READ, stub.lastCalled);
    }

    @Test
    void leaveConversationDispatches() throws Exception {
        StubDataManager stub = new StubDataManager(testDataRoot().toString());
        stub.responses.put(RequestType.LEAVE_CONVERSATION, new Response(ResponseType.LEAVE_RESULT));
        server = buildServerWithStub(stub);

        Response response = server.processRequest(
                new Request(RequestType.LEAVE_CONVERSATION, new LeaveConversationPayload(1L), "u1"));
        assertEquals(ResponseType.LEAVE_RESULT, response.getType());
        assertEquals(RequestType.LEAVE_CONVERSATION, stub.lastCalled);
    }

    @Test
    void adminQueryDispatches() throws Exception {
        StubDataManager stub = new StubDataManager(testDataRoot().toString());
        stub.responses.put(RequestType.ADMIN_CONVERSATION_QUERY, new Response(ResponseType.ADMIN_CONVERSATION_RESULT));
        server = buildServerWithStub(stub);

        Response response = server.processRequest(
                new Request(RequestType.ADMIN_CONVERSATION_QUERY, new AdminConversationQuery("u2"), "u1"));
        assertEquals(ResponseType.ADMIN_CONVERSATION_RESULT, response.getType());
        assertEquals(RequestType.ADMIN_CONVERSATION_QUERY, stub.lastCalled);
    }

    @Test
    void joinConversationDispatches() throws Exception {
        StubDataManager stub = new StubDataManager(testDataRoot().toString());
        stub.responses.put(RequestType.JOIN_CONVERSATION, new Response(ResponseType.CONVERSATION));
        server = buildServerWithStub(stub);

        Response response = server.processRequest(
                new Request(RequestType.JOIN_CONVERSATION, new JoinConversationPayload(1L), "u1"));
        assertEquals(ResponseType.CONVERSATION, response.getType());
        assertEquals(RequestType.JOIN_CONVERSATION, stub.lastCalled);
    }

    @Test
    void enqueueBroadcastCandidates() throws Exception {
        StubDataManager stub = new StubDataManager(testDataRoot().toString());
        Response messageResp = new Response(ResponseType.MESSAGE, new Message("hi", 7L, new java.util.Date(), "u1", 11L));
        Response createResp = new Response(ResponseType.CONVERSATION, new Conversation(22L, participants("u1", "u2", "u3")));
        Response addResp = new Response(ResponseType.CONVERSATION, new Conversation(33L, participants("u1", "u2", "u3", "u4")));
        stub.responses.put(RequestType.MESSAGE, messageResp);
        stub.responses.put(RequestType.CREATE_CONVERSATION, createResp);
        stub.responses.put(RequestType.ADD_PARTICIPANT, addResp);
        stub.participantsByConversationId.put(11L, participants("u1", "u2", "u3"));
        stub.participantsByConversationId.put(22L, participants("u1", "u2", "u3"));
        stub.participantsByConversationId.put(33L, participants("u1", "u2", "u3", "u4"));

        server = buildServerWithStub(stub);
        LinkedBlockingQueue<Map.Entry<String, Response>> queue = getResponseQueue(server);
        server.addSession("u1", noOpHandler());
        server.addSession("u2", noOpHandler());
        server.addSession("u3", noOpHandler());
        server.addSession("u4", noOpHandler());

        assertEquals(ResponseType.MESSAGE, server.processRequest(
                new Request(RequestType.MESSAGE, new RawMessage("hi", 1L), "u1")).getType());
        assertQueuedRecipients(queue, Set.of("u1", "u2", "u3"), Message.class);

        ArrayList<User.UserInfo> members = new ArrayList<>();
        members.add(User.userInfo("u1", "Alice", UserType.USER));
        assertEquals(ResponseType.CONVERSATION, server.processRequest(
                new Request(RequestType.CREATE_CONVERSATION, new CreateConversationPayload(members), "u1")).getType());
        assertQueuedRecipients(queue, Set.of("u1", "u2", "u3"), Conversation.class);

        assertEquals(ResponseType.CONVERSATION, server.processRequest(
                new Request(RequestType.ADD_PARTICIPANT,
                        new AddToConversationPayload(participants("u4"), 33L),
                        "u1")).getType());
        Map.Entry<String, Response> d1 = queue.poll();
        Map.Entry<String, Response> d2 = queue.poll();
        Map.Entry<String, Response> d3 = queue.poll();
        Map.Entry<String, Response> d4 = queue.poll();
        assertNotNull(d1);
        assertNotNull(d2);
        assertNotNull(d3);
        assertNotNull(d4);
        Map<String, Response> deliveries = Map.of(
                d1.getKey(), d1.getValue(),
                d2.getKey(), d2.getValue(),
                d3.getKey(), d3.getValue(),
                d4.getKey(), d4.getValue());
        assertEquals(Set.of("u1", "u2", "u3", "u4"), deliveries.keySet());
        assertTrue(deliveries.get("u4").getPayload() instanceof Conversation);
        assertTrue(deliveries.get("u1").getPayload() instanceof ConversationMetadata);
        assertTrue(deliveries.get("u2").getPayload() instanceof ConversationMetadata);
        assertTrue(deliveries.get("u3").getPayload() instanceof ConversationMetadata);
        assertNull(queue.poll());
    }

    @Test
    void logoutRemovesSession() throws Exception {
        server = buildServerWithStub();
        server.addSession("u1", noOpHandler());
        assertTrue(server.hasActiveSession("u1"));

        Response out = server.processRequest(new Request(RequestType.LOGOUT, (RequestPayload) null, "u1"));

        assertNull(out);
        assertFalse(server.hasActiveSession("u1"));
    }

    private ServerController buildServerWithStub() throws Exception {
        return buildServerWithStub(new StubDataManager(testDataRoot().toString()));
    }

    private ServerController buildServerWithStub(StubDataManager stub) throws Exception {
        ServerController c = new ServerController(testDataRoot().toString(), 0, false);
        setField(c, "dataManager", stub);
        return c;
    }

    /** Returns a ConnectionHandler stub that performs no I/O — safe for unit tests. */
    private static shared.networking.ConnectionHandler noOpHandler() {
        return new shared.networking.ConnectionHandler(null, null) {
            @Override public void sendResponse(shared.networking.Response r) {}
            @Override public void close() {}
        };
    }

    private Path testDataRoot() throws IOException {
        Path testData = tempRoot.resolve("test_data");
        prepareMinimalDataTree(testData);
        return testData;
    }

    private static void prepareMinimalDataTree(Path root) throws IOException {
        Path serverData = root.resolve("server_data");
        Path authorizedIds = serverData.resolve("authorized_ids");
        Files.createDirectories(authorizedIds);
        Files.writeString(authorizedIds.resolve("authorized_users.txt"), "u1,Alice\nu2,Bob\n");
        Files.writeString(authorizedIds.resolve("authorized_admins.txt"), "");
        Files.writeString(serverData.resolve("server_config.txt"), "");
        Files.createDirectories(root.resolve("conversation_data"));
        Files.createDirectories(root.resolve("user_data"));
    }

    @SuppressWarnings("unchecked")
    private static LinkedBlockingQueue<Map.Entry<String, Response>> getResponseQueue(ServerController c) throws Exception {
        Field f = ServerController.class.getDeclaredField("responseQueue");
        f.setAccessible(true);
        return (LinkedBlockingQueue<Map.Entry<String, Response>>) f.get(c);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void assertQueuedRecipients(
            LinkedBlockingQueue<Map.Entry<String, Response>> queue,
            Set<String> expectedRecipients,
            Class<?> expectedPayloadType) {
        Map.Entry<String, Response> d1 = queue.poll();
        Map.Entry<String, Response> d2 = queue.poll();
        Map.Entry<String, Response> d3 = queue.poll();
        assertNotNull(d1);
        assertNotNull(d2);
        assertNotNull(d3);
        Map<String, Response> deliveries = Map.of(
                d1.getKey(), d1.getValue(),
                d2.getKey(), d2.getValue(),
                d3.getKey(), d3.getValue());
        assertEquals(expectedRecipients, deliveries.keySet());
        for (Response response : deliveries.values()) {
            assertTrue(expectedPayloadType.isInstance(response.getPayload()));
        }
        assertNull(queue.poll());
    }

    private static ArrayList<User.UserInfo> participants(String... ids) {
        ArrayList<User.UserInfo> out = new ArrayList<>();
        for (String id : ids) {
            out.add(User.userInfo(id, id.toUpperCase(), UserType.USER));
        }
        return out;
    }

    private static class StubDataManager extends DataManager {
        final Map<RequestType, Response> responses = new EnumMap<>(RequestType.class);
        final Map<Long, ArrayList<User.UserInfo>> participantsByConversationId = new HashMap<>();
        RequestType lastCalled;

        StubDataManager(String dataRootPath) {
            super(dataRootPath);
        }

        private Response hit(RequestType type) {
            lastCalled = type;
            return responses.get(type);
        }

        @Override public Response handleRegister(Request request) { return hit(RequestType.REGISTER); }
        @Override public Response handleLogin(Request request) { return hit(RequestType.LOGIN); }
        @Override public Response handleSendMessage(Request request) { return hit(RequestType.MESSAGE); }
        @Override public Response handleUpdateReadMessages(Request request) { return hit(RequestType.UPDATE_READ); }
        @Override public Response handleCreateConversation(Request request) { return hit(RequestType.CREATE_CONVERSATION); }
        @Override public Response handleAddToConversation(Request request) { return hit(RequestType.ADD_PARTICIPANT); }
        @Override public Response handleLeaveConversation(Request request) { return hit(RequestType.LEAVE_CONVERSATION); }
        @Override public Response handleAdminConversationQuery(Request request) { return hit(RequestType.ADMIN_CONVERSATION_QUERY); }
        @Override public Response handleJoinConversation(Request request) { return hit(RequestType.JOIN_CONVERSATION); }
        @Override public ArrayList<User.UserInfo> getParticipantList(long conversationId) {
            ArrayList<User.UserInfo> participants = participantsByConversationId.get(conversationId);
            if (participants == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(participants);
        }
    }
}

