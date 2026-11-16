package quest.uuid.core;

import com.google.gson.JsonParser;

import java.net.URI;
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

    public static final String WEBSITE = "https://uuid.quest/";
    private static final URI KEY_ENDPOINT = URI.create("https://uuid.quest/v1/key");
    private static final Duration KEY_CHECK_TIMEOUT = Duration.ofSeconds(15);

    /** How long closing the game may wait for the final batch. */
    public static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Platform platform;
    private final Settings settings;
    private final QuestLog log;
    private final ServerGuard guard;
    private final Announcer announcer = new Announcer();
    private final SubmissionHistory history;
    private final Submitter submitter;
    private final Transport transport;
    private final LongSupplier clock;
    private final String userAgent;
    private volatile UpdateChecker updateChecker;

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
        this.guard = new ServerGuard(clock);
        this.transport = transport;
        this.clock = clock;
        this.userAgent = userAgent;
        this.submitter = new Submitter(transport, history, guard, log, clock, userAgent);
        submitter.setApiKey(settings::apiKey);
        submitter.setOnAttribution(this::onAttribution);
        submitter.setOnWarning(warning -> warn("uuid.quest: " + warning));
        submitter.setOnAbuseThrottled(seconds ->
            warn("uuid.quest is blocking this network for " + seconds / 60 + " min after too many fake UUIDs"));
    }

    /**
     * {@code uuid-quest/1.0.0 (fabric; minecraft 1.21.4)}: the mod version without the jar's Minecraft range,
     * so uuid.quest can tell the variants and the Minecraft version actually played apart.
     */
    public static String userAgent(String version, String platform, String minecraft) {
        return "uuid-quest/" + UpdateChecker.modVersion(version) + " (" + platform + "; minecraft " + minecraft + ")";
    }

    public Submitter submitter() {
        return submitter;
    }

    public SubmissionHistory history() {
        return history;
    }

    /**
     * Turns on the GitHub release check, run once per session while {@code UpdateCheck} is on.
     *
     * @param version     the running version, {@code <mod version>+<minecraft range>}
     * @param assetPrefix start of this variant's jar names, e.g. {@code uuid-quest-fabric-}
     */
    public void enableUpdateCheck(Path cacheFile, String version, String assetPrefix) {
        updateChecker = new UpdateChecker(transport, cacheFile, log, clock, userAgent, version, assetPrefix);
    }

    /** Client thread, about once a second. */
    public void update() {
        UpdateChecker checker = updateChecker;
        if (checker != null && settings.updateCheck()) checker.checkOnce(this::onUpdateAvailable);
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
        boolean warned = false;
        if (ServerGuard.isSuspicious(submission)) {
            for (String server : guard.block(submission.servers())) {
                warn("Most UUIDs from " + server + " were fake (NPCs?), not collecting there for "
                    + ServerGuard.COOLDOWN.toMinutes() + " min");
                warned = true;
            }
        }
        // uuid.quest's own notice after the Mojang check, unless the guard already warned about the same thing.
        if (!warned && submission.earlyExited() && submission.warning() != null) {
            warn("uuid.quest: " + submission.warning());
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

    /** The key and outcome last told to the user, so each key is confirmed or rejected only once. */
    private volatile String lastAttribution;

    private void onAttribution(boolean attributed, String name) {
        reportKey(settings.apiKey().trim(), attributed, name);
    }

    private void reportKey(String key, boolean valid, String name) {
        String outcome = key + (valid ? "|ok" : "|rejected");
        if (outcome.equals(lastAttribution)) return;
        lastAttribution = outcome;
        if (valid) {
            log.info("uuid.quest credits submissions to " + name);
            announcer.announce(QuestText.of("uuid.quest credits your submissions to " + name), EnumSet.of(AnnounceTarget.CHAT));
        } else {
            // Shown in chat, unlike the other warnings: it answers the user setting a key.
            log.warn("uuid.quest did not accept the API key");
            announcer.announce(QuestText.of("uuid.quest did not accept your API key, so your submissions are anonymous."
                + " Check the ApiKey setting."), EnumSet.of(AnnounceTarget.CHAT));
        }
    }

    /**
     * Checks {@code key} with {@code GET /v1/key} right after it was set and reports the result in chat
     * (once in a world). Read-only on uuid.quest's side; an empty key just forgets the last result.
     */
    public void verifyApiKey(String key) {
        String trimmed = key == null ? "" : key.trim();
        lastAttribution = null;
        if (trimmed.isEmpty()) return;
        transport.sendAsync(new Transport.Request(KEY_ENDPOINT, null, KEY_CHECK_TIMEOUT, userAgent, trimmed))
            .whenComplete((response, error) -> {
                try {
                    if (response != null && response.status() == 200) {
                        String name = JsonParser.parseString(response.body()).getAsJsonObject().get("name").getAsString();
                        reportKey(trimmed, true, name);
                    } else if (response != null && response.status() == 401) {
                        reportKey(trimmed, false, null);
                    } else {
                        String status = response == null ? "no response" : String.valueOf(response.status());
                        log.warn("Could not check the API key (" + status + ")", error);
                        announcer.announce(QuestText.of("Could not check the API key right now (" + status
                            + "); the next submission will tell"), EnumSet.of(AnnounceTarget.CHAT));
                    }
                } catch (RuntimeException e) {
                    log.warn("Checking the API key failed", e);
                }
            });
    }

    private void onUpdateAvailable(UpdateChecker.Update update) {
        log.info("uuid.quest " + update.version() + " is available: " + update.url());
        var message = QuestText.builder()
            .text("uuid.quest " + update.version() + " is available: ")
            .link("download", update.url(), "Open " + update.url())
            .build();
        announcer.announce(message, EnumSet.allOf(AnnounceTarget.class));
    }

    /** Fake-rate and abuse warnings go to the log only. */
    private void warn(String message) {
        log.warn(message);
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
    public QuestText totals() {
        return QuestText.builder()
            .text("Seen " + submitter.rememberedCount() + " this session, pending " + submitter.pendingCount() + ". "
                + "Submitted " + history.total("received") + ", new to uuid.quest " + history.total("forwarded")
                + ", already known " + history.total("alreadyKnown") + ". ")
            .link("uuid.quest", WEBSITE, "Open " + WEBSITE)
            .build();
    }

    /** Submissions per page of {@link #submissionsPage}. */
    public static final int PAGE_SIZE = 10;

    /**
     * {@code .uuidquest submissions [page]}: page 1 holds the newest {@link #PAGE_SIZE}, each page listed oldest
     * first so the newest line ends up next to the chat input, followed by a line with clickable older/newer
     * buttons when there is more than one page. Empty if there are no submissions; out-of-range pages are clamped.
     */
    public List<QuestText> submissionsPage(int page) {
        List<Submission> all = history.recent(Integer.MAX_VALUE);
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(page, 1), pages);
        var slice = new ArrayList<>(all.subList(Math.min((current - 1) * PAGE_SIZE, all.size()),
            Math.min(current * PAGE_SIZE, all.size())));
        java.util.Collections.reverse(slice);

        var lines = new ArrayList<QuestText>();
        for (Submission submission : slice) {
            var details = new StringBuilder(" " + submission.sent() + " sent, " + submission.state());
            if (submission.finished()) details.append(", ").append(submission.summaryCounts());
            if (submission.attributedTo() != null) details.append(" | credited to ").append(submission.attributedTo());
            if (submission.note() != null) details.append(" | ").append(submission.note());
            lines.add(QuestText.builder()
                .text(TIME.format(Instant.ofEpochMilli(submission.createdAt())) + " ")
                .link(submission.submitId())
                .text(details.toString())
                .build());
        }
        if (pages > 1) {
            var nav = QuestText.builder();
            if (current < pages) {
                nav.command("« older", "submissions " + (current + 1), "Page " + (current + 1)).text("  ");
            }
            nav.text("page " + current + "/" + pages);
            if (current > 1) nav.text("  ").command("newer »", "submissions " + (current - 1), "Page " + (current - 1));
            lines.add(nav.build());
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
