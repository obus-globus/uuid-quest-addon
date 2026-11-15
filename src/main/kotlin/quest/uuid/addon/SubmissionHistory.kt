package quest.uuid.addon

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import net.ccbluex.liquidbounce.config.ConfigSystem

/**
 * One `POST /v1/uuids` and what uuid.quest reported about it. [counts] mirrors the API's
 * `SubmissionCounts` (received, cracked, alreadyKnown, real, fake, forwarded, ...).
 */
class Submission(
    val submitId: String,
    val createdAt: Long,
    val sent: Int,
    val note: String?,
    var state: String,
    var counts: Map<String, Int>,
    var earlyExited: Boolean = false,
    var lastPolled: Long = 0L,
) {
    val finished get() = state == STATE_DONE || state == STATE_EXPIRED

    fun count(name: String) = counts[name] ?: 0

    fun update(status: JsonObject) {
        state = status.get("state").asString
        counts = status.getAsJsonObject("counts").entrySet().associate { (key, value) -> key to value.asInt }
        earlyExited = status.get("earlyExited")?.asBoolean ?: false
    }

    companion object {
        const val STATE_DONE = "done"

        /** Not an API state: the status was polled after uuid.quest had already pruned it (404). */
        const val STATE_EXPIRED = "expired"
    }
}

/**
 * The most recent submissions, kept in `LiquidBounce/uuid-quest/submissions.json` so unfinished ones
 * are still polled after a restart.
 */
object SubmissionHistory {

    private const val MAX_KEPT = 100

    private val file = ConfigSystem.rootFolder.resolve("uuid-quest").resolve("submissions.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val submissions = ArrayList<Submission>()

    init {
        if (file.isFile) {
            runCatching {
                val type = object : TypeToken<List<Submission>>() {}.type
                submissions.addAll(gson.fromJson<List<Submission>>(file.readText(), type))
            }.onFailure { log.warn("Could not read $file", it) }
        }
    }

    @Synchronized
    fun add(submission: Submission) {
        submissions.add(submission)
        while (submissions.size > MAX_KEPT) submissions.removeFirst()
        save()
    }

    @Synchronized
    fun recent(limit: Int): List<Submission> = submissions.takeLast(limit).reversed()

    @Synchronized
    fun unfinished(): List<Submission> = submissions.filter { !it.finished }

    @Synchronized
    fun total(count: String) = submissions.sumOf { it.count(count) }

    @Synchronized
    fun save() {
        runCatching {
            file.parentFile.mkdirs()
            file.writeText(gson.toJson(submissions))
        }.onFailure { log.warn("Could not write $file", it) }
    }

}
