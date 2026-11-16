package quest.uuid.core;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** HTTP as the submitter needs it; {@link HttpTransport} in the game, a fake in tests. */
public interface Transport {

    /** {@code body} is null for a GET. */
    record Request(URI uri, String body, Duration timeout, String userAgent) {
    }

    record Response(int status, Optional<String> contentType, Optional<String> retryAfter, String body) {
    }

    CompletableFuture<Response> sendAsync(Request request);

    Response send(Request request) throws IOException, InterruptedException;

}
