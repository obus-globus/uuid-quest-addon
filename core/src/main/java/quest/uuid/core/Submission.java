package quest.uuid.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One {@code POST /v1/uuids} and what uuid.quest reported about it. {@code counts} mirrors the API's
 * {@code SubmissionCounts} (received, cracked, alreadyKnown, real, fake, forwarded, ...).
 * The field names are the history file's format.
 */
public final class Submission {

    public static final String STATE_DONE = "done";

    /** Not an API state: the status was polled after uuid.quest had already pruned it (404). */
    public static final String STATE_EXPIRED = "expired";

    private String submitId;
    private long createdAt;
    private int sent;
    private String note;
    private volatile String state;
    private volatile Map<String, Integer> counts;
    private volatile boolean earlyExited;
    private volatile long lastPolled;
    /** Addresses of the servers the UUIDs were seen on; null in entries saved before this was tracked. */
    private List<String> servers;
    /** Account name uuid.quest credited the submission to, null when it was anonymous. */
    private String attributedTo;
    /** uuid.quest's notice when the submission was flagged (mostly invalid or fake UUIDs), else null. */
    private volatile String warning;

    @SuppressWarnings("unused") // Gson
    private Submission() {
    }

    public Submission(String submitId, long createdAt, int sent, String note, String state,
                      Map<String, Integer> counts, List<String> servers) {
        this.submitId = submitId;
        this.createdAt = createdAt;
        this.sent = sent;
        this.note = note;
        this.state = state;
        this.counts = counts;
        this.servers = servers;
    }

    public String submitId() {
        return submitId;
    }

    public long createdAt() {
        return createdAt;
    }

    public int sent() {
        return sent;
    }

    public String note() {
        return note;
    }

    public String state() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean earlyExited() {
        return earlyExited;
    }

    public long lastPolled() {
        return lastPolled;
    }

    public void setLastPolled(long lastPolled) {
        this.lastPolled = lastPolled;
    }

    public List<String> servers() {
        return servers == null ? List.of() : servers;
    }

    public String attributedTo() {
        return attributedTo;
    }

    public String warning() {
        return warning;
    }

    void setWarning(String warning) {
        this.warning = warning;
    }

    void setAttributedTo(String attributedTo) {
        this.attributedTo = attributedTo;
    }

    public boolean finished() {
        return STATE_DONE.equals(state) || STATE_EXPIRED.equals(state);
    }

    public int count(String name) {
        Integer value = counts.get(name);
        return value == null ? 0 : value;
    }

    private static final java.util.regex.Pattern SUBMIT_ID = java.util.regex.Pattern.compile("[0-9A-Za-z-]{1,64}");

    /**
     * Gson skips the constructor, so an entry missing a field comes back with nulls; an id that is not a plain
     * token (it goes into the status URL) marks a damaged entry too.
     */
    boolean isComplete() {
        return submitId != null && SUBMIT_ID.matcher(submitId).matches() && state != null && counts != null;
    }

    /** Parses everything before assigning, so a malformed status cannot leave the submission half updated. */
    public void update(JsonObject status) {
        String newState = status.get("state").getAsString();
        Map<String, Integer> newCounts = parseCounts(status.getAsJsonObject("counts"));
        JsonElement early = status.get("earlyExited");
        boolean newEarlyExited = early != null && !early.isJsonNull() && early.getAsBoolean();
        String newWarning = text(status, "warning");
        counts = newCounts;
        earlyExited = newEarlyExited;
        if (newWarning != null) warning = newWarning;
        state = newState;
    }

    static String text(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() || value.getAsString().isBlank() ? null : value.getAsString();
    }

    static Map<String, Integer> parseCounts(JsonObject counts) {
        var parsed = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, JsonElement> entry : counts.entrySet()) {
            parsed.put(entry.getKey(), entry.getValue().getAsInt());
        }
        return parsed;
    }

    /** {@code 3 new, 39 known, 0 fake}. */
    public String summaryCounts() {
        String summary = count("forwarded") + " new, " + count("alreadyKnown") + " known, " + count("fake") + " fake";
        return earlyExited ? summary + ", stopped early" : summary;
    }

    /** For the log: {@code uuid.quest <submitId>: 3 new, 39 known, 0 fake}. */
    public String summaryLine() {
        return "uuid.quest " + submitId + ": " + summaryCounts();
    }

    /** For chat, with a clickable id. */
    public QuestText summaryText() {
        return QuestText.builder().text("uuid.quest ").link(submitId).text(": " + summaryCounts()).build();
    }

}
