package io.github.ryukadev.taczspray.mixin;

import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.CubesItem;
import io.github.ryukadev.taczspray.client.DepthNudge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives every cube of a TaCZ model its {@link DepthNudge}, counted in load order. */
@Mixin(value = BedrockModel.class, remap = false)
public abstract class BedrockModelMixin {
    @Unique
    private int taczspray$cube;

    @Inject(method = {"loadNewModel", "loadLegacyModel"}, at = @At("HEAD"))
    private void taczspray$startCounting(BedrockModelPOJO pojo, CallbackInfo ci) {
        taczspray$cube = 0;
    }

    @Redirect(method = {"loadNewModel", "loadLegacyModel"},
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/resource/pojo/model/CubesItem;getInflate()F"))
    private float taczspray$nudgedInflate(CubesItem cube) {
        return cube.getInflate() + DepthNudge.forCube(taczspray$cube++);
    }
}
