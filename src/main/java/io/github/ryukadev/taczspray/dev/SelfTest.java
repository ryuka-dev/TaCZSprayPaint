package io.github.ryukadev.taczspray.dev;

import com.tacz.guns.api.item.builder.GunItemBuilder;
import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.TaczSpray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Development check, off unless the game is started with {@code -Dtaczspray.selftest=true}: audits every mixin, then
 * in a singleplayer world puts a painted and a plain AK-47 in the first two hotbar slots, drops a heavily worn painted
 * one and a plain one at the player's feet, takes a screenshot with each in hand and quits. The finish is
 * {@code -Dtaczspray.selftest.finish=<id>} (default {@value #DEFAULT_FINISH}). Not part of the mod's behaviour.
 */
public final class SelfTest {
    public static final String PROPERTY = "taczspray.selftest";
    private static final String DEFAULT_FINISH = "ak47_sakura";

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
            case 60 -> give(mc);
            case 100 -> {
                mc.player.getInventory().selected = 0;
                mc.player.setXRot(55f);
            }
            case 200 -> {
                TaczSpray.LOG.info("[selftest] painted gun in hand: {}", Finish.of(mc.player.getMainHandItem()));
                Screenshot.grab(mc.gameDirectory, "taczspray-selftest-painted.png", mc.getMainRenderTarget(), c -> {});
                mc.player.getInventory().selected = 1;
            }
            case 300 -> {
                TaczSpray.LOG.info("[selftest] plain gun in hand: {}", Finish.of(mc.player.getMainHandItem()));
                Screenshot.grab(mc.gameDirectory, "taczspray-selftest-plain.png", mc.getMainRenderTarget(), c -> {});
            }
            case 320 -> {
                TaczSpray.LOG.info("[selftest] done");
                mc.stop();
            }
            default -> {
            }
        }
    }

    private static void give(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            TaczSpray.LOG.error("[selftest] needs a singleplayer world");
            return;
        }
        String finish = System.getProperty("taczspray.selftest.finish", DEFAULT_FINISH);
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                return;
            }
            // the test world is reused: clear the guns earlier runs left, and see in the dark
            player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16))
                    .forEach(ItemEntity::discard);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60, 0, false, false));
            ItemStack painted = gun();
            new Finish(finish, 0.05, 1234, 567).writeTo(painted);
            ItemStack worn = gun();
            new Finish(finish, 0.9, 1234, 567).writeTo(worn);
            ItemStack plain = gun();
            player.getInventory().setItem(0, painted);
            player.getInventory().setItem(1, plain.copy());
            Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
            drop(player, worn, look.scale(1.2).add(look.z * 0.5, 0, -look.x * 0.5));
            drop(player, plain, look.scale(1.2).add(-look.z * 0.5, 0, look.x * 0.5));
        });
    }

    private static ItemStack gun() {
        ResourceLocation id = ResourceLocation.tryBuild("tacz", "ak47");
        ItemStack stack = GunItemBuilder.create().setId(id).build();
        return stack.isEmpty() ? GunItemBuilder.create().setId(id).forceBuild() : stack;
    }

    private static void drop(ServerPlayer player, ItemStack stack, Vec3 offset) {
        ItemEntity item = new ItemEntity(player.level(), player.getX() + offset.x, player.getY() + 0.2,
                player.getZ() + offset.z, stack, 0, 0, 0);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        player.level().addFreshEntity(item);
    }
}
