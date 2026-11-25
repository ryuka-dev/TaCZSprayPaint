package io.github.ryukadev.taczspray.item;

import com.google.gson.JsonObject;
import io.github.ryukadev.taczspray.Settings;
import io.github.ryukadev.taczspray.TaczSpray;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * {@code {"type": "taczspray:craftable", "item": "spray_can"}} in a recipe's {@code conditions}: the recipe loads only
 * while the server owner's settings let that item be crafted. Checked whenever the recipes load (start, {@code /reload}).
 */
public record CraftableCondition(String item) implements ICondition {
    public static final ResourceLocation ID = TaczSpray.id("craftable");

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return Settings.craftable(item);
    }

    public static final class Serializer implements IConditionSerializer<CraftableCondition> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, CraftableCondition value) {
            json.addProperty("item", value.item());
        }

        @Override
        public CraftableCondition read(JsonObject json) {
            return new CraftableCondition(json.get("item").getAsString());
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
