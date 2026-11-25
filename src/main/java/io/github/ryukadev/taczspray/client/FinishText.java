package io.github.ryukadev.taczspray.client;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.TenPull;
import net.minecraft.ChatFormatting;
import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;

/** How a finish is named: in the reveal and as two lines in a painted gun's tooltip. */
@Mod.EventBusSubscriber(modid = io.github.ryukadev.taczspray.TaczSpray.MOD_ID, value = Dist.CLIENT)
public final class FinishText {
    private FinishText() {
    }

    public static Component grade(int grade) {
        return Component.translatable("taczspray.grade." + grade);
    }

    public static Component band(int band) {
        return Component.translatable("taczspray.band." + band);
    }

    public static String wear(double wear) {
        return String.format(Locale.ROOT, "%.6f", wear);
    }

    /** Under the gun's name: the finish's name in its grade's colour and the grade; then the band and the wear. */
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (TenPull.of(event.getItemStack()) != null) {
            List<Component> lines = event.getToolTip();
            lines.add(Math.min(1, lines.size()), Component.translatable("taczspray.tooltip.pending", TenPull.SIZE)
                    .withStyle(ChatFormatting.GOLD));
        }
        Finish finish = Finish.of(event.getItemStack());
        if (finish == null) {
            return;
        }
        Style colour = Style.EMPTY.withColor(TextColor.fromRgb(RevealScreen.gradeColour(finish.grade())));
        MutableComponent name = ClientCatalog.lookName(finish.look()).copy().withStyle(colour)
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(grade(finish.grade()).copy().withStyle(colour));
        MutableComponent wear = band(finish.band()).copy().withStyle(ChatFormatting.GRAY)
                .append(Component.literal(" " + wear(finish.wear())).withStyle(ChatFormatting.DARK_GRAY));
        List<Component> lines = event.getToolTip();
        int at = Math.min(1, lines.size());
        lines.add(at, wear);
        lines.add(at, name);
    }

    /** Under the band and wear line, the wear bar (a tooltip can only draw such a part at this later stage). */
    @SubscribeEvent
    public static void wearBar(RenderTooltipEvent.GatherComponents event) {
        Finish finish = Finish.of(event.getItemStack());
        if (finish == null) {
            return;
        }
        String line = band(finish.band()).getString() + " " + wear(finish.wear());
        List<Either<FormattedText, TooltipComponent>> parts = event.getTooltipElements();
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).left().map(t -> t.getString().equals(line)).orElse(false)) {
                parts.add(i + 1, Either.right(new WearBar.Tooltip(finish.band(), finish.wear())));
                return;
            }
        }
    }
}
