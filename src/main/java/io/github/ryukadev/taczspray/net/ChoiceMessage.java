package io.github.ryukadev.taczspray.net;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.TenPull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: a gun's ten-pull to pick from, opening the screen for it.
 *
 * @param gun   the gun as it is (to show each of the ten on it)
 * @param pull  the ten and the nonce a pick must name
 * @param fresh just rolled (the ten turn over one by one), or reopened later (shown at once)
 */
public record ChoiceMessage(ItemStack gun, TenPull pull, boolean fresh) {
    public ChoiceMessage {
        gun = gun.copy();
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeItem(gun);
        buf.writeLong(pull.nonce());
        pull.finishes().forEach(f -> RevealMessage.writeFinish(buf, f));
        buf.writeBoolean(fresh);
    }

    static ChoiceMessage decode(FriendlyByteBuf buf) {
        ItemStack gun = buf.readItem();
        long nonce = buf.readLong();
        List<Finish> finishes = new ArrayList<>(TenPull.SIZE);
        for (int i = 0; i < TenPull.SIZE; i++) {
            finishes.add(RevealMessage.readFinish(buf));
        }
        return new ChoiceMessage(gun, new TenPull(nonce, finishes), buf.readBoolean());
    }
}
