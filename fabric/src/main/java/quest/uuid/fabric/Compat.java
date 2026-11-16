package quest.uuid.fabric;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.util.UUID;

/** Every Minecraft call whose shape differs between the supported versions. */
final class Compat {

    private Compat() {
    }

    static UUID id(GameProfile profile) {
        //? if >=1.21.9 {
        return profile.id();
        //?} else {
        /*return profile.getId();
        *///?}
    }

    static String name(GameProfile profile) {
        //? if >=1.21.9 {
        return profile.name();
        //?} else {
        /*return profile.getName();
        *///?}
    }

    /** Adds a line to the chat; needs a player. */
    static void chat(Minecraft mc, Component text) {
        //? if >=26.1 {
        mc.player.sendSystemMessage(text);
        //?} else {
        /*mc.player.displayClientMessage(text, false);
        *///?}
    }

    /**
     * 1.21.2 renamed getToasts() to getToastManager() in Mojang's names only; the intermediary name stayed,
     * so the jar built against 1.20.3 runs on 1.21.2 to 1.21.4 too.
     */
    static void toast(Minecraft mc, Component title, Component body) {
        //? if >=26.2 {
        SystemToast.add(mc.gui.toastManager(), new SystemToast.SystemToastId(), title, body);
        //?} elif >=1.21.2 {
        /*SystemToast.add(mc.getToastManager(), new SystemToast.SystemToastId(), title, body);
        *///?} elif >=1.20.3 {
        /*SystemToast.add(mc.getToasts(), new SystemToast.SystemToastId(), title, body);
        *///?} else {
        /*SystemToast.add(mc.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, body);
        *///?}
    }

    static void setScreen(Minecraft mc, Screen screen) {
        //? if >=26.2 {
        mc.gui.setScreen(screen);
        //?} else {
        /*mc.setScreen(screen);
        *///?}
    }

    /** Runs the chat command {@code command} (with its slash) when clicked and shows {@code hover} on mouse-over. */
    static Style runCommand(Style style, String command, Component hover) {
        //? if >=1.21.5 {
        return style.withClickEvent(new ClickEvent.RunCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(hover));
        //?} else {
        /*return style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        *///?}
    }

    /** Opens {@code url} when clicked and shows {@code hover} on mouse-over. */
    static Style link(Style style, String url, Component hover) {
        //? if >=1.21.5 {
        return style.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
            .withHoverEvent(new HoverEvent.ShowText(hover));
        //?} else {
        /*return style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        *///?}
    }

}
