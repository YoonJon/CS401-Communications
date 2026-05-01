package client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import shared.enums.LoginStatus;
import shared.enums.ResponseType;
import shared.networking.Response;
import shared.networking.User.UserInfo;
import shared.networking.fixtures.NetworkingSeedData;
import shared.payload.LoginResult;
import shared.payload.ReadMessagesUpdated;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression tests for issue #209 — verifies {@code handleReadMessagesUpdatedResponse}
 * merges per-conversation lastRead pointers instead of replacing the {@link UserInfo}
 * wholesale. Pre-fix, an ack for one conversation wiped optimistic advances for
 * other conversations whose acks had not yet returned.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class ClientControllerLastReadMergeTest {

    private static ClientController headless() {
        return new ClientController("localhost", 0, null) {
            @Override
            void processResponse(Response response) {
                super.processResponse(response);
                flushEdt();
            }
        };
    }

    private static void flushEdt() {
        try {
            SwingUtilities.invokeAndWait(() -> {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush EDT", e);
        }
    }

    private static Response loginAlice() {
        return new Response(ResponseType.LOGIN_RESULT,
                new LoginResult(LoginStatus.SUCCESS,
                        NetworkingSeedData.aliceInfo(),
                        new ArrayList<>(),
                        new ArrayList<>()));
    }

    /** Builds a fresh server-snapshot UserInfo for Alice with the given (convId,seq) entry. */
    private static UserInfo aliceServerSnapshotWithRead(long convId, long seq) {
        UserInfo u = NetworkingSeedData.aliceInfo();
        u.setLastRead(convId, seq);
        return u;
    }

    @Test
    void merge_preservesLocalAdvanceForOtherConversation() {
        ClientController c = headless();
        c.processResponse(loginAlice());

        // Optimistic local advance for two conversations.
        c.getCurrentUserInfo().setLastRead(100L, 5L);
        c.getCurrentUserInfo().setLastRead(200L, 7L);

        // Server ack arrives for conv 100 only — server snapshot has no entry for 200.
        Response ack = new Response(ResponseType.READ_MESSAGES_UPDATED,
                new ReadMessagesUpdated(aliceServerSnapshotWithRead(100L, 5L)));
        c.processResponse(ack);

        UserInfo merged = c.getCurrentUserInfo();
        assertEquals(5L, merged.getLastRead(100L), "ack value retained for the acked conversation");
        assertEquals(7L, merged.getLastRead(200L), "local advance for unrelated conversation preserved");
    }

    @Test
    void merge_serverAheadWins() {
        ClientController c = headless();
        c.processResponse(loginAlice());
        c.getCurrentUserInfo().setLastRead(100L, 3L);

        Response ack = new Response(ResponseType.READ_MESSAGES_UPDATED,
                new ReadMessagesUpdated(aliceServerSnapshotWithRead(100L, 9L)));
        c.processResponse(ack);

        assertEquals(9L, c.getCurrentUserInfo().getLastRead(100L),
                "server snapshot is ahead — local pointer must catch up");
    }

    @Test
    void merge_localAheadWins() {
        ClientController c = headless();
        c.processResponse(loginAlice());
        c.getCurrentUserInfo().setLastRead(100L, 12L);

        Response ack = new Response(ResponseType.READ_MESSAGES_UPDATED,
                new ReadMessagesUpdated(aliceServerSnapshotWithRead(100L, 10L)));
        c.processResponse(ack);

        assertEquals(12L, c.getCurrentUserInfo().getLastRead(100L),
                "local advance must not regress when server snapshot is behind");
    }

    @Test
    void merge_nullPayloadIsNoOp() {
        ClientController c = headless();
        c.processResponse(loginAlice());
        UserInfo before = c.getCurrentUserInfo();

        Response ack = new Response(ResponseType.READ_MESSAGES_UPDATED,
                new ReadMessagesUpdated(null));
        c.processResponse(ack);

        assertSame(before, c.getCurrentUserInfo(),
                "null updated UserInfo must leave currentUser untouched");
    }

    @Test
    void merge_loggedOutDoesNotNpe() {
        ClientController c = headless();
        // No prior login — currentUser is null.
        Response ack = new Response(ResponseType.READ_MESSAGES_UPDATED,
                new ReadMessagesUpdated(aliceServerSnapshotWithRead(100L, 5L)));

        assertDoesNotThrow(() -> c.processResponse(ack));
    }
}
