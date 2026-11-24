package io.github.ryukadev.taczspray.dev;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.FinishTables;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.function.Consumer;

/**
 * Development check, off unless the game is started with {@code -Dtaczspray.selftest=true}. In a singleplayer world,
 * with no input: audits every mixin; puts a spray can in the main hand and an AK-47 in the off hand and finishes a
 * spray on the server (the real roll, the real reveal); screenshots the reveal, then the painted gun in hand next to
 * two more painted ones on the ground; then takes the paint off with the remover and checks it is gone; quits. Along
 * the way it checks that a can and a remover are used up only when they do their work, and not in creative mode.
 * The gun is {@code -Dtaczspray.selftest.gun=<TaCZ gun id>} (default tacz:ak47). Not part of the mod's behaviour.
 */
public final class SelfTest {
    public static final String PROPERTY = "taczspray.selftest";
    private static final String NO_FINISHES = "tacz:glock_17";

    private int ticks = -1;

    private SelfTest() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void start() {
        MinecraftForge.EVENT_BUS.addListener(new SelfTest()::tick);
    }

    public static void audit() {
        try {
            MixinEnvironment.getCurrentEnvironment().audit();
            TaczSpray.LOG.info("[selftest] mixin audit passed");
        } catch (Throwable t) {
            TaczSpray.LOG.error("[selftest] mixin audit failed", t);
        }
    }

    private void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.level == null) {
            return;
        }
        ticks++;
        switch (ticks) {
            case 60 -> onServer(mc, SelfTest::setUp);
            case 80 -> onServer(mc, SelfTest::spray);
            case 250 -> {
                TaczSpray.LOG.info("[selftest] reveal screen open: {}", mc.screen != null);
                shot(mc, "reveal");
            }
            case 270 -> {
                mc.setScreen(null);
                onServer(mc, player -> {
                    ItemStack gun = player.getOffhandItem().copy();
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    player.getInventory().setItem(0, gun);
                });
                mc.player.getInventory().selected = 0;
                mc.player.setXRot(55f);
            }
            case 340 -> {
                TaczSpray.LOG.info("[selftest] painted gun in hand: {}", Finish.of(mc.player.getMainHandItem()));
                shot(mc, "painted");
            }
            case 350 -> onServer(mc, player -> {
                ItemStack gun = player.getMainHandItem().copy();
                player.setItemInHand(InteractionHand.OFF_HAND, gun);
                ItemStack remover = new ItemStack(ModItems.PAINT_REMOVER.get());
                player.getInventory().setItem(0, remover);
                GameType mode = player.gameMode.getGameModeForPlayer();
                player.setGameMode(GameType.SURVIVAL);
                remover.getItem().finishUsingItem(remover, player.level(), player);
                player.setGameMode(mode);
                TaczSpray.LOG.info("[selftest] after the remover: {}; removers left {} (expect 0)",
                        Finish.of(player.getOffhandItem()), player.getMainHandItem().getCount());
            });
            case 380 -> {
                TaczSpray.LOG.info("[selftest] done");
                mc.stop();
            }
            default -> {
            }
        }
    }

    /**
     * In survival: a gun with no finishes takes no can; a spray takes one; then in creative a spray takes none. The
     * player's game mode is put back after.
     */
    private static void spray(ServerPlayer player) {
        ItemStack can = player.getMainHandItem();
        ItemStack gun = player.getOffhandItem();
        GameType mode = player.gameMode.getGameModeForPlayer();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.OFF_HAND, gun(NO_FINISHES));
        can.getItem().finishUsingItem(can, player.level(), player);
        int refused = can.getCount();
        player.setItemInHand(InteractionHand.OFF_HAND, gun);
        can.getItem().finishUsingItem(can, player.level(), player);
        int sprayed = can.getCount();
        player.setGameMode(GameType.CREATIVE);
        can.getItem().finishUsingItem(can, player.level(), player);
        int creative = can.getCount();
        player.setGameMode(mode);
        TaczSpray.LOG.info("[selftest] sprayed: {}", Finish.of(player.getOffhandItem()));
        TaczSpray.LOG.info("[selftest] cans 2 -> {} on a gun with no finishes, {} after a spray, {} after one in "
                + "creative (expect 2, 1, 1)", refused, sprayed, creative);
    }

    private static void setUp(ServerPlayer player) {
        // the test world is reused: clear the guns earlier runs left, and see in the dark
        player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32))
                .forEach(ItemEntity::discard);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60, 0, false, false));
        String gunId = System.getProperty("taczspray.selftest.gun", "tacz:ak47");
        player.getInventory().setItem(0, new ItemStack(ModItems.SPRAY_CAN.get(), 2));
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.OFF_HAND, gun(gunId));
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        for (int i = 0; i < 2; i++) {
            ItemStack lying = gun(gunId);
            FinishTables.current().roll(gunId, player.getRandom()::nextDouble).ifPresent(f -> f.writeTo(lying));
            double side = i == 0 ? 0.5 : -0.5;
            drop(player, lying, look.scale(1.2).add(look.z * side, 0, -look.x * side));
        }
        TaczSpray.LOG.info("[selftest] set up with {} finishes loaded", FinishTables.current().size());
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> work) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            TaczSpray.LOG.error("[selftest] needs a singleplayer world");
            return;
        }
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                work.accept(player);
            }
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "taczspray-selftest-" + name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private static ItemStack gun(String gunId) {
        return DevGuns.make(gunId);
    }

    private static void drop(ServerPlayer player, ItemStack stack, Vec3 offset) {
        ItemEntity item = new ItemEntity(player.level(), player.getX() + offset.x, player.getY() + 0.2,
                player.getZ() + offset.z, stack, 0, 0, 0);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        player.level().addFreshEntity(item);
    }
}
