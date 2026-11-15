package quest.uuid.addon

import net.ccbluex.liquidbounce.config.types.group.ValueGroup

/**
 * Stored in `LiquidBounce/uuidquest.json`; change with `.value set uuidQuest.settings.<name> <value>`.
 */
object UuidQuestSettings : ValueGroup("Settings") {

    val enabled by boolean("Enabled", true).onChanged { enabled ->
        if (!enabled) UuidQuestSubmitter.flush(note, UuidQuestTracker::verboseReport)
    }
    val interval by int("Interval", 60, 30..600, "s")
    val tabList by boolean("TabList", true)
    val worldPlayers by boolean("WorldPlayers", true)
    val note by text("Note", "")
    val announce by enumChoice("Announce", AnnounceMode.NOTIFICATION)
    val verbose by boolean("Verbose", false)

}
