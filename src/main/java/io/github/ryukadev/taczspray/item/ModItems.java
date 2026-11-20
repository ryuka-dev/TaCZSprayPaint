package io.github.ryukadev.taczspray.item;

import io.github.ryukadev.taczspray.TaczSpray;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TaczSpray.MOD_ID);

    public static final RegistryObject<Item> SPRAY_CAN =
            ITEMS.register("spray_can", () -> new SprayCanItem(new Item.Properties()));
    public static final RegistryObject<Item> PAINT_REMOVER =
            ITEMS.register("paint_remover", () -> new PaintRemoverItem(new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SPRAY_CAN);
            event.accept(PAINT_REMOVER);
        }
    }
}
