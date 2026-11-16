package quest.uuid.addon

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.notification
import quest.uuid.core.Platform
import quest.uuid.core.QuestText
import java.util.UUID
import java.util.function.BiConsumer

/** Chat and notifications through LiquidBounce; players from the vanilla client. */
object LiquidBouncePlatform : Platform {

    override fun inWorld() = mc.player != null

    override fun showChat(text: QuestText) = chat(text.toComponent())

    override fun showNotification(text: QuestText, severity: Platform.Severity) {
        val level = when (severity) {
            Platform.Severity.SUCCESS -> NotificationEvent.Severity.SUCCESS
            Platform.Severity.ERROR -> NotificationEvent.Severity.ERROR
        }
        notification("uuid.quest", text.plain(), level)
    }

    override fun serverAddress(): String? = mc.currentServer?.ip

    override fun ownUuid(): UUID = mc.user.profileId

    override fun forEachListedPlayer(sink: BiConsumer<UUID, String>) {
        mc.connection?.listedOnlinePlayers?.forEach { info -> sink.accept(info.profile.id, info.profile.name) }
    }

    override fun forEachWorldPlayer(sink: BiConsumer<UUID, String>) {
        mc.level?.players()?.forEach { player -> sink.accept(player.uuid, player.gameProfile.name) }
    }

}
