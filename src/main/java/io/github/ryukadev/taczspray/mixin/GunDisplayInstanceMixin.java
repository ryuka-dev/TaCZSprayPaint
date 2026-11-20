package io.github.ryukadev.taczspray.mixin;

import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import io.github.ryukadev.taczspray.client.GunTextures;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives the gun being drawn its own texture, for the close-up model and the far (LOD) one. Models stay TaCZ's. */
@Mixin(value = GunDisplayInstance.class, remap = false)
public abstract class GunDisplayInstanceMixin {
    @Inject(method = "getModelTexture", at = @At("RETURN"), cancellable = true)
    private void taczspray$texture(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation original = cir.getReturnValue();
        ResourceLocation resolved = GunTextures.resolve(original, false);
        if (resolved != original) {
            cir.setReturnValue(resolved);
        }
    }

    @Inject(method = "getLodModel", at = @At("RETURN"), cancellable = true)
    private void taczspray$lodTexture(CallbackInfoReturnable<Pair<BedrockGunModel, ResourceLocation>> cir) {
        Pair<BedrockGunModel, ResourceLocation> lod = cir.getReturnValue();
        if (lod == null) {
            return;
        }
        ResourceLocation resolved = GunTextures.resolve(lod.getRight(), true);
        if (resolved != lod.getRight()) {
            cir.setReturnValue(Pair.of(lod.getLeft(), resolved));
        }
    }
}
