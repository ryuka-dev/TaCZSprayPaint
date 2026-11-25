package io.github.ryukadev.taczspray.item;

import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.TenPull;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TaczSpray.MOD_ID);

    public static final RegistryObject<Item> SPRAY_CAN =
            ITEMS.register("spray_can", () -> new SprayCanItem(new Item.Properties(), 1));
    /** Ten finishes at once, one to keep. */
    public static final RegistryObject<Item> SPRAY_CASE =
            ITEMS.register("spray_case", () -> new SprayCanItem(new Item.Properties(), TenPull.SIZE));
    public static final RegistryObject<Item> PAINT_REMOVER =
            ITEMS.register("paint_remover", () -> new PaintRemoverItem(new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::creativeTab);
        modBus.addListener(ModItems::conditions);
    }

    private static void conditions(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
            CraftingHelper.register(CraftableCondition.Serializer.INSTANCE);
        }
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SPRAY_CAN);
            event.accept(SPRAY_CASE);
            event.accept(PAINT_REMOVER);
        }
    }
}
