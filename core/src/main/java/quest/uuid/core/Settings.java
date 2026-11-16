package quest.uuid.core;

/** What the user can configure; each platform stores these its own way. */
public interface Settings {

    int MIN_INTERVAL = 30;
    int MAX_INTERVAL = 600;
    int DEFAULT_INTERVAL = 60;
    int MAX_NOTE_LENGTH = 200;

    /** Collect and submit UUIDs. */
    boolean enabled();

    /** Seconds a UUID waits in the queue before the batch is sent, {@link #MIN_INTERVAL} to {@link #MAX_INTERVAL}. */
    int interval();

    /** Collect from the tab list. */
    boolean tabList();

    /** Collect from players in the world. */
    boolean worldPlayers();

    /** Also submit the own account; uuid.quest stores hashed IP prefixes with each submission. */
    boolean submitOwnUuid();

    /** Optional label sent with each submission. */
    String note();

    AnnounceMode announce();

    /** Show every submission's outcome in chat. */
    boolean verbose();

}
