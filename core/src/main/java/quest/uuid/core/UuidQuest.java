package quest.uuid.core;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Runs for as long as the game does: collects UUIDs while in a world, submits and polls in the
 * background, and shows announcements once the player can see them. The platform calls {@link #update}
 * about once a second, also in the menus, and {@link #onStopping} when the client closes.
 */
public final class UuidQuest {

    /** How long closing the game may wait for the final batch. */
    public static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Platform platform;
    private final Settings settings;
    private final QuestLog log;
    private final ServerGuard guard = new ServerGuard();
    private final Announcer announcer = new Announcer();
    private final SubmissionHistory history;
    private final Submitter submitter;

    public UuidQuest(Platform platform, Settings settings, QuestLog log, Path historyFile, String userAgent) {
        this(platform, settings, log, new SubmissionHistory(historyFile, log), new HttpTransport(),
            System::currentTimeMillis, userAgent);
    }

    UuidQuest(Platform platform, Settings settings, QuestLog log, SubmissionHistory history, Transport transport,
              LongSupplier clock, String userAgent) {
        this.platform = platform;
        this.settings = settings;
        this.log = log;
        this.history = history;
        this.submitter = new Submitter(transport, history, guard, log, clock, userAgent);
        submitter.setOnAbuseThrottled(seconds ->
            warn("uuid.quest is blocking this network for " + seconds / 60 + " min after too many fake UUIDs"));
    }

    public Submitter submitter() {
        return submitter;
    }

    public SubmissionHistory history() {
        return history;
    }

    /** Client thread, about once a second. */
    public void update() {
        if (settings.enabled()) {
            collect();
            if (submitter.isDue(settings.interval())) {
                submitter.flush(settings.note(), this::verboseReport);
            }
        }
        submitter.pollUnfinished(this::onFinished);
        announcer.deliver(platform);
    }

    private void collect() {
        String address = platform.serverAddress();
        String server = address == null ? "singleplayer" : address;
        if (guard.isBlocked(server)) return;
        // uuid.quest keeps hashed IP prefixes per submission, so the own account is opt-in.
        UUID own = settings.submitOwnUuid() ? null : platform.ownUuid();
        if (settings.tabList()) platform.forEachListedPlayer((uuid, name) -> offer(uuid, name, own, server));
        if (settings.worldPlayers()) platform.forEachWorldPlayer((uuid, name) -> offer(uuid, name, own, server));
    }

    private void offer(UUID uuid, String name, UUID own, String server) {
        if (!uuid.equals(own) && PremiumUuid.isPremium(uuid, name)) submitter.offer(uuid, server);
    }

    private void onFinished(Submission submission) {
        if (ServerGuard.isSuspicious(submission)) {
            for (String server : guard.block(submission.servers())) {
                warn("Most UUIDs from " + server + " were fake (NPCs?), not collecting there until restart");
            }
        }

        int found = submission.count("forwarded");
        if (found > 0) {
            var message = QuestText.builder()
                .text("Found " + found + " new " + (found == 1 ? "UUID" : "UUIDs") + " (")
                .link(submission.submitId())
                .text(")")
                .build();
            announcer.announce(message, settings.announce().targets());
        }
        verboseReport(submission.summaryText());
    }

    /** Always shown in chat and as a notification, whatever {@code Announce} is set to. */
    private void warn(String message) {
        log.warn(message);
        announcer.announce(QuestText.of(message), EnumSet.allOf(AnnounceTarget.class), Platform.Severity.ERROR);
    }

    /** Shown in chat when {@code Verbose} is on; safe from any thread. */
    public void verboseReport(QuestText message) {
        if (settings.verbose()) announcer.announce(message, EnumSet.of(AnnounceTarget.CHAT));
    }

    /** Call when {@code Enabled} is switched off, so the pending UUIDs still go out. */
    public void onDisabled() {
        submitter.flush(settings.note(), this::verboseReport);
    }

    /** Sends the final batch, unless collecting is disabled. Blocks for up to {@link #SHUTDOWN_TIMEOUT}. */
    public void onStopping() {
        if (settings.enabled()) submitter.flushBlocking(settings.note(), SHUTDOWN_TIMEOUT);
    }

    /** {@code .uuidquest}: what was collected and what uuid.quest made of it. */
    public String totals() {
        return "Seen " + submitter.rememberedCount() + " this session, pending " + submitter.pendingCount() + ". "
            + "Submitted " + history.total("received") + ", new to uuid.quest " + history.total("forwarded")
            + ", already known " + history.total("alreadyKnown");
    }

    /** {@code .uuidquest submissions}: the last ten, newest first; empty if there are none. */
    public List<QuestText> recentSubmissions() {
        var lines = new ArrayList<QuestText>();
        for (Submission submission : history.recent(10)) {
            var details = new StringBuilder(" " + submission.sent() + " sent, " + submission.state());
            if (submission.finished()) details.append(", ").append(submission.summaryCounts());
            if (submission.note() != null) details.append(" | ").append(submission.note());
            lines.add(QuestText.builder()
                .text(TIME.format(Instant.ofEpochMilli(submission.createdAt())) + " ")
                .link(submission.submitId())
                .text(details.toString())
                .build());
        }
        return lines;
    }

    /**
     * {@code .uuidquest flush}: sends the pending UUIDs now and returns the immediate answer.
     * {@code report} later receives the outcome, on the HTTP thread.
     */
    public String flushNow(Consumer<QuestText> report) {
        Submitter.FlushResult result = submitter.flush(settings.note(), report);
        return switch (result) {
            case SENT -> result.reason() + " (" + submitter.pendingCount() + " left)";
            case WAITING -> result.reason() + ", " + submitter.waitSeconds() + "s left";
            default -> result.reason();
        };
    }

}
