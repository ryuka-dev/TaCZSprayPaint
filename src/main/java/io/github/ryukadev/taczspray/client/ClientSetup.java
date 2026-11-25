package io.github.ryukadev.taczspray.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.ryukadev.taczspray.PaintableGuns;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.client.compat.OculusPbr;
import io.github.ryukadev.taczspray.item.CanLabel;
import io.github.ryukadev.taczspray.item.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Item;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Mod.EventBusSubscriber(modid = TaczSpray.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        GunTextures.setSource(FinishTextures.instance());
        event.enqueueWork(OculusPbr::registerIfPresent);
        // a can or case for a series or one finish has its own icon (the overrides in models/item/)
        event.enqueueWork(() -> {
            for (var item : new Item[]{ModItems.SPRAY_CAN.get(), ModItems.SPRAY_CASE.get()}) {
                ItemProperties.register(item, TaczSpray.id("labelled"), (stack, level, entity, seed) -> CanLabel.of(stack)
                        .map(l -> l.series() != null || l.look() != null ? 1f : 0f).orElse(0f));
            }
        });
    }

    @SubscribeEvent
    public static void tooltipParts(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(WearBar.Tooltip.class, WearBar.Line::new);
    }

    @SubscribeEvent
    public static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(FinishTextures.instance());
        event.registerReloadListener((ResourceManagerReloadListener) ClientSetup::readPaintableGuns);
    }

    private static void readPaintableGuns(ResourceManager manager) {
        Set<String> guns = new HashSet<>();
        Optional<Resource> resource = manager.getResource(TaczSpray.id("finishes/guns.json"));
        if (resource.isPresent()) {
            try (InputStream in = resource.get().open()) {
                for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonArray()) {
                    guns.add(e.getAsString());
                }
            } catch (Exception e) {
                TaczSpray.LOG.error("Could not read the list of guns that have finishes", e);
            }
        }
        PaintableGuns.setClient(guns);
        GunModel.forget();
    }
}
