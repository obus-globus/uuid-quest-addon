package quest.uuid.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

final class TestSupport {

    private TestSupport() {
    }

    static UUID v4() {
        return UUID.randomUUID();
    }

    static UUID v3(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static final class Clock implements LongSupplier {
        long now = 1_000_000L;

        @Override
        public long getAsLong() {
            return now;
        }

        void advanceSeconds(long seconds) {
            now += seconds * 1000L;
        }
    }

    static final class RecordingLog implements QuestLog {
        final List<String> lines = new ArrayList<>();

        @Override
        public void info(String message) {
            lines.add("INFO " + message);
        }

        @Override
        public void warn(String message, Throwable error) {
            lines.add("WARN " + message);
        }

        @Override
        public void error(String message, Throwable error) {
            lines.add("ERROR " + message);
        }
    }

}
