package quest.uuid.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Batches UUIDs and posts them to {@code POST https://uuid.quest/v1/uuids}, then polls each submission's status.
 *
 * <p>{@link #offer} and {@link #isDue} are called from the client thread only; responses are handled on the
 * HTTP client's executor.
 */
public final class Submitter {

    static final String API = "https://uuid.quest/v1";
    static final int MAX_BATCH = 10_000;
    static final int MAX_REMEMBERED = 200_000;
    static final long POLL_INTERVAL_MS = 30_000L;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final URI SUBMIT_ENDPOINT = URI.create(API + "/uuids");

    public enum FlushResult {
        SENT("Sending the pending UUIDs"),
        NOTHING_PENDING("Nothing to send"),
        IN_FLIGHT("A submission is already running"),
        WAITING("uuid.quest asked to wait");

        private final String reason;

        FlushResult(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    private final Transport transport;
    private final SubmissionHistory history;
    private final ServerGuard guard;
    private final QuestLog log;
    private final LongSupplier clock;
    private final String userAgent;

    /** UUIDs already queued or sent this session, oldest dropped first. */
    private final Map<UUID, Boolean> remembered = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > MAX_REMEMBERED;
        }
    };
    private final Queue<UUID> pending = new ConcurrentLinkedQueue<>();

    /** Server each pending UUID was seen on, so a submission's result can be traced back to servers. */
    private final Map<UUID, String> origins = new ConcurrentHashMap<>();
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final AtomicBoolean polling = new AtomicBoolean(false);

    /** Submitting waits until then after a 429/5xx/network error; polling has its own wait. */
    private volatile long notBefore;
    private volatile long pollNotBefore;

    /** When the oldest pending UUID was queued, 0 while nothing is pending. */
    private volatile long pendingSince;

    /** Called with the penalty in seconds when uuid.quest throttles this network for submitting fake UUIDs. */
    private volatile LongConsumer onAbuseThrottled = seconds -> {
    };

    public Submitter(Transport transport, SubmissionHistory history, ServerGuard guard, QuestLog log,
                     LongSupplier clock, String userAgent) {
        this.transport = transport;
        this.history = history;
        this.guard = guard;
        this.log = log;
        this.clock = clock;
        this.userAgent = userAgent;
    }

    public void setOnAbuseThrottled(LongConsumer onAbuseThrottled) {
        this.onAbuseThrottled = onAbuseThrottled;
    }

    public int rememberedCount() {
        return remembered.size();
    }

    public int pendingCount() {
        return pending.size();
    }

    public void offer(UUID uuid, String server) {
        if (remembered.put(uuid, Boolean.TRUE) == null) {
            origins.put(uuid, server);
            pending.add(uuid);
            if (pendingSince == 0L) pendingSince = clock.getAsLong();
        }
    }

    /** True once the oldest pending UUID has waited {@code intervalSeconds}, so quiet periods still fill a batch. */
    public boolean isDue(int intervalSeconds) {
        long since = pendingSince;
        return since != 0L && clock.getAsLong() - since >= intervalSeconds * 1000L;
    }

    /** Seconds until submitting is allowed again, 0 if it is. */
    public long waitSeconds() {
        return Math.max(0L, (notBefore - clock.getAsLong()) / 1000L);
    }

    /**
     * Sends up to {@link #MAX_BATCH} pending UUIDs unless a request is running or the server asked to wait.
     * {@code report} receives a one-line summary of the outcome, on the HTTP thread.
     */
    public FlushResult flush(String note, Consumer<QuestText> report) {
        if (pending.isEmpty()) return FlushResult.NOTHING_PENDING;
        if (!inFlight.compareAndSet(false, true)) return FlushResult.IN_FLIGHT;
        if (clock.getAsLong() < notBefore) {
            inFlight.set(false);
            return FlushResult.WAITING;
        }

        List<UUID> batch = drainBatch();
        if (batch.isEmpty()) {
            inFlight.set(false);
            return FlushResult.NOTHING_PENDING;
        }
        log.info("Sending " + batch.size() + " UUIDs to uuid.quest (" + pending.size() + " still pending)");
        try {
            transport.sendAsync(submitRequest(batch, note, REQUEST_TIMEOUT)).whenComplete((response, error) -> {
                try {
                    handle(batch, note, response, error, report);
                } catch (Exception e) {
                    log.error("Handling the uuid.quest response failed", e);
                } finally {
                    inFlight.set(false);
                }
            });
        } catch (RuntimeException e) {
            inFlight.set(false);
            retryLater(batch, RetryPolicy.DEFAULT_RETRY_SECONDS);
            log.warn("uuid.quest submission failed, retrying " + batch.size() + " UUIDs later", e);
        }
        return FlushResult.SENT;
    }

    /**
     * Sends everything pending before the client exits, blocking for at most {@code timeout}. Waits for a
     * running request first, and sends nothing while uuid.quest has asked to wait.
     */
    public void flushBlocking(String note, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (inFlight.get() && deadline - System.nanoTime() > 0) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        while (!pending.isEmpty() && deadline - System.nanoTime() > 0 && clock.getAsLong() >= notBefore) {
            if (!inFlight.compareAndSet(false, true)) return;
            List<UUID> batch = drainBatch();
            if (batch.isEmpty()) {
                inFlight.set(false);
                return;
            }
            log.info("Sending " + batch.size() + " UUIDs to uuid.quest before exit");
            try {
                Duration remaining = Duration.ofNanos(deadline - System.nanoTime());
                handle(batch, note, transport.send(submitRequest(batch, note, remaining)), null, text -> {
                });
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                pending.addAll(batch);
                log.warn("uuid.quest submission on shutdown failed, " + pending.size() + " UUIDs not sent", e);
                return;
            } finally {
                inFlight.set(false);
            }
        }
        if (!pending.isEmpty()) log.info(pending.size() + " UUIDs were not sent before exit");
    }

    /** Takes up to {@link #MAX_BATCH} pending UUIDs, dropping those seen on a server blocked since. */
    private List<UUID> drainBatch() {
        var batch = new ArrayList<UUID>(Math.min(pending.size(), MAX_BATCH));
        while (batch.size() < MAX_BATCH) {
            UUID uuid = pending.poll();
            if (uuid == null) break;
            String server = origins.get(uuid);
            if (server != null && guard.isBlocked(server)) {
                origins.remove(uuid);
            } else {
                batch.add(uuid);
            }
        }
        if (pending.isEmpty()) pendingSince = 0L;
        return batch;
    }

    /** Where the API key for submissions comes from; only submissions carry it, never status polls. */
    private volatile java.util.function.Supplier<String> apiKey = () -> "";

    public void setApiKey(java.util.function.Supplier<String> apiKey) {
        this.apiKey = apiKey;
    }

    private Transport.Request submitRequest(List<UUID> batch, String note, Duration timeout) {
        var uuids = new JsonArray();
        batch.forEach(uuid -> uuids.add(uuid.toString()));
        var body = new JsonObject();
        body.add("uuids", uuids);
        String cleaned = cleanNote(note);
        if (cleaned != null) body.addProperty("note", cleaned);
        String key = apiKey.get();
        String bearer = key == null || key.isBlank() ? null : key.trim();
        return new Transport.Request(SUBMIT_ENDPOINT, body.toString(), timeout, userAgent, bearer);
    }

    static String cleanNote(String note) {
        if (note == null) return null;
        String cleaned = StoredSettings.cleanNote(note);
        return cleaned.isEmpty() ? null : cleaned;
    }

    private void handle(List<UUID> batch, String note, Transport.Response response, Throwable error,
                        Consumer<QuestText> report) {
        if (response == null) {
            retryLater(batch, RetryPolicy.DEFAULT_RETRY_SECONDS);
            log.warn("uuid.quest submission failed, retrying " + batch.size() + " UUIDs later", error);
            return;
        }

        int status = response.status();
        switch (RetryPolicy.decide(response)) {
            case ACCEPTED -> {
                var servers = new LinkedHashSet<String>();
                batch.stream().map(origins::remove).filter(Objects::nonNull).forEach(servers::add);
                Submission submission;
                try {
                    submission = parseAccepted(response.body(), batch.size(), note, List.copyOf(servers));
                } catch (RuntimeException e) {
                    log.warn("uuid.quest accepted " + batch.size() + " UUIDs but sent an unreadable body: "
                        + response.body(), e);
                    return;
                }
                history.add(submission);
                log.info("uuid.quest accepted submission " + submission.submitId() + ": "
                    + submission.count("queued") + " queued, " + submission.count("cracked") + " cracked, "
                    + submission.count("duplicate") + " duplicate");
                report.accept(QuestText.builder().text("uuid.quest ").link(submission.submitId())
                    .text(": queued " + submission.count("queued") + " of " + batch.size() + " UUIDs").build());
            }
            case RETRY -> {
                long seconds = RetryPolicy.retryAfterSeconds(response);
                retryLater(batch, seconds);
                log.info("uuid.quest answered " + status + ", retrying " + batch.size() + " UUIDs in " + seconds + "s");
                if (RetryPolicy.isAbuseThrottled(response)) {
                    onAbuseThrottled.accept(seconds);
                } else if (status == 429) {
                    report.accept(QuestText.of("uuid.quest is rate limiting, retrying in " + seconds + "s"));
                }
            }
            case REJECTED -> {
                batch.forEach(origins::remove);
                log.warn("uuid.quest rejected " + batch.size() + " UUIDs (" + status + "): " + response.body());
                report.accept(QuestText.of("uuid.quest rejected the submission (" + status + ")"));
            }
        }
    }

    private Submission parseAccepted(String body, int sent, String note, List<String> servers) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        return new Submission(
            json.get("submitId").getAsString(),
            clock.getAsLong(),
            sent,
            cleanNote(note),
            json.get("state").getAsString(),
            Submission.parseCounts(json.getAsJsonObject("counts")),
            servers
        );
    }

    private void retryLater(List<UUID> batch, long seconds) {
        pending.addAll(batch);
        if (pendingSince == 0L) pendingSince = clock.getAsLong();
        notBefore = clock.getAsLong() + seconds * 1000L;
    }

    /**
     * Refreshes the oldest unfinished submission whose status is older than {@link #POLL_INTERVAL_MS}, one
     * request at a time. {@code onFinished} is called, on the HTTP thread, once uuid.quest reports it done.
     */
    public void pollUnfinished(Consumer<Submission> onFinished) {
        long now = clock.getAsLong();
        if (now < pollNotBefore) return;
        Submission submission = history.unfinished().stream()
            .filter(candidate -> now - candidate.lastPolled() >= POLL_INTERVAL_MS)
            .findFirst().orElse(null);
        if (submission == null || !polling.compareAndSet(false, true)) return;
        submission.setLastPolled(now);

        var request = new Transport.Request(URI.create(API + "/submissions/" + submission.submitId()), null,
            REQUEST_TIMEOUT, userAgent);
        try {
            transport.sendAsync(request).whenComplete((response, error) -> {
                try {
                    handlePoll(submission, response, error, onFinished);
                } catch (Exception e) {
                    log.error("Handling the uuid.quest status of " + submission.submitId() + " failed", e);
                } finally {
                    polling.set(false);
                }
            });
        } catch (RuntimeException e) {
            polling.set(false);
            log.warn("uuid.quest status poll of " + submission.submitId() + " failed", e);
        }
    }

    private void handlePoll(Submission submission, Transport.Response response, Throwable error,
                            Consumer<Submission> onFinished) {
        int status = response == null ? -1 : response.status();
        if (status == 200) {
            boolean wasFinished = submission.finished();
            submission.update(JsonParser.parseString(response.body()).getAsJsonObject());
            if (submission.finished() && !wasFinished) {
                log.info(submission.summaryLine());
                onFinished.accept(submission);
            }
        } else if (status == 404) {
            submission.setState(Submission.STATE_EXPIRED);
        } else {
            if (status == 429 || status >= 500) {
                pollNotBefore = clock.getAsLong() + RetryPolicy.retryAfterSeconds(response) * 1000L;
            }
            log.warn("uuid.quest status poll of " + submission.submitId() + " failed ("
                + (response == null ? "null" : status) + ")", error);
        }
        history.save();
    }

}
