package quest.uuid.fabric;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import quest.uuid.core.AnnounceMode;
import quest.uuid.core.Settings;
import quest.uuid.core.StoredSettings;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
//? if <1.20.2
/*import net.minecraft.client.gui.GuiGraphics;*/

/**
 * The settings, built from vanilla widgets only. Edits a draft and stores it when the screen closes
 * (Done or Escape).
 */
final class ConfigScreen extends Screen {

    private static final int COLUMN = 150;
    private static final int GAP = 10;
    private static final int ROW = 24;
    private static final int INTERVAL_STEP = 5;

    private final Screen parent;
    private final UuidQuestClient mod;
    private final Draft draft;

    /** A mutable copy of the settings. */
    private static final class Draft implements Settings {
        boolean enabled;
        int interval;
        boolean tabList;
        boolean worldPlayers;
        boolean submitOwnUuid;
        String note;
        AnnounceMode announce;
        boolean verbose;
        boolean updateCheck;

        Draft(Settings from) {
            enabled = from.enabled();
            interval = from.interval();
            tabList = from.tabList();
            worldPlayers = from.worldPlayers();
            submitOwnUuid = from.submitOwnUuid();
            note = from.note();
            announce = from.announce();
            verbose = from.verbose();
            updateCheck = from.updateCheck();
        }

        @Override public boolean enabled() { return enabled; }
        @Override public int interval() { return interval; }
        @Override public boolean tabList() { return tabList; }
        @Override public boolean worldPlayers() { return worldPlayers; }
        @Override public boolean submitOwnUuid() { return submitOwnUuid; }
        @Override public String note() { return note; }
        @Override public AnnounceMode announce() { return announce; }
        @Override public boolean verbose() { return verbose; }
        @Override public boolean updateCheck() { return updateCheck; }
    }

    ConfigScreen(Screen parent, UuidQuestClient mod) {
        super(Component.literal("uuid.quest"));
        this.parent = parent;
        this.mod = mod;
        this.draft = new Draft(mod.settings());
    }

    @Override
    protected void init() {
        int left = width / 2 - COLUMN - GAP / 2;
        int right = width / 2 + GAP / 2;
        int full = 2 * COLUMN + GAP;
        int y = 20;

        addRenderableWidget(centered(title, y));
        y += 16;

        addRenderableWidget(toggle(left, y, "Enabled", () -> draft.enabled, value -> draft.enabled = value,
            "Collect and submit UUIDs."));
        addRenderableWidget(toggle(right, y, "Verbose", () -> draft.verbose, value -> draft.verbose = value,
            "Show the outcome of every submission in chat."));
        y += ROW;
        addRenderableWidget(toggle(left, y, "Tab list", () -> draft.tabList, value -> draft.tabList = value,
            "Collect UUIDs from the tab list."));
        addRenderableWidget(toggle(right, y, "World players", () -> draft.worldPlayers,
            value -> draft.worldPlayers = value, "Collect UUIDs from players in the world."));
        y += ROW;
        addRenderableWidget(toggle(left, y, "Own UUID", () -> draft.submitOwnUuid, value -> draft.submitOwnUuid = value,
            "Also submit your own account. uuid.quest stores hashed IP prefixes with each submission."));
        addRenderableWidget(Button.builder(announceLabel(), button -> {
                draft.announce = draft.announce.next();
                button.setMessage(announceLabel());
            })
            .bounds(right, y, COLUMN, 20)
            .tooltip(Tooltip.create(Component.literal(
                "Where to announce UUIDs that were new to uuid.quest. Shown once you are in a world.")))
            .build());
        y += ROW;
        addRenderableWidget(toggle(left, y, "Update check", () -> draft.updateCheck, value -> draft.updateCheck = value,
            "Look for a newer release on GitHub, at most every 6 hours."));
        y += ROW;
        addRenderableWidget(new IntervalSlider(left, y, full));
        y += ROW + 4;

        addRenderableWidget(centered(
            Component.literal("Note (sent with each submission, up to " + Settings.MAX_NOTE_LENGTH + " characters)"), y));
        y += 12;
        var note = new EditBox(font, left, y, full, 20, Component.literal("Note"));
        note.setMaxLength(Settings.MAX_NOTE_LENGTH);
        note.setValue(draft.note);
        note.setResponder(value -> draft.note = value);
        addRenderableWidget(note);
        y += ROW + 8;

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
            .bounds(width / 2 - 100, y, 200, 20)
            .build());
    }

    @Override
    public void onClose() {
        mod.changeSettings(() -> mod.settings().apply(draft));
        Compat.setScreen(minecraft, parent);
    }

    //? if <1.20.2 {
    /*// 1.20.1 screens draw their own background; fill() also exists in 1.20.2, which this jar supports too.
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        super.render(graphics, mouseX, mouseY, delta);
    }
    *///?}

    /** Sized to the text, since StringWidget's own alignment differs between versions. */
    private StringWidget centered(Component text, int y) {
        int textWidth = font.width(text);
        return new StringWidget(width / 2 - textWidth / 2, y, textWidth, 9, text, font);
    }

    private Button toggle(int x, int y, String label, BooleanSupplier get, Consumer<Boolean> set, String description) {
        return Button.builder(toggleLabel(label, get.getAsBoolean()), button -> {
                set.accept(!get.getAsBoolean());
                button.setMessage(toggleLabel(label, get.getAsBoolean()));
            })
            .bounds(x, y, COLUMN, 20)
            .tooltip(Tooltip.create(Component.literal(description)))
            .build();
    }

    private static Component toggleLabel(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    private Component announceLabel() {
        return Component.literal("Announce: " + draft.announce.tag());
    }

    private final class IntervalSlider extends AbstractSliderButton {

        private static final int RANGE = Settings.MAX_INTERVAL - Settings.MIN_INTERVAL;

        IntervalSlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), (draft.interval - Settings.MIN_INTERVAL) / (double) RANGE);
            setTooltip(Tooltip.create(Component.literal("Seconds a UUID waits before its batch is sent.")));
            updateMessage();
        }

        private int seconds() {
            int raw = Settings.MIN_INTERVAL + (int) Math.round(value * RANGE);
            return StoredSettings.clampInterval(Math.round(raw / (float) INTERVAL_STEP) * INTERVAL_STEP);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Interval: " + draft.interval + " s"));
        }

        @Override
        protected void applyValue() {
            draft.interval = seconds();
        }
    }

}
