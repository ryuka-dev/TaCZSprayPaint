package io.github.ryukadev.taczspray.item;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.FinishTable;
import io.github.ryukadev.taczspray.FinishTables;
import io.github.ryukadev.taczspray.PaintableGuns;
import io.github.ryukadev.taczspray.Series;
import io.github.ryukadev.taczspray.client.ClientCatalog;
import io.github.ryukadev.taczspray.net.Network;
import io.github.ryukadev.taczspray.net.RevealMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Sprays the off-hand gun with a finish rolled anew every time: pattern, grade and wear. One can a spray. A plain can
 * draws from every finish (or the series the server owner set for plain cans); a labelled one ({@link CanLabel}) from
 * a series or gives one finish.
 */
public final class SprayCanItem extends PaintingItem {
    private static final int[] MIST = {0xE04848, 0xF2C14E, 0x58B368, 0x4A8FE0, 0xB060D0, 0xF08CB0};

    public SprayCanItem(Properties properties) {
        super(properties);
    }

    /** The pool a can draws from on this server, or why it cannot. */
    private record Draw(@Nullable FinishTable.Pool pool, @Nullable Component refusal) {
    }

    @Nullable
    @Override
    protected Component refusal(Player player, ItemStack self, ItemStack gun) {
        if (!(player instanceof ServerPlayer server)) {
            return PaintableGuns.clientKnows(FinishTables.gunId(gun)) ? null
                    : Component.translatable("taczspray.message.no_finishes");
        }
        return draw(self, gun, server.getLanguage()).refusal();
    }

    private static Draw draw(ItemStack can, ItemStack gun, String language) {
        String id = FinishTables.gunId(gun);
        FinishTable table = FinishTables.current();
        Optional<CanLabel> label = CanLabel.of(can);
        if (label.isEmpty()) {
            return new Draw(null, Component.translatable("taczspray.message.bad_can"));
        }
        CanLabel l = label.get();
        if (l.look() != null) {
            FinishTable.Pool pool = FinishTable.Pool.of(l.look());
            return table.paints(id, pool) ? new Draw(pool, null) : new Draw(null,
                    Component.translatable("taczspray.message.no_look", FinishTables.lookName(l.look(), language)));
        }
        if (l.series() != null) {
            Series series = FinishTables.series().get(l.series());
            if (series == null) {
                return new Draw(null, Component.translatable("taczspray.message.no_series", l.series()));
            }
            return table.paints(id, series.pool()) ? new Draw(series.pool(), null) : new Draw(null,
                    Component.translatable("taczspray.message.series_no_gun", series.name(language)));
        }
        FinishTables.Plain plain = FinishTables.plain();
        if (plain.pool() == null) {
            return new Draw(null, Component.translatable("taczspray.message.no_series", plain.missingSeries()));
        }
        return table.paints(id, plain.pool()) ? new Draw(plain.pool(), null)
                : new Draw(null, Component.translatable("taczspray.message.no_finishes"));
    }

    @Override
    protected boolean apply(ServerPlayer player, ItemStack self, ItemStack gun) {
        Draw draw = draw(self, gun, player.getLanguage());
        String id = FinishTables.gunId(gun);
        FinishTable table = FinishTables.current();
        var random = player.getRandom();
        Optional<Finish> rolled = draw.pool() == null ? Optional.empty() : table.roll(id, draw.pool(), random::nextDouble);
        if (rolled.isEmpty()) {
            player.displayClientMessage(draw.refusal() != null ? draw.refusal()
                    : Component.translatable("taczspray.message.no_finishes"), true);
            return false;
        }
        Finish finish = rolled.get();
        finish.writeTo(gun);
        player.getInventory().setChanged();
        Network.sendTo(player, new RevealMessage(finish, table.reel(id, draw.pool(), finish, random::nextDouble)));
        return true;
    }

    /** The series or the finish a labelled can gives. Client only (tooltips), with the names the server sent. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CanLabel.of(stack).ifPresentOrElse(l -> {
            if (l.series() != null) {
                tooltip.add(Component.translatable("taczspray.tooltip.series", ClientCatalog.seriesName(l.series()))
                        .withStyle(ChatFormatting.AQUA));
            } else if (l.look() != null) {
                tooltip.add(Component.translatable("taczspray.tooltip.look", ClientCatalog.lookName(l.look()))
                        .withStyle(ChatFormatting.AQUA));
            }
        }, () -> tooltip.add(Component.translatable("taczspray.message.bad_can").withStyle(ChatFormatting.RED)));
    }

    @Override
    protected SoundEvent sound() {
        return SoundEvents.FIRE_EXTINGUISH;
    }

    @Override
    protected int mistColour(Level level) {
        return MIST[level.random.nextInt(MIST.length)];
    }
}
