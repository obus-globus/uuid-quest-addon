package quest.uuid.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerGuardTest {

    private static Submission finished(int checked, int fake, boolean earlyExited) {
        var submission = new Submission("id", 0, checked, null, "queued", Map.of(), List.of("a.example"));
        submission.update(JsonParser.parseString("{\"state\":\"done\",\"earlyExited\":" + earlyExited
            + ",\"counts\":{\"checked\":" + checked + ",\"fake\":" + fake + "}}").getAsJsonObject());
        return submission;
    }

    @Test
    void needsTenCheckedBeforeJudging() {
        assertFalse(ServerGuard.isSuspicious(finished(9, 9, false)));
        assertTrue(ServerGuard.isSuspicious(finished(10, 5, false)));
    }

    @Test
    void tripsAtHalfFake() {
        assertFalse(ServerGuard.isSuspicious(finished(10, 4, false)));
        assertFalse(ServerGuard.isSuspicious(finished(100, 49, false)));
        assertTrue(ServerGuard.isSuspicious(finished(100, 50, false)));
    }

    @Test
    void earlyExitIsAlwaysSuspicious() {
        assertTrue(ServerGuard.isSuspicious(finished(0, 0, true)));
    }

    @Test
    void blockReturnsOnlyNewlyBlockedServers() {
        var guard = new ServerGuard();
        assertEquals(List.of("a", "b"), guard.block(List.of("a", "b")));
        assertEquals(List.of("c"), guard.block(List.of("a", "c")));
        assertTrue(guard.isBlocked("a"));
        assertFalse(guard.isBlocked("d"));
    }

}
