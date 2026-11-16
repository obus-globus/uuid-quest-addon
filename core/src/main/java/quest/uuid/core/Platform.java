package quest.uuid.core;

import java.util.UUID;
import java.util.function.BiConsumer;

/** What {@link UuidQuest} needs from the game client. All methods are called on the client thread. */
public interface Platform {

    enum Severity {
        SUCCESS,
        ERROR,
    }

    /** A player exists, so chat and notifications are visible. */
    boolean inWorld();

    void showChat(QuestText text);

    /** A notification (LiquidBounce) or toast (vanilla) titled "uuid.quest"; links show as their short id. */
    void showNotification(QuestText text, Severity severity);

    /** Address of the current server, null in singleplayer or the menus. */
    String serverAddress();

    /** The own account's UUID. */
    UUID ownUuid();

    /** UUID and name of every listed tab entry. */
    void forEachListedPlayer(BiConsumer<UUID, String> sink);

    /** UUID and name of every player entity in the world. */
    void forEachWorldPlayer(BiConsumer<UUID, String> sink);

}
