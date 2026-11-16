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
                submissions.addAll(read());
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
    /** The usable entries in the file; throws if it cannot be read or parsed. */
    private List<Submission> read() throws IOException {
        List<Submission> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST_TYPE);
        var usable = new ArrayList<Submission>();
        if (loaded != null) loaded.stream().filter(Objects::nonNull).filter(Submission::isComplete).forEach(usable::add);
        return usable;
    }

    /**
     * Writes the newest {@link #MAX_KEPT} of this instance's submissions together with any another game instance
     * sharing the folder has written, so neither overwrites the other's.
     */
    public synchronized void save() {
        var merged = new ArrayList<>(submissions);
        if (Files.isRegularFile(file)) {
            try {
                var known = new java.util.HashSet<String>();
                submissions.forEach(submission -> known.add(submission.submitId()));
                for (Submission other : read()) {
                    if (known.add(other.submitId())) merged.add(other);
                }
            } catch (Exception e) {
                // An unreadable file is simply replaced by this instance's submissions.
            }
        }
        merged.sort(java.util.Comparator.comparingLong(Submission::createdAt));
        List<Submission> kept = merged.subList(Math.max(0, merged.size() - MAX_KEPT), merged.size());
        try {
            AtomicFiles.write(file, GSON.toJson(kept, LIST_TYPE));
        } catch (IOException e) {
            log.warn("Could not write " + file, e);
        }
    }

}
