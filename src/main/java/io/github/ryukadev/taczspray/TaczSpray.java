package io.github.ryukadev.taczspray;

import com.mojang.logging.LogUtils;
import io.github.ryukadev.taczspray.dev.DevKit;
import io.github.ryukadev.taczspray.dev.SelfTest;
import io.github.ryukadev.taczspray.item.ModItems;
import io.github.ryukadev.taczspray.net.Network;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
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
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(Network::register));
        if (DevKit.enabled()) {
            DevKit.start();
        }
        if (FMLEnvironment.dist == Dist.CLIENT && SelfTest.enabled()) {
            modBus.addListener((FMLLoadCompleteEvent event) -> SelfTest.audit());
            SelfTest.start();
        }
    }
}
