package quest.uuid.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static quest.uuid.core.RetryPolicy.Decision.ACCEPTED;
import static quest.uuid.core.RetryPolicy.Decision.REJECTED;
import static quest.uuid.core.RetryPolicy.Decision.RETRY;

class RetryPolicyTest {

    private static final String PROBLEM = "application/problem+json";

    private static Transport.Response response(int status, String contentType, String retryAfter, String body) {
        return new Transport.Response(status, Optional.ofNullable(contentType), Optional.ofNullable(retryAfter), body);
    }

    @Test
    void acceptsOnly202() {
        assertEquals(ACCEPTED, RetryPolicy.decide(response(202, "application/json", null, "{}")));
        assertEquals(REJECTED, RetryPolicy.decide(response(200, "application/json", null, "{}")));
    }

    @Test
    void retriesTransientStatuses() {
        for (int status : new int[]{408, 425, 429, 500, 502, 503}) {
            assertEquals(RETRY, RetryPolicy.decide(response(status, PROBLEM, null, "{}")), "status " + status);
        }
    }

    @Test
    void dropsBadRequestsWithProblemJson() {
        assertEquals(REJECTED, RetryPolicy.decide(response(400, PROBLEM, null, "{}")));
        assertEquals(RetryPolicy.Decision.SPLIT, RetryPolicy.decide(response(413, "application/json", null, "{}")));
    }

    @Test
    void retriesClientErrorsWithoutJson() {
        // A proxy or captive portal, not uuid.quest itself.
        assertEquals(RETRY, RetryPolicy.decide(response(403, "text/html", null, "<html>")));
        assertEquals(RETRY, RetryPolicy.decide(response(404, null, null, "")));
    }

    @Test
    void honoursRetryAfter() {
        assertEquals(120, RetryPolicy.retryAfterSeconds(response(429, PROBLEM, "120", "{}")));
        assertEquals(RetryPolicy.DEFAULT_RETRY_SECONDS, RetryPolicy.retryAfterSeconds(response(503, null, null, "")));
        assertEquals(RetryPolicy.DEFAULT_RETRY_SECONDS,
            RetryPolicy.retryAfterSeconds(response(503, null, "Wed, 21 Oct 2015 07:28:00 GMT", "")));
    }

    @Test
    void recognisesAbuseThrottling() {
        assertTrue(RetryPolicy.isAbuseThrottled(response(429, PROBLEM, "3600",
            "{\"type\":\"https://uuid.quest/problems/abuse-throttled\"}")));
        assertFalse(RetryPolicy.isAbuseThrottled(response(429, PROBLEM, "60",
            "{\"type\":\"https://uuid.quest/problems/rate-limited\"}")));
        assertFalse(RetryPolicy.isAbuseThrottled(response(429, "text/plain", "60", "abuse-throttled")));
        assertFalse(RetryPolicy.isAbuseThrottled(response(429, PROBLEM, "60", "not json")));
    }

}
