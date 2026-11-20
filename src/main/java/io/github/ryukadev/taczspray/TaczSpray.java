package io.github.ryukadev.taczspray;

import com.mojang.logging.LogUtils;
import io.github.ryukadev.taczspray.dev.SelfTest;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(TaczSpray.MOD_ID)
public final class TaczSpray {
    public static final String MOD_ID = "taczspray";
    public static final Logger LOG = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.tryBuild(MOD_ID, path);
    }

    // FMLJavaModLoadingContext.get() is deprecated in late Forge 47 builds but is the one form every 47.x accepts
    @SuppressWarnings("removal")
    public TaczSpray() {
        if (FMLEnvironment.dist == Dist.CLIENT && SelfTest.enabled()) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLLoadCompleteEvent event) -> SelfTest.audit());
            SelfTest.start();
        }
    }
}
