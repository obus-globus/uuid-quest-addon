package quest.uuid.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import quest.uuid.core.AnnounceMode;
import quest.uuid.core.QuestText;
import quest.uuid.core.Settings;
import quest.uuid.core.StoredSettings;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@code /uuidquest} shows totals, {@code submissions} the recent submissions and their results,
 * {@code settings} the current settings, {@code flush} sends the pending UUIDs now, {@code set} changes a
 * setting and {@code config} opens the settings screen.
 *
 * <p>Plain Brigadier builders, as Fabric renamed its helper class in 26.1.
 */
final class UuidQuestCommand {

    private UuidQuestCommand() {
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, UuidQuestClient mod) {
        StoredSettings settings = mod.settings();
        dispatcher.register(literal("uuidquest")
            .executes(context -> {
                context.getSource().sendFeedback(Texts.toComponent(mod.quest().totals()));
                return Command.SINGLE_SUCCESS;
            })
            .then(literal("submissions")
                .executes(context -> showSubmissions(context, mod, 1))
                .then(argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> showSubmissions(context, mod, IntegerArgumentType.getInteger(context, "page")))))
            .then(literal("settings").executes(context -> showSettings(context, settings)))
            .then(literal("flush").executes(context -> flush(context, mod)))
            .then(literal("config").executes(context -> {
                mod.openConfigNextTick();
                return Command.SINGLE_SUCCESS;
            }))
            .then(literal("set")
                .then(toggle(mod, "enabled", settings::setEnabled))
                .then(literal("interval")
                    .then(argument("seconds", IntegerArgumentType.integer(Settings.MIN_INTERVAL, Settings.MAX_INTERVAL))
                        .executes(context -> {
                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                            mod.changeSettings(() -> settings.setInterval(seconds));
                            return reply(context, "Interval: " + seconds + " s");
                        })))
                .then(toggle(mod, "tabList", settings::setTabList))
                .then(toggle(mod, "worldPlayers", settings::setWorldPlayers))
                .then(toggle(mod, "submitOwnUuid", settings::setSubmitOwnUuid))
                .then(literal("note")
                    .executes(context -> {
                        mod.changeSettings(() -> settings.setNote(""));
                        return reply(context, "Note cleared");
                    })
                    .then(argument("text", StringArgumentType.greedyString())
                        .executes(context -> {
                            String note = StringArgumentType.getString(context, "text");
                            mod.changeSettings(() -> settings.setNote(note));
                            return reply(context, "Note: " + settings.note());
                        })))
                .then(literal("apikey")
                    .executes(context -> {
                        mod.changeSettings(() -> settings.setApiKey(""));
                        return reply(context, "API key cleared, submitting anonymously");
                    })
                    .then(argument("key", StringArgumentType.word())
                        .executes(context -> {
                            String key = StringArgumentType.getString(context, "key");
                            mod.changeSettings(() -> settings.setApiKey(key));
                            return reply(context, "API key: " + Settings.maskApiKey(settings.apiKey()));
                        })))
                .then(literal("announce")
                    .then(argument("mode", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (AnnounceMode mode : AnnounceMode.values()) builder.suggest(mode.tag());
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            AnnounceMode mode = AnnounceMode.byTag(StringArgumentType.getString(context, "mode"));
                            if (mode == null) {
                                context.getSource().sendError(Component.literal("Announce is one of Off, Chat, Notification, Both"));
                                return 0;
                            }
                            mod.changeSettings(() -> settings.setAnnounce(mode));
                            return reply(context, "Announce: " + mode.tag());
                        })))
                .then(toggle(mod, "verbose", settings::setVerbose))
                .then(toggle(mod, "updatecheck", settings::setUpdateCheck))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(UuidQuestClient mod, String name,
                                                                           Consumer<Boolean> setter) {
        return literal(name).then(argument("value", BoolArgumentType.bool()).executes(context -> {
            boolean value = BoolArgumentType.getBool(context, "value");
            mod.changeSettings(() -> setter.accept(value));
            return reply(context, name + ": " + value);
        }));
    }

    private static int reply(CommandContext<FabricClientCommandSource> context, String text) {
        context.getSource().sendFeedback(Texts.gray(text));
        return Command.SINGLE_SUCCESS;
    }

    private static int showSubmissions(CommandContext<FabricClientCommandSource> context, UuidQuestClient mod, int page) {
        List<QuestText> recent = mod.quest().submissionsPage(page);
        if (recent.isEmpty()) return reply(context, "No submissions yet");
        for (QuestText line : recent) context.getSource().sendFeedback(Texts.toComponent(line));
        return Command.SINGLE_SUCCESS;
    }

    private static int showSettings(CommandContext<FabricClientCommandSource> context, Settings settings) {
        reply(context, "Enabled: " + settings.enabled());
        reply(context, "Interval: " + settings.interval() + " s");
        reply(context, "TabList: " + settings.tabList());
        reply(context, "WorldPlayers: " + settings.worldPlayers());
        reply(context, "SubmitOwnUuid: " + settings.submitOwnUuid());
        reply(context, "Note: " + settings.note());
        reply(context, "Announce: " + settings.announce().tag());
        reply(context, "Verbose: " + settings.verbose());
        reply(context, "UpdateCheck: " + settings.updateCheck());
        reply(context, "ApiKey: " + Settings.maskApiKey(settings.apiKey()));
        return reply(context, "Change one with /uuidquest set <name> <value> or /uuidquest config");
    }

    private static int flush(CommandContext<FabricClientCommandSource> context, UuidQuestClient mod) {
        Minecraft mc = Minecraft.getInstance();
        String answer = mod.quest().flushNow(outcome -> mc.execute(() -> {
            if (mc.player != null) Compat.chat(mc, Texts.toComponent(outcome));
        }));
        return reply(context, answer);
    }

}
