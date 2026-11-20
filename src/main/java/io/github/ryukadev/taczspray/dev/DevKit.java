package io.github.ryukadev.taczspray.dev;

import com.tacz.guns.api.item.builder.GunItemBuilder;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Development help, off unless the game is started with {@code -Dtaczspray.devkit=true}: a player who joins gets a
 * spray can, a paint remover and a few guns to try them on, one of them a gun with no finishes (to see the refusal).
 * Not part of the mod's behaviour.
 */
public final class DevKit {
    public static final String PROPERTY = "taczspray.devkit";
    private static final String[] GUNS = {"tacz:ak47", "tacz:m4a1", "tacz:ai_awp", "tacz:deagle", "tacz:glock_17"};

    private DevKit() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void start() {
        MinecraftForge.EVENT_BUS.addListener(DevKit::joined);
    }

    private static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.getInventory().add(new ItemStack(ModItems.SPRAY_CAN.get()));
        player.getInventory().add(new ItemStack(ModItems.PAINT_REMOVER.get()));
        for (String id : GUNS) {
            ResourceLocation gun = ResourceLocation.tryParse(id);
            ItemStack stack = GunItemBuilder.create().setId(gun).build();
            player.getInventory().add(stack.isEmpty() ? GunItemBuilder.create().setId(gun).forceBuild() : stack);
        }
        TaczSpray.LOG.info("[devkit] gave {} the spray can, the remover and {} guns", player.getName().getString(),
                GUNS.length);
    }
}
