package quest.uuid.addon

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import net.ccbluex.liquidbounce.utils.client.mc
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
    /** Addresses of the servers the UUIDs were seen on; null in entries saved before this was tracked. */
    val servers: List<String>? = null,
) {
    val finished get() = state == STATE_DONE || state == STATE_EXPIRED

    fun count(name: String) = counts[name] ?: 0

    /** Gson skips the constructor, so an entry missing a field comes back with nulls in non-null fields. */
    @Suppress("SENSELESS_COMPARISON")
    val isComplete get() = submitId != null && state != null && counts != null

    /** Parses everything before assigning, so a malformed status cannot leave the submission half updated. */
    fun update(status: JsonObject) {
        val newState = status.get("state").asString
        val newCounts = parseCounts(status.getAsJsonObject("counts"))
        val newEarlyExited = status.get("earlyExited")?.asBoolean ?: false
        counts = newCounts
        earlyExited = newEarlyExited
        state = newState
    }

    companion object {
        const val STATE_DONE = "done"

        /** Not an API state: the status was polled after uuid.quest had already pruned it (404). */
        const val STATE_EXPIRED = "expired"
    }
}

fun parseCounts(counts: JsonObject): Map<String, Int> =
    counts.entrySet().associate { (key, value) -> key to value.asInt }

/**
 * The most recent submissions, kept in `LiquidBounce/uuid-quest/submissions.json` so unfinished ones
 * are still polled after a restart.
 */
object SubmissionHistory {

    private const val MAX_KEPT = 100

    private val file = File(mc.gameDirectory, "LiquidBounce").resolve("uuid-quest").resolve("submissions.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val submissions = ArrayList<Submission>()

    init {
        if (file.isFile) {
            runCatching {
                val type = object : TypeToken<List<Submission?>>() {}.type
                val loaded = gson.fromJson<List<Submission?>>(file.readText(), type).orEmpty()
                submissions.addAll(loaded.filterNotNull().filter(Submission::isComplete))
            }.onFailure {
                val backup = File(file.parentFile, "submissions.json.bak")
                file.renameTo(backup)
                log.warn("Could not read $file, moved it to $backup", it)
            }
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

    /** Writes a temporary file and moves it over the old one, so a crash mid-write keeps the previous file. */
    @Synchronized
    fun save() {
        runCatching {
            file.parentFile.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(gson.toJson(submissions))
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { log.warn("Could not write $file", it) }
    }

}
