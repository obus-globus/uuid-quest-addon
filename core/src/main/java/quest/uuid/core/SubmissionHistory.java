package quest.uuid.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** The most recent submissions, kept in a JSON file so unfinished ones are still polled after a restart. */
public final class SubmissionHistory {

    static final int MAX_KEPT = 100;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Submission>>() {
    }.getType();

    private final Path file;
    private final QuestLog log;
    private final List<Submission> submissions = new ArrayList<>();

    /** Reads {@code file}; a file that cannot be parsed is moved to {@code <name>.bak}. */
    public SubmissionHistory(Path file, QuestLog log) {
        this.file = file;
        this.log = log;
        if (Files.isRegularFile(file)) {
            try {
                List<Submission> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST_TYPE);
                if (loaded != null) {
                    loaded.stream().filter(Objects::nonNull).filter(Submission::isComplete).forEach(submissions::add);
                }
            } catch (Exception e) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                try {
                    Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveError) {
                    e.addSuppressed(moveError);
                }
                log.warn("Could not read " + file + ", moved it to " + backup, e);
            }
        }
    }

    public synchronized void add(Submission submission) {
        submissions.add(submission);
        while (submissions.size() > MAX_KEPT) submissions.remove(0);
        save();
    }

    /** Newest first. */
    public synchronized List<Submission> recent(int limit) {
        var recent = new ArrayList<>(submissions.subList(Math.max(0, submissions.size() - limit), submissions.size()));
        Collections.reverse(recent);
        return recent;
    }

    public synchronized List<Submission> unfinished() {
        return submissions.stream().filter(submission -> !submission.finished()).toList();
    }

    public synchronized int total(String count) {
        return submissions.stream().mapToInt(submission -> submission.count(count)).sum();
    }

    public synchronized int size() {
        return submissions.size();
    }

    /** Writes a temporary file and moves it over the old one, so a crash mid-write keeps the previous file. */
    public synchronized void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(submissions, LIST_TYPE), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Could not write " + file, e);
        }
    }

}
