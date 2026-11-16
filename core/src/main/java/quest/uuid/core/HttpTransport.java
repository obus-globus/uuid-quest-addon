package quest.uuid.core;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** {@link Transport} on {@code java.net.http}. */
public final class HttpTransport implements Transport {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public CompletableFuture<Response> sendAsync(Request request) {
        return http.sendAsync(build(request), HttpResponse.BodyHandlers.ofString()).thenApply(HttpTransport::convert);
    }

    @Override
    public Response send(Request request) throws IOException, InterruptedException {
        return convert(http.send(build(request), HttpResponse.BodyHandlers.ofString()));
    }

    private static HttpRequest build(Request request) {
        var builder = HttpRequest.newBuilder(request.uri())
            .timeout(request.timeout())
            .header("User-Agent", request.userAgent());
        if (request.body() == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(request.body()));
        }
        return builder.build();
    }

    private static Response convert(HttpResponse<String> response) {
        return new Response(
            response.statusCode(),
            response.headers().firstValue("Content-Type"),
            response.headers().firstValue("Retry-After"),
            response.body()
        );
    }

}
