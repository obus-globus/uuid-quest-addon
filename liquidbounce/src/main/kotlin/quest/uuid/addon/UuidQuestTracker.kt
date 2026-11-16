package quest.uuid.addon

import net.ccbluex.liquidbounce.event.EventListener

/** Drives [quest.uuid.core.UuidQuest.update] once a second for as long as the add-on is loaded, also in menus. */
object UuidQuestTracker : EventListener {

    internal var addon: EventListener? = null

    override fun parent() = addon

    init {
        every(20) { UuidQuestAddon.quest?.update() }
    }

}
