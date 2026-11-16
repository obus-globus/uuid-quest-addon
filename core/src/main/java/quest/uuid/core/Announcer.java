package quest.uuid.core;

import java.util.EnumSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Queues messages from any thread and shows them once the player is in a world, since chat and
 * notifications are not visible from the menus.
 */
public final class Announcer {

    private record Announcement(QuestText message, Set<AnnounceTarget> targets, Platform.Severity severity) {
    }

    private final Queue<Announcement> queue = new ConcurrentLinkedQueue<>();

    public void announce(QuestText message, Set<AnnounceTarget> targets, Platform.Severity severity) {
        if (!targets.isEmpty()) queue.add(new Announcement(message, EnumSet.copyOf(targets), severity));
    }

    public void announce(QuestText message, Set<AnnounceTarget> targets) {
        announce(message, targets, Platform.Severity.SUCCESS);
    }

    /** Client thread only. */
    public void deliver(Platform platform) {
        if (!platform.inWorld()) return;
        Announcement announcement;
        while ((announcement = queue.poll()) != null) {
            if (announcement.targets().contains(AnnounceTarget.CHAT)) {
                platform.showChat(announcement.message());
            }
            if (announcement.targets().contains(AnnounceTarget.NOTIFICATION)) {
                platform.showNotification(announcement.message(), announcement.severity());
            }
        }
    }

    int size() {
        return queue.size();
    }

}
