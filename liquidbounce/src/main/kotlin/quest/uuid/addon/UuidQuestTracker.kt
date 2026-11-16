package quest.uuid.addon

import net.ccbluex.liquidbounce.event.EventListener

/** Drives [quest.uuid.core.UuidQuest.update] once a second for as long as the add-on is loaded, also in menus. */
object UuidQuestTracker : EventListener {

    internal var addon: EventListener? = null

    override fun parent() = addon

    init {
        // An exception would end the every() loop for the rest of the session, so it is logged instead.
        every(20) {
            runCatching { UuidQuestAddon.quest?.update() }.onFailure { AddonLog.error("uuid.quest update failed", it) }
        }
    }

}
