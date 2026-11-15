package quest.uuid.addon

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Batches UUIDs and posts them to `POST https://uuid.quest/v1/uuids`, then polls each submission's status.
 *
 * [offer] and [isDue] are called from the render thread only; HTTP responses are handled on the client's executor.
 */
object UuidQuestSubmitter {

    private const val API = "https://uuid.quest/v1"
    private val submitEndpoint = URI("$API/uuids")
    private const val MAX_BATCH = 10_000
    private const val MAX_REMEMBERED = 200_000
    private const val DEFAULT_RETRY_SECONDS = 60L
    private const val POLL_INTERVAL_MS = 30_000L
    private const val MAX_NOTE_LENGTH = 200
    private val REQUEST_TIMEOUT = Duration.ofSeconds(30)

    /** 4xx statuses worth another try; any other 4xx with a problem+json body means the request itself is bad. */
    private val RETRYABLE_CLIENT_ERRORS = setOf(408, 425, 429)

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    /** UUIDs already queued or sent this session, oldest dropped first. */
    private val remembered = object : LinkedHashMap<UUID, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, Unit>) = size > MAX_REMEMBERED
    }
    private val pending = ConcurrentLinkedQueue<UUID>()
    private val inFlight = AtomicBoolean(false)
    private val polling = AtomicBoolean(false)

    /** Submitting waits until then after a 429/5xx/network error; polling has its own wait. */
    @Volatile
    private var notBefore = 0L

    @Volatile
    private var pollNotBefore = 0L

    /** When the oldest pending UUID was queued, 0 while nothing is pending. */
    @Volatile
    private var pendingSince = 0L

    /** `<add-on id>/<version>`, set from the add-on's metadata on initialization. */
    @Volatile
    var userAgent = "uuid-quest"

    val rememberedCount get() = remembered.size
    val pendingCount get() = pending.size

    enum class FlushResult(val reason: String) {
        SENT("Sending the pending UUIDs"),
        NOTHING_PENDING("Nothing to send"),
        IN_FLIGHT("A submission is already running"),
        WAITING("uuid.quest asked to wait"),
    }

    fun offer(uuid: UUID) {
        if (remembered.put(uuid, Unit) == null) {
            pending.add(uuid)
            if (pendingSince == 0L) pendingSince = System.currentTimeMillis()
        }
    }

    /** True once the oldest pending UUID has waited [intervalSeconds], so quiet periods still fill a batch. */
    fun isDue(intervalSeconds: Int): Boolean {
        val since = pendingSince
        return since != 0L && System.currentTimeMillis() - since >= intervalSeconds * 1000L
    }

    /** Seconds until submitting is allowed again, 0 if it is. */
    fun waitSeconds() = ((notBefore - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)

    /**
     * Sends up to [MAX_BATCH] pending UUIDs unless a request is running or the server asked to wait.
     * [report] receives a one-line summary of the outcome.
     */
    fun flush(note: String, report: (Component) -> Unit = {}): FlushResult {
        if (pending.isEmpty()) return FlushResult.NOTHING_PENDING
        if (!inFlight.compareAndSet(false, true)) return FlushResult.IN_FLIGHT
        if (System.currentTimeMillis() < notBefore) {
            inFlight.set(false)
            return FlushResult.WAITING
        }

        val batch = drainBatch()
        log.info("Sending ${batch.size} UUIDs to uuid.quest (${pending.size} still pending)")
        http.sendAsync(request(batch, note, REQUEST_TIMEOUT), HttpResponse.BodyHandlers.ofString())
            .whenComplete { response, error ->
                try {
                    handle(batch, note, response, error, report)
                } catch (e: Exception) {
                    log.error("Handling the uuid.quest response failed", e)
                } finally {
                    inFlight.set(false)
                }
            }
        return FlushResult.SENT
    }

    /**
     * Sends everything pending before the client exits, blocking for at most [timeout]. Waits for a
     * running request first, and sends nothing while uuid.quest has asked to wait.
     */
    fun flushBlocking(note: String, timeout: Duration) {
        val deadline = System.nanoTime() + timeout.toNanos()
        fun remaining() = Duration.ofNanos(deadline - System.nanoTime())

        while (inFlight.get() && !remaining().isNegative) Thread.sleep(50)

        while (pending.isNotEmpty() && remaining() > Duration.ZERO && System.currentTimeMillis() >= notBefore) {
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
        if (pending.isNotEmpty()) log.info("${pending.size} UUIDs were not sent before exit")
    }

    private fun drainBatch(): List<UUID> {
        val batch = ArrayList<UUID>(minOf(pending.size, MAX_BATCH))
        while (batch.size < MAX_BATCH) batch.add(pending.poll() ?: break)
        if (pending.isEmpty()) pendingSince = 0L
        return batch
    }

    private fun request(batch: List<UUID>, note: String, timeout: Duration): HttpRequest {
        val body = JsonObject().apply {
            add("uuids", JsonArray().apply { batch.forEach { add(it.toString()) } })
            cleanNote(note)?.let { addProperty("note", it) }
        }
        return HttpRequest.newBuilder(submitEndpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
    }

    private fun cleanNote(note: String) = note.trim().take(MAX_NOTE_LENGTH).ifEmpty { null }

    @Suppress("CyclomaticComplexMethod")
    private fun handle(
        batch: List<UUID>,
        note: String,
        response: HttpResponse<String>?,
        error: Throwable?,
        report: (Component) -> Unit,
    ) {
        if (response == null) {
            retryLater(batch, DEFAULT_RETRY_SECONDS)
            log.warn("uuid.quest submission failed, retrying ${batch.size} UUIDs later", error)
            return
        }

        val status = response.statusCode()
        when {
            status == 202 -> {
                val submission = runCatching { parseAccepted(response.body(), batch.size, note) }.getOrElse {
                    log.warn("uuid.quest accepted ${batch.size} UUIDs but sent an unreadable body: ${response.body()}", it)
                    return
                }
                SubmissionHistory.add(submission)
                log.info(
                    "uuid.quest accepted submission ${submission.submitId}: ${submission.count("queued")} queued, " +
                        "${submission.count("cracked")} cracked, ${submission.count("duplicate")} duplicate"
                )
                report(
                    gray("uuid.quest ").append(submissionLink(submission.submitId))
                        .append(gray(": queued ${submission.count("queued")} of ${batch.size} UUIDs"))
                )
            }

            status >= 500 || status in RETRYABLE_CLIENT_ERRORS || (status in 400..499 && !isProblemJson(response)) -> {
                val seconds = retryAfterSeconds(response) ?: DEFAULT_RETRY_SECONDS
                retryLater(batch, seconds)
                log.info("uuid.quest answered $status, retrying ${batch.size} UUIDs in ${seconds}s")
                if (status == 429) report(gray("uuid.quest is rate limiting, retrying in ${seconds}s"))
            }

            else -> {
                log.warn("uuid.quest rejected ${batch.size} UUIDs ($status): ${response.body()}")
                report(gray("uuid.quest rejected the submission ($status)"))
            }
        }
    }

    private fun parseAccepted(body: String, sent: Int, note: String): Submission {
        val json = JsonParser.parseString(body).asJsonObject
        return Submission(
            submitId = json.get("submitId").asString,
            createdAt = System.currentTimeMillis(),
            sent = sent,
            note = cleanNote(note),
            state = json.get("state").asString,
            counts = parseCounts(json.getAsJsonObject("counts")),
        )
    }

    private fun isProblemJson(response: HttpResponse<String>) =
        response.headers().firstValue("Content-Type").map { "json" in it }.orElse(false)

    private fun retryAfterSeconds(response: HttpResponse<String>) =
        response.headers().firstValue("Retry-After").map(String::toLongOrNull).orElse(null)

    private fun retryLater(batch: List<UUID>, seconds: Long) {
        pending.addAll(batch)
        if (pendingSince == 0L) pendingSince = System.currentTimeMillis()
        notBefore = System.currentTimeMillis() + seconds * 1000L
    }

    /**
     * Refreshes the oldest unfinished submission whose status is older than [POLL_INTERVAL_MS], one
     * request at a time. [onFinished] is called once uuid.quest reports the submission done.
     */
    fun pollUnfinished(onFinished: (Submission) -> Unit = {}) {
        val now = System.currentTimeMillis()
        if (now < pollNotBefore) return
        val submission = SubmissionHistory.unfinished().firstOrNull { now - it.lastPolled >= POLL_INTERVAL_MS }
            ?: return
        if (!polling.compareAndSet(false, true)) return
        submission.lastPolled = now

        val request = HttpRequest.newBuilder(URI("$API/submissions/${submission.submitId}"))
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", userAgent)
            .GET()
            .build()
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, error ->
            try {
                handlePoll(submission, response, error, onFinished)
            } catch (e: Exception) {
                log.error("Handling the uuid.quest status of ${submission.submitId} failed", e)
            } finally {
                polling.set(false)
            }
        }
    }

    private fun handlePoll(
        submission: Submission,
        response: HttpResponse<String>?,
        error: Throwable?,
        onFinished: (Submission) -> Unit,
    ) {
        when (val status = response?.statusCode()) {
            200 -> {
                val wasFinished = submission.finished
                submission.update(JsonParser.parseString(response.body()).asJsonObject)
                if (submission.finished && !wasFinished) {
                    log.info(summaryLine(submission))
                    onFinished(submission)
                }
            }

            404 -> submission.state = Submission.STATE_EXPIRED

            else -> {
                if (response != null && (status == 429 || response.statusCode() >= 500)) {
                    val seconds = retryAfterSeconds(response) ?: DEFAULT_RETRY_SECONDS
                    pollNotBefore = System.currentTimeMillis() + seconds * 1000L
                }
                log.warn("uuid.quest status poll of ${submission.submitId} failed ($status)", error)
            }
        }
        SubmissionHistory.save()
    }

}
