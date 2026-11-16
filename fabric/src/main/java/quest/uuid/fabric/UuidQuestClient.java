package quest.uuid.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import quest.uuid.core.StoredSettings;
import quest.uuid.core.UuidQuest;

import java.nio.file.Path;

public final class UuidQuestClient implements ClientModInitializer {

    public static final String MOD_ID = "uuid-quest";

    private static UuidQuestClient instance;

    private StoredSettings settings;
    private UuidQuest quest;
    private int ticks;
    private boolean openConfig;

    @Override
    public void onInitializeClient() {
        instance = this;
        var log = new Slf4jLog();
        Path config = FabricLoader.getInstance().getConfigDir();
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");

        settings = StoredSettings.load(config.resolve("uuid-quest.json"), log);
        quest = new UuidQuest(new FabricPlatform(), settings, log,
            config.resolve("uuid-quest").resolve("submissions.json"), MOD_ID + "/" + version);

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> quest.onStopping());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> UuidQuestCommand.register(dispatcher, this));
        log.info("uuid.quest " + version + " ready, collecting " + (settings.enabled() ? "on" : "off"));
    }

    static UuidQuestClient instance() {
        return instance;
    }

    StoredSettings settings() {
        return settings;
    }

    UuidQuest quest() {
        return quest;
    }

    /** Applies {@code change} to the settings and flushes the queue if it switched collecting off. */
    void changeSettings(Runnable change) {
        boolean wasEnabled = settings.enabled();
        change.run();
        if (wasEnabled && !settings.enabled()) quest.onDisabled();
    }

    ConfigScreen configScreen(Screen parent) {
        return new ConfigScreen(parent, this);
    }

    /** From a chat command: the chat screen closes after the command runs, so open it on the next tick. */
    void openConfigNextTick() {
        openConfig = true;
    }

    private void onTick(Minecraft client) {
        if (openConfig) {
            openConfig = false;
            Compat.setScreen(client, configScreen(null));
        }
        if (++ticks % 20 == 0) quest.update();
    }

}
