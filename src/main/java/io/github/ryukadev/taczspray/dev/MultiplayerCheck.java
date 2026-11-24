package io.github.ryukadev.taczspray.dev;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;

/**
 * Development check for a dedicated server, off unless it is started with {@code -Dtaczspray.mpcheck=true}. Once two
 * players are on: the first sprays the gun in their off hand for real (the server's roll, the reveal sent to them),
 * then holds it, facing the second; later takes the paint off with the remover. Then it says {@link #DONE} to
 * everyone (clients running {@link MultiplayerWatch} quit on it) and stops the server. Not part of the mod's behaviour.
 */
public final class MultiplayerCheck {
    public static final String PROPERTY = "taczspray.mpcheck";
    public static final String DONE = "[taczspray] multiplayer check done";
    private static final int SETTLE = 200;

    private int ticks = -1;

    private MultiplayerCheck() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void start() {
        MinecraftForge.EVENT_BUS.addListener(new MultiplayerCheck()::tick);
    }

    private void tick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.phase != TickEvent.Phase.END || server == null) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (ticks < 0 && players.size() < 2) {
            return;
        }
        ticks++;
        if (players.size() < 2) {
            TaczSpray.LOG.error("[mp] a player left during the check");
            finish(server);
            return;
        }
        ServerPlayer a = players.get(0);
        ServerPlayer b = players.get(1);
        switch (ticks) {
            case SETTLE -> setUp(a, b);
            case SETTLE + 20 -> {
                ItemStack can = a.getMainHandItem();
                can.getItem().finishUsingItem(can, a.level(), a);
                TaczSpray.LOG.info("[mp] server: {} sprayed: {}", name(a), Finish.of(a.getOffhandItem()));
            }
            case SETTLE + 200 -> {
                // the reveal has played on the sprayer's client; now hold the gun up for the other player to see
                ItemStack gun = a.getOffhandItem().copy();
                a.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                a.setItemInHand(InteractionHand.MAIN_HAND, gun);
                face(a, b);
                TaczSpray.LOG.info("[mp] server: {} holds {}", name(a), Finish.of(a.getMainHandItem()));
            }
            case SETTLE + 400 -> {
                ItemStack gun = a.getMainHandItem().copy();
                a.setItemInHand(InteractionHand.OFF_HAND, gun);
                ItemStack remover = new ItemStack(ModItems.PAINT_REMOVER.get());
                a.setItemInHand(InteractionHand.MAIN_HAND, remover);
                remover.getItem().finishUsingItem(remover, a.level(), a);
                ItemStack bare = a.getOffhandItem().copy();
                a.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                a.setItemInHand(InteractionHand.MAIN_HAND, bare);
                face(a, b);
                TaczSpray.LOG.info("[mp] server: after the remover {} holds {}", name(a), Finish.of(bare));
            }
            case SETTLE + 520 -> finish(server);
            default -> {
            }
        }
    }

    private static void setUp(ServerPlayer a, ServerPlayer b) {
        for (ServerPlayer p : List.of(a, b)) {
            p.getInventory().clearContent();
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 5, 0, false, false));
        }
        a.serverLevel().setDayTime(6000);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SPRAY_CAN.get()));
        a.setItemInHand(InteractionHand.OFF_HAND, DevGuns.make("tacz:ak47"));
        double x = a.getX();
        double y = a.getY();
        double z = a.getZ();
        a.connection.teleport(x, y, z, 0f, 0f);
        b.connection.teleport(x, y, z + 2.5, 180f, 10f);
        TaczSpray.LOG.info("[mp] server: set up {} (sprays) and {} (watches)", name(a), name(b));
    }

    /** The holder turns side-on to the watcher, so the gun is seen from its side. */
    private static void face(ServerPlayer holder, ServerPlayer watcher) {
        holder.connection.teleport(holder.getX(), holder.getY(), holder.getZ(), 90f, 0f);
        watcher.connection.teleport(watcher.getX(), watcher.getY(), watcher.getZ(), 180f, 10f);
    }

    private void finish(MinecraftServer server) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(DONE), false);
        TaczSpray.LOG.info("[mp] server: done");
        ticks = Integer.MIN_VALUE / 2;
        server.halt(false);
    }

    private static String name(ServerPlayer p) {
        return p.getGameProfile().getName();
    }
}
