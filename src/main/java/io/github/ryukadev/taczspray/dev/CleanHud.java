package io.github.ryukadev.taczspray.dev;

import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Recording help, part of the dev kit ({@code -Dtaczspray.devkit=true}): the client command {@code /hidehud} toggles
 * every HUD overlay off and on (hotbar, crosshair, health, chat, TaCZ's ammo counter), unlike F1 leaving the hand and
 * the held gun drawn, so a gun inspect can be filmed clean. Screens are not overlays and still open. Not part of the
 * mod's behaviour.
 */
public final class CleanHud {
    private static boolean hidden;

    private CleanHud() {
    }

    public static void start() {
        MinecraftForge.EVENT_BUS.addListener(CleanHud::commands);
        MinecraftForge.EVENT_BUS.addListener(CleanHud::overlay);
    }

    private static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hidehud").executes(context -> {
            hidden = !hidden;
            return 1;
        }));
    }

    private static void overlay(RenderGuiOverlayEvent.Pre event) {
        if (hidden) {
            event.setCanceled(true);
        }
    }
}
