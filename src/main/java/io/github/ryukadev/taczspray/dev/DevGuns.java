package io.github.ryukadev.taczspray.dev;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Guns for the development tools, made the way TaCZ's gun smith table makes them. Not part of the mod's behaviour. */
final class DevGuns {
    private DevGuns() {
    }

    /**
     * A gun as crafted: on the first fire mode its data lists. Left on {@code UNKNOWN} (the builder's default), guns
     * whose animation scripts pick their idle pose by fire mode, such as the SPAS-12, hold their arms in the model's
     * rest pose.
     */
    static ItemStack make(String gunId) {
        ResourceLocation id = ResourceLocation.tryParse(gunId);
        GunItemBuilder builder = GunItemBuilder.create().setId(id);
        TimelessAPI.getCommonGunIndex(id)
                .map(index -> index.getGunData().getFireModeSet())
                .filter(modes -> !modes.isEmpty())
                .map(modes -> modes.get(0))
                .ifPresent(builder::setFireMode);
        ItemStack stack = builder.build();
        return stack.isEmpty() ? builder.forceBuild() : stack;
    }
}
