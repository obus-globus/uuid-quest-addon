package quest.uuid.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Answers requests from a queue, synchronously; an empty queue fails the request like a network error. */
final class FakeTransport implements Transport {

    final List<Request> requests = new ArrayList<>();
    private final Deque<Response> responses = new ArrayDeque<>();

    FakeTransport respond(int status, String contentType, String retryAfter, String body) {
        responses.add(new Response(status, Optional.ofNullable(contentType), Optional.ofNullable(retryAfter), body));
        return this;
    }

    FakeTransport accepted(String submitId, int queued) {
        return respond(202, "application/json", null,
            "{\"submitId\":\"" + submitId + "\",\"state\":\"queued\",\"counts\":{\"received\":" + queued
                + ",\"queued\":" + queued + "}}");
    }

    @Override
    public CompletableFuture<Response> sendAsync(Request request) {
        requests.add(request);
        Response response = responses.poll();
        return response == null
            ? CompletableFuture.failedFuture(new java.io.IOException("no response queued"))
            : CompletableFuture.completedFuture(response);
    }

    @Override
    public Response send(Request request) throws java.io.IOException {
        requests.add(request);
        Response response = responses.poll();
        if (response == null) throw new java.io.IOException("no response queued");
        return response;
    }

}
