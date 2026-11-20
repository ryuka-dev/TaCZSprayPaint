package io.github.ryukadev.taczspray.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.item.ExternalGunItemVboRenderer;
import com.tacz.guns.client.renderer.item.StaticGunRenderResult;
import io.github.ryukadev.taczspray.client.GunTextures;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TaCZ 1.2.0 and later draw third-person, dropped and framed guns from cached meshes that carry the display's
 * texture. A repainted gun leaves that path and is drawn the regular way, where {@link GunDisplayInstanceMixin}
 * applies. Not applied on TaCZ versions without this class ({@link TaczSprayMixinPlugin}).
 */
@Mixin(value = ExternalGunItemVboRenderer.class, remap = false)
public abstract class ExternalGunItemVboRendererMixin {
    @Inject(method = "tryRender", at = @At("HEAD"), cancellable = true)
    private static void taczspray$regularPathWhenPainted(ItemStack stack, ItemDisplayContext context, boolean useLod,
                                                         PoseStack pose, MultiBufferSource buffers, int light,
                                                         int overlay, CallbackInfoReturnable<StaticGunRenderResult> cir) {
        if (GunTextures.paints(stack)) {
            cir.setReturnValue(StaticGunRenderResult.UNSUPPORTED);
        }
    }
}
