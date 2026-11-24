package io.github.ryukadev.taczspray.item;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.FinishTable;
import io.github.ryukadev.taczspray.FinishTables;
import io.github.ryukadev.taczspray.PaintableGuns;
import io.github.ryukadev.taczspray.net.Network;
import io.github.ryukadev.taczspray.net.RevealMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** Sprays the off-hand gun with a finish rolled anew every time: pattern, grade and wear. One can a spray. */
public final class SprayCanItem extends PaintingItem {
    private static final int[] MIST = {0xE04848, 0xF2C14E, 0x58B368, 0x4A8FE0, 0xB060D0, 0xF08CB0};

    public SprayCanItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean canStart(ItemStack gun, Level level) {
        String id = FinishTables.gunId(gun);
        return id != null && (level.isClientSide ? PaintableGuns.clientKnows(id) : FinishTables.current().paints(id));
    }

    @Override
    protected Component refusal(ItemStack gun) {
        return Component.translatable("taczspray.message.no_finishes");
    }

    @Override
    protected boolean apply(ServerPlayer player, ItemStack gun) {
        String id = FinishTables.gunId(gun);
        FinishTable table = FinishTables.current();
        var random = player.getRandom();
        Optional<Finish> rolled = table.roll(id, random::nextDouble);
        if (rolled.isEmpty()) {
            player.displayClientMessage(refusal(gun), true);
            return false;
        }
        Finish finish = rolled.get();
        finish.writeTo(gun);
        player.getInventory().setChanged();
        Network.sendTo(player, new RevealMessage(finish, table.reel(id, finish, random::nextDouble)));
        return true;
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
