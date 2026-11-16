package quest.uuid.core;

/** The platform's logger. */
public interface QuestLog {

    void info(String message);

    void warn(String message, Throwable error);

    default void warn(String message) {
        warn(message, null);
    }

    void error(String message, Throwable error);

}
