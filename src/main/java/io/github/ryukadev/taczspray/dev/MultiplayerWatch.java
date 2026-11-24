package io.github.ryukadev.taczspray.dev;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.client.ClientCatalog;
import io.github.ryukadev.taczspray.client.RevealScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Development check for a client on a server running {@link MultiplayerCheck}, off unless the client is started with
 * {@code -Dtaczspray.mpwatch=true}. Logs every change it sees in what the players near it hold in the main hand (the
 * finish as this client reads it from the gun) and when the reveal opens, screenshots each a moment later, and quits
 * when the server says the check is done (or after five minutes). Not part of the mod's behaviour.
 */
public final class MultiplayerWatch {
    public static final String PROPERTY = "taczspray.mpwatch";
    private static final int SHOT_DELAY = 40;
    private static final int REVEAL_SHOT_DELAY = 140;
    private static final int GIVE_UP = 20 * 60 * 5;

    private final Map<String, String> held = new HashMap<>();
    private int ticks;
    private int shots;
    private int shootAt = -1;
    private String shotName;
    private boolean revealSeen;
    private int closeRevealAt = -1;
    private int quitAt = -1;
    private boolean catalogLogged;

    private MultiplayerWatch() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void start() {
        MultiplayerWatch watch = new MultiplayerWatch();
        MinecraftForge.EVENT_BUS.addListener(watch::tick);
        MinecraftForge.EVENT_BUS.addListener(watch::chat);
    }

    private void chat(ClientChatReceivedEvent event) {
        if (quitAt < 0 && event.getMessage().getString().contains(MultiplayerCheck.DONE)) {
            TaczSpray.LOG.info("[mp] client: the server says done");
            quitAt = ticks + 20;
        }
    }

    private void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        if (ticks == quitAt || ticks == GIVE_UP) {
            TaczSpray.LOG.info("[mp] client: quitting{}", ticks == GIVE_UP ? " (gave up waiting)" : "");
            mc.stop();
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        String me = mc.player.getGameProfile().getName();
        if (!catalogLogged && ticks > 100) {
            catalogLogged = true;
            TaczSpray.LOG.info("[mp] client {}: series selftest_metal is called \"{}\"", me,
                    ClientCatalog.seriesName("selftest_metal"));
        }
        if (!revealSeen && mc.screen instanceof RevealScreen) {
            revealSeen = true;
            TaczSpray.LOG.info("[mp] client {}: reveal opened", me);
            schedule(REVEAL_SHOT_DELAY, me + "-reveal");
            closeRevealAt = ticks + REVEAL_SHOT_DELAY + 5;
        }
        if (ticks == closeRevealAt && mc.screen instanceof RevealScreen) {
            mc.setScreen(null);
        }
        for (Player p : mc.level.players()) {
            String name = p.getGameProfile().getName();
            Finish finish = Finish.of(p.getMainHandItem());
            String now = p.getMainHandItem().isEmpty() ? "nothing"
                    : p.getMainHandItem().getItem().toString() + " " + (finish == null ? "unpainted" : finish);
            if (!now.equals(held.put(name, now))) {
                TaczSpray.LOG.info("[mp] client {}: {} holds {}", me, name, now);
                if (!name.equals(me) && !p.getMainHandItem().isEmpty()) {
                    schedule(SHOT_DELAY, me + "-sees-" + name + "-" + (finish == null ? "unpainted" : "painted"));
                }
            }
        }
        if (ticks == shootAt && shotName != null) {
            shots++;
            Screenshot.grab(mc.gameDirectory, String.format("taczspray-mp-%d-%s.png", shots, shotName),
                    mc.getMainRenderTarget(), c -> {});
            shotName = null;
        }
    }

    private void schedule(int delay, String name) {
        shootAt = ticks + delay;
        shotName = name;
    }
}
