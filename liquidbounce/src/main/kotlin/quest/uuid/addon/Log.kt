package quest.uuid.addon

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import quest.uuid.core.QuestLog

/** Same naming as the logger LiquidBounce gives add-ons, usable from the add-on's objects. */
internal val log: Logger = LogManager.getLogger("LiquidBounce/Addon/uuid-quest")

internal object AddonLog : QuestLog {
    override fun info(message: String) = log.info(message)
    override fun warn(message: String, error: Throwable?) = log.warn(message, error)
    override fun error(message: String, error: Throwable?) = log.error(message, error)
}
