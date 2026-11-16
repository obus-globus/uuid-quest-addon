package quest.uuid.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import quest.uuid.core.Platform;
import quest.uuid.core.QuestText;

import java.util.UUID;
import java.util.function.BiConsumer;

/** Chat lines and vanilla toasts; players from the tab list and the client level. */
final class FabricPlatform implements Platform {

    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public boolean inWorld() {
        return mc.player != null;
    }

    @Override
    public void showChat(QuestText text) {
        if (mc.player != null) Compat.chat(mc, Texts.toComponent(text));
    }

    @Override
    public void showNotification(QuestText text, Severity severity) {
        Component title = Component.literal(severity == Severity.ERROR ? "uuid.quest warning" : "uuid.quest");
        Compat.toast(mc, title, Component.literal(text.plain()));
    }

    @Override
    public String serverAddress() {
        ServerData server = mc.getCurrentServer();
        return server == null ? null : server.ip;
    }

    @Override
    public UUID ownUuid() {
        return mc.getUser().getProfileId();
    }

    @Override
    public void forEachListedPlayer(BiConsumer<UUID, String> sink) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return;
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            sink.accept(Compat.id(info.getProfile()), Compat.name(info.getProfile()));
        }
    }

    @Override
    public void forEachWorldPlayer(BiConsumer<UUID, String> sink) {
        ClientLevel level = mc.level;
        if (level == null) return;
        for (AbstractClientPlayer player : level.players()) {
            sink.accept(player.getUUID(), Compat.name(player.getGameProfile()));
        }
    }

}
