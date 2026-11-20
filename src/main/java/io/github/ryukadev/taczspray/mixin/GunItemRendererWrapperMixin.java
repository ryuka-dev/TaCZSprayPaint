package io.github.ryukadev.taczspray.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import io.github.ryukadev.taczspray.client.GunTextures;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes which gun is being drawn, so that {@link GunDisplayInstanceMixin} can give it its own texture. Both methods
 * draw synchronously, so the stack is set for exactly the time TaCZ reads the texture.
 */
@Mixin(value = GunItemRendererWrapper.class, remap = false)
public abstract class GunItemRendererWrapperMixin {
    @Inject(method = "renderFirstPerson", at = @At("HEAD"))
    private void taczspray$enterFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                                            MultiBufferSource buffers, int light, float partialTick, CallbackInfo ci) {
        GunTextures.enter(stack);
    }

    @Inject(method = "renderFirstPerson", at = @At("RETURN"))
    private void taczspray$exitFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                                           MultiBufferSource buffers, int light, float partialTick, CallbackInfo ci) {
        GunTextures.exit();
    }

    // renderByItem overrides a vanilla method: "renderByItem" in development, "m_108829_" in the released jars
    @Inject(method = {"renderByItem", "m_108829_"}, at = @At("HEAD"))
    private void taczspray$enterItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers,
                                     int light, int overlay, CallbackInfo ci) {
        GunTextures.enter(stack);
    }

    @Inject(method = {"renderByItem", "m_108829_"}, at = @At("RETURN"))
    private void taczspray$exitItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers,
                                    int light, int overlay, CallbackInfo ci) {
        GunTextures.exit();
    }
}
