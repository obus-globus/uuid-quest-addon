package quest.uuid.addon

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.utils.client.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Batches UUIDs and posts them to `POST https://uuid.quest/v1/uuids`.
 *
 * [offer] is called from the render thread only; the HTTP response is handled on the client's executor.
 */
object UuidQuestSubmitter {

    private val endpoint = URI("https://uuid.quest/v1/uuids")
    private const val MAX_BATCH = 10_000
    private const val MAX_REMEMBERED = 200_000
    private const val ERROR_BACKOFF_MS = 60_000L
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
    val acceptedTotal = AtomicInteger()

    @Volatile
    var lastSubmitId: String? = null
        private set

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
    fun flush(report: (String) -> Unit = {}) {
        val now = System.currentTimeMillis()
        if (pending.isEmpty() || now < notBefore || !inFlight.compareAndSet(false, true)) return
        lastFlush = now

        val batch = drainBatch()
        http.sendAsync(request(batch, REQUEST_TIMEOUT), HttpResponse.BodyHandlers.ofString())
            .whenComplete { response, error ->
                try {
                    handle(batch, response, error, report)
                } finally {
                    inFlight.set(false)
                }
            }
    }

    /**
     * Sends everything pending before the client exits, blocking for at most [timeout]. Waits for a
     * running request first and ignores any rate-limit wait, as there is no later chance.
     */
    fun flushBlocking(timeout: Duration) {
        val deadline = System.nanoTime() + timeout.toNanos()
        fun remaining() = Duration.ofNanos(deadline - System.nanoTime())

        while (inFlight.get() && !remaining().isNegative) Thread.sleep(50)
        notBefore = 0L

        while (pending.isNotEmpty() && remaining() > Duration.ZERO && notBefore == 0L) {
            if (!inFlight.compareAndSet(false, true)) return
            val batch = drainBatch()
            try {
                val response = http.send(request(batch, remaining()), HttpResponse.BodyHandlers.ofString())
                handle(batch, response, null) { logger.info(it) }
            } catch (e: Exception) {
                pending.addAll(batch)
                logger.warn("uuid.quest submission on shutdown failed, ${pending.size} UUIDs not sent", e)
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

    private fun request(batch: List<UUID>, timeout: Duration): HttpRequest {
        val body = JsonObject().apply {
            add("uuids", JsonArray().apply { batch.forEach { add(it.toString()) } })
        }
        return HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("User-Agent", "uuid-quest-addon")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
    }

    private fun handle(
        batch: List<UUID>,
        response: HttpResponse<String>?,
        error: Throwable?,
        report: (String) -> Unit,
    ) {
        val status = response?.statusCode()
        when {
            response == null || status == null -> {
                retryLater(batch, ERROR_BACKOFF_MS)
                logger.warn("uuid.quest submission failed, retrying later", error)
            }

            status == 202 -> {
                val json = JsonParser.parseString(response.body()).asJsonObject
                val queued = json.getAsJsonObject("counts").get("queued").asInt
                lastSubmitId = json.get("submitId").asString
                acceptedTotal.addAndGet(queued)
                report("uuid.quest accepted $queued of ${batch.size} UUIDs")
            }

            status == 429 -> {
                val retryAfter = response.headers().firstValue("Retry-After").map(String::toLongOrNull).orElse(null)
                retryLater(batch, (retryAfter ?: 60L) * 1000L)
                report("uuid.quest is rate limiting, retrying in ${retryAfter ?: 60}s")
            }

            status >= 500 -> {
                retryLater(batch, ERROR_BACKOFF_MS)
                logger.warn("uuid.quest answered $status, retrying later")
            }

            else -> {
                logger.warn("uuid.quest rejected ${batch.size} UUIDs ($status): ${response.body()}")
                report("uuid.quest rejected the submission ($status)")
            }
        }
    }

    private fun retryLater(batch: List<UUID>, delayMs: Long) {
        pending.addAll(batch)
        notBefore = System.currentTimeMillis() + delayMs
    }

}
