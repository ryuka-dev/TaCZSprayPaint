package io.github.ryukadev.taczspray.item;

import io.github.ryukadev.taczspray.Finish;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Takes the finish off the off-hand gun, leaving it exactly as it was before it was sprayed. One a use. */
public final class PaintRemoverItem extends PaintingItem {
    public PaintRemoverItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean canStart(ItemStack gun, Level level) {
        return Finish.of(gun) != null;
    }

    @Override
    protected Component refusal(ItemStack gun) {
        return Component.translatable("taczspray.message.no_paint");
    }

    @Override
    protected boolean apply(ServerPlayer player, ItemStack gun) {
        Finish.remove(gun);
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable("taczspray.message.removed"), true);
        return true;
    }

    @Override
    protected SoundEvent sound() {
        return SoundEvents.BUCKET_EMPTY;
    }

    @Override
    protected int mistColour(Level level) {
        return 0xD8E4E8;
    }
}
