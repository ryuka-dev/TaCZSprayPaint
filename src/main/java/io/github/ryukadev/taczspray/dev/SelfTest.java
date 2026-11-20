package io.github.ryukadev.taczspray.dev;

import com.tacz.guns.api.item.builder.GunItemBuilder;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.client.GunTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Development check, off unless the game is started with {@code -Dtaczspray.selftest=true}: audits every mixin, then
 * in a singleplayer world puts a marked and a plain gun in the first two hotbar slots, drops one of each at the
 * player's feet, takes a screenshot with each in hand and quits. Marked guns are drawn with a flat test texture, so
 * the screenshots show at a glance which paths repaint. Not part of the mod's behaviour.
 */
public final class SelfTest {
    public static final String PROPERTY = "taczspray.selftest";
    private static final String MARK = "taczspray_selftest";
    private static final ResourceLocation TEST_TEXTURE =
            ResourceLocation.tryBuild("minecraft", "textures/block/magenta_glazed_terracotta.png");

    private int ticks = -1;
    private int painted;

    private SelfTest() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void start() {
        SelfTest test = new SelfTest();
        GunTextures.setSource(new GunTextures.Source() {
            @Override
            public boolean paints(ItemStack gun) {
                return gun.hasTag() && gun.getTag().getBoolean(MARK);
            }

            @Override
            public ResourceLocation texture(ItemStack gun, ResourceLocation original, boolean lod) {
                test.painted++;
                return TEST_TEXTURE;
            }
        });
        MinecraftForge.EVENT_BUS.addListener(test::tick);
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
                TaczSpray.LOG.info("[selftest] marked gun in hand, painted {} times so far", painted);
                Screenshot.grab(mc.gameDirectory, "taczspray-selftest-marked.png", mc.getMainRenderTarget(), c -> {});
                mc.player.getInventory().selected = 1;
            }
            case 300 -> {
                TaczSpray.LOG.info("[selftest] plain gun in hand, painted {} times so far", painted);
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
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                return;
            }
            ItemStack marked = gun();
            marked.getOrCreateTag().putBoolean(MARK, true);
            ItemStack plain = gun();
            player.getInventory().setItem(0, marked.copy());
            player.getInventory().setItem(1, plain.copy());
            Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
            drop(player, marked, look.scale(1.2).add(look.z * 0.5, 0, -look.x * 0.5));
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
                player.getZ() + offset.z, stack.copy(), 0, 0, 0);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        player.level().addFreshEntity(item);
    }
}
