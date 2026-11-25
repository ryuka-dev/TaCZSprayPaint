package io.github.ryukadev.taczspray.client;

import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.item.PaintingItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The spray can's and the remover's own tooltip lines, wrapped to a readable width: one line in English is already long,
 * and some languages run half as long again.
 */
@Mod.EventBusSubscriber(modid = TaczSpray.MOD_ID, value = Dist.CLIENT)
public final class Descriptions {
    static final int WIDTH = 240;

    private Descriptions() {
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof PaintingItem item)) {
            return;
        }
        // the item's own lines: its description, and the spray can's line on ten-pulls
        String own = item.getDescriptionId() + ".";
        List<Component> lines = event.getToolTip();
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line.getContents() instanceof TranslatableContents t && t.getKey().startsWith(own)) {
                lines.remove(i);
                List<FormattedText> parts = Minecraft.getInstance().font.getSplitter()
                        .splitLines(line, WIDTH, Style.EMPTY);
                for (FormattedText part : parts) {
                    lines.add(i++, Component.literal(part.getString()).withStyle(line.getStyle()));
                }
                i--;
            }
        }
    }
}
