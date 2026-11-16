package quest.uuid.fabric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quest.uuid.core.QuestLog;

final class Slf4jLog implements QuestLog {

    private final Logger logger = LoggerFactory.getLogger("uuid-quest");

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message, Throwable error) {
        logger.warn(message, error);
    }

    @Override
    public void error(String message, Throwable error) {
        logger.error(message, error);
    }

}
