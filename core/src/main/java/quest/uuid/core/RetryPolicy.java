package quest.uuid.core;

import com.google.gson.JsonParser;

import java.util.Optional;
import java.util.Set;

/** How a response to {@code POST /v1/uuids} is treated. */
public final class RetryPolicy {

    public enum Decision {
        ACCEPTED,
        /** Queue the batch again and wait (Retry-After, or {@link #DEFAULT_RETRY_SECONDS}). */
        RETRY,
        /** The batch was too large (413); send it again in smaller batches. */
        SPLIT,
        /** The request itself is bad; drop the batch. */
        REJECTED,
    }

    public static final long DEFAULT_RETRY_SECONDS = 60;

    /** 4xx statuses worth another try; any other 4xx with a problem+json body means the request itself is bad. */
    private static final Set<Integer> RETRYABLE_CLIENT_ERRORS = Set.of(408, 425, 429);

    private RetryPolicy() {
    }

    public static Decision decide(Transport.Response response) {
        int status = response.status();
        if (status == 202) return Decision.ACCEPTED;
        if (status == 413) return Decision.SPLIT;
        if (status >= 500 || RETRYABLE_CLIENT_ERRORS.contains(status)
            || (status >= 400 && status <= 499 && !isProblemJson(response))) {
            return Decision.RETRY;
        }
        return Decision.REJECTED;
    }

    /** Retry-After in seconds, or the default when it is missing or not a number. */
    public static long retryAfterSeconds(Transport.Response response) {
        return parseSeconds(response.retryAfter()).orElse(DEFAULT_RETRY_SECONDS);
    }

    static Optional<Long> parseSeconds(Optional<String> retryAfter) {
        try {
            return retryAfter.map(String::trim).map(Long::parseLong);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static boolean isProblemJson(Transport.Response response) {
        return response.contentType().map(type -> type.contains("json")).orElse(false);
    }

    /** uuid.quest penalises this network for submitting mostly fake UUIDs. */
    public static boolean isAbuseThrottled(Transport.Response response) {
        if (response.status() != 429 || !isProblemJson(response)) return false;
        try {
            return JsonParser.parseString(response.body()).getAsJsonObject().get("type").getAsString()
                .endsWith("/abuse-throttled");
        } catch (RuntimeException e) {
            return false;
        }
    }

}
