package io.github.ryukadev.taczspray.net;

import io.github.ryukadev.taczspray.TenPull;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client to server: which of a ten-pull's finishes to keep. The server finds the gun by the nonce among what the
 * player carries and checks the index; anything else is ignored.
 */
public record ChooseMessage(long nonce, int index) {
    void encode(FriendlyByteBuf buf) {
        buf.writeLong(nonce);
        buf.writeVarInt(index);
    }

    static ChooseMessage decode(FriendlyByteBuf buf) {
        long nonce = buf.readLong();
        int index = buf.readVarInt();
        if (index < 0 || index >= TenPull.SIZE) {
            throw new IllegalArgumentException("bad pick " + index);
        }
        return new ChooseMessage(nonce, index);
    }
}
