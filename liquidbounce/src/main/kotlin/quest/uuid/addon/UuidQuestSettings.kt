package quest.uuid.addon

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import quest.uuid.core.Settings

/** LiquidBounce's choice type for [quest.uuid.core.AnnounceMode]; the tags are what the config file stores. */
enum class AnnounceMode(override val tag: String, val core: quest.uuid.core.AnnounceMode) : Tagged {
    OFF("Off", quest.uuid.core.AnnounceMode.OFF),
    CHAT("Chat", quest.uuid.core.AnnounceMode.CHAT),
    NOTIFICATION("Notification", quest.uuid.core.AnnounceMode.NOTIFICATION),
    BOTH("Both", quest.uuid.core.AnnounceMode.BOTH),
}

/**
 * Stored in `LiquidBounce/uuidquest.json`; change with `.value set uuidQuest.settings.<name> <value>`.
 */
object UuidQuestSettings : ValueGroup("Settings") {

    val enabled by boolean("Enabled", true).onChanged { enabled ->
        if (!enabled) UuidQuestAddon.quest?.onDisabled()
    }
    val interval by int("Interval", Settings.DEFAULT_INTERVAL, Settings.MIN_INTERVAL..Settings.MAX_INTERVAL, "s")
    val tabList by boolean("TabList", true)
    val worldPlayers by boolean("WorldPlayers", true)
    val submitOwnUuid by boolean("SubmitOwnUuid", false)
    val note by text("Note", "")
    val announce by enumChoice("Announce", AnnounceMode.OFF)
    val verbose by boolean("Verbose", false)
    val updateCheck by boolean("UpdateCheck", true)
    // Loading the config at startup also fires onChanged, so only changes made after that are checked.
    val apiKey by text("ApiKey", "").onChanged { key ->
        if (UuidQuestAddon.started) UuidQuestAddon.quest?.verifyApiKey(key)
    }

    /** The settings as core reads them. */
    object View : Settings {
        override fun enabled() = UuidQuestSettings.enabled
        override fun interval() = UuidQuestSettings.interval
        override fun tabList() = UuidQuestSettings.tabList
        override fun worldPlayers() = UuidQuestSettings.worldPlayers
        override fun submitOwnUuid() = UuidQuestSettings.submitOwnUuid
        override fun note() = UuidQuestSettings.note
        override fun announce() = UuidQuestSettings.announce.core
        override fun verbose() = UuidQuestSettings.verbose
        override fun updateCheck() = UuidQuestSettings.updateCheck
        override fun apiKey() = UuidQuestSettings.apiKey
    }

}
