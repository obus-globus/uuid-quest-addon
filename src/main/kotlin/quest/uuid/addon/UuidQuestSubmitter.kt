package quest.uuid.addon

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Batches UUIDs and posts them to `POST https://uuid.quest/v1/uuids`.
 *
 * [offer] is called from the render thread only; the HTTP response is handled on the client's executor.
 */
object UuidQuestSubmitter {

    private const val API = "https://uuid.quest/v1"
    private val submitEndpoint = URI("$API/uuids")
    private const val MAX_BATCH = 10_000
    private const val MAX_REMEMBERED = 200_000
    private const val ERROR_BACKOFF_MS = 60_000L
    private const val POLL_INTERVAL_MS = 30_000L
    private const val MAX_NOTE_LENGTH = 200
    private val REQUEST_TIMEOUT = Duration.ofSeconds(30)

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    /** UUIDs already queued or sent this session, oldest dropped first. */
    private val remembered = object : LinkedHashMap<UUID, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, Unit>) = size > MAX_REMEMBERED
    }
    private val pending = ConcurrentLinkedQueue<UUID>()
    private val inFlight = AtomicBoolean(false)

    @Volatile
    private var notBefore = 0L
    private var lastFlush = 0L

    val rememberedCount get() = remembered.size
    val pendingCount get() = pending.size
    private val polling = AtomicBoolean(false)

    /** `<add-on id>/<version>`, set from the add-on's metadata on initialization. */
    @Volatile
    var userAgent = "uuid-quest"

    fun offer(uuid: UUID) {
        if (remembered.put(uuid, Unit) == null) {
            pending.add(uuid)
        }
    }

    fun isDue(intervalSeconds: Int) = System.currentTimeMillis() - lastFlush >= intervalSeconds * 1000L

    /**
     * Sends up to [MAX_BATCH] pending UUIDs unless a request is running or the server asked to wait.
     * [report] receives a one-line summary of the outcome.
     */
    fun flush(note: String, report: (String) -> Unit = {}) {
        val now = System.currentTimeMillis()
        if (pending.isEmpty() || now < notBefore || !inFlight.compareAndSet(false, true)) return
        lastFlush = now

        val batch = drainBatch()
        log.info("Sending ${batch.size} UUIDs to uuid.quest (${pending.size} still pending)")
        http.sendAsync(request(batch, note, REQUEST_TIMEOUT), HttpResponse.BodyHandlers.ofString())
            .whenComplete { response, error ->
                try {
                    handle(batch, note, response, error, report)
                } finally {
                    inFlight.set(false)
                }
            }
    }

    /**
     * Sends everything pending before the client exits, blocking for at most [timeout]. Waits for a
     * running request first and ignores any rate-limit wait, as there is no later chance.
     */
    fun flushBlocking(note: String, timeout: Duration) {
        val deadline = System.nanoTime() + timeout.toNanos()
        fun remaining() = Duration.ofNanos(deadline - System.nanoTime())

        while (inFlight.get() && !remaining().isNegative) Thread.sleep(50)
        notBefore = 0L

        while (pending.isNotEmpty() && remaining() > Duration.ZERO && notBefore == 0L) {
            if (!inFlight.compareAndSet(false, true)) return
            val batch = drainBatch()
            log.info("Sending ${batch.size} UUIDs to uuid.quest before exit")
            try {
                val response = http.send(request(batch, note, remaining()), HttpResponse.BodyHandlers.ofString())
                handle(batch, note, response, null) {}
            } catch (e: Exception) {
                pending.addAll(batch)
                log.warn("uuid.quest submission on shutdown failed, ${pending.size} UUIDs not sent", e)
                return
            } finally {
                inFlight.set(false)
            }
        }
    }

    private fun drainBatch(): List<UUID> {
        val batch = ArrayList<UUID>(minOf(pending.size, MAX_BATCH))
        while (batch.size < MAX_BATCH) batch.add(pending.poll() ?: break)
        return batch
    }

    private fun request(batch: List<UUID>, note: String, timeout: Duration): HttpRequest {
        val body = JsonObject().apply {
            add("uuids", JsonArray().apply { batch.forEach { add(it.toString()) } })
            note.trim().take(MAX_NOTE_LENGTH).ifEmpty { null }?.let { addProperty("note", it) }
        }
        return HttpRequest.newBuilder(submitEndpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
    }

    private fun handle(
        batch: List<UUID>,
        note: String,
        response: HttpResponse<String>?,
        error: Throwable?,
        report: (String) -> Unit,
    ) {
        val status = response?.statusCode()
        when {
            response == null || status == null -> {
                retryLater(batch, ERROR_BACKOFF_MS)
                log.warn("uuid.quest submission failed, retrying later", error)
            }

            status == 202 -> {
                val json = JsonParser.parseString(response.body()).asJsonObject
                val submission = Submission(
                    submitId = json.get("submitId").asString,
                    createdAt = System.currentTimeMillis(),
                    sent = batch.size,
                    note = note.trim().take(MAX_NOTE_LENGTH).ifEmpty { null },
                    state = json.get("state").asString,
                    counts = json.getAsJsonObject("counts").entrySet()
                        .associate { (key, value) -> key to value.asInt },
                )
                SubmissionHistory.add(submission)
                log.info(
                    "uuid.quest accepted submission ${submission.submitId}: ${submission.count("queued")} queued, " +
                        "${submission.count("cracked")} cracked, ${submission.count("duplicate")} duplicate"
                )
                report("uuid.quest queued ${submission.count("queued")} of ${batch.size} UUIDs")
            }

            status == 429 -> {
                val retryAfter = response.headers().firstValue("Retry-After").map(String::toLongOrNull).orElse(null)
                retryLater(batch, (retryAfter ?: 60L) * 1000L)
                log.info("uuid.quest is rate limiting, retrying ${batch.size} UUIDs in ${retryAfter ?: 60}s")
                report("uuid.quest is rate limiting, retrying in ${retryAfter ?: 60}s")
            }

            status >= 500 -> {
                retryLater(batch, ERROR_BACKOFF_MS)
                log.warn("uuid.quest answered $status, retrying later")
            }

            else -> {
                log.warn("uuid.quest rejected ${batch.size} UUIDs ($status): ${response.body()}")
                report("uuid.quest rejected the submission ($status)")
            }
        }
    }

    /**
     * Refreshes the oldest unfinished submission whose status is older than [POLL_INTERVAL_MS], one
     * request at a time. [onFinished] is called once uuid.quest reports the submission done.
     */
    fun pollUnfinished(onFinished: (Submission) -> Unit = {}) {
        val now = System.currentTimeMillis()
        val submission = SubmissionHistory.unfinished().firstOrNull { now - it.lastPolled >= POLL_INTERVAL_MS }
            ?: return
        if (now < notBefore || !polling.compareAndSet(false, true)) return
        submission.lastPolled = now

        val request = HttpRequest.newBuilder(URI("$API/submissions/${submission.submitId}"))
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", userAgent)
            .GET()
            .build()
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, error ->
            try {
                when (response?.statusCode()) {
                    200 -> {
                        submission.update(JsonParser.parseString(response.body()).asJsonObject)
                        if (submission.finished) {
                            log.info(summary(submission))
                            onFinished(submission)
                        }
                    }

                    404 -> submission.state = Submission.STATE_EXPIRED
                    429 -> notBefore = System.currentTimeMillis() +
                        (response.headers().firstValue("Retry-After").map(String::toLongOrNull).orElse(null) ?: 60L) *
                        1000L

                    else -> log.warn("uuid.quest status poll failed (${response?.statusCode()})", error)
                }
                SubmissionHistory.save()
            } finally {
                polling.set(false)
            }
        }
    }

    fun summary(submission: Submission) = buildString {
        append("uuid.quest ${submission.submitId.take(8)}: ")
        append("${submission.count("forwarded")} new, ${submission.count("alreadyKnown")} known, ")
        append("${submission.count("fake")} fake")
        if (submission.earlyExited) append(", stopped early")
    }

    private fun retryLater(batch: List<UUID>, delayMs: Long) {
        pending.addAll(batch)
        notBefore = System.currentTimeMillis() + delayMs
    }

}
