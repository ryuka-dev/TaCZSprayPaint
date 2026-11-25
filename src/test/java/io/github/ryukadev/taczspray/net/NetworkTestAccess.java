package io.github.ryukadev.taczspray.net;

import io.github.ryukadev.taczspray.CustomLook;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Map;
import java.util.Set;

/** The messages' encode and decode, for tests in other packages. */
public final class NetworkTestAccess {
    private NetworkTestAccess() {
    }

    public static CustomLook roundTrip(CustomLook look) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new CustomLookMessage(look).encode(buf);
        CustomLook back = CustomLookMessage.decode(buf).look();
        if (buf.readableBytes() != 0) {
            throw new AssertionError(buf.readableBytes() + " bytes left over");
        }
        return back;
    }

    public static Set<String> roundTripCatalog(Set<String> custom) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new CatalogMessage(Map.of("metal", Map.of("en_us", "Metal")), custom,
                io.github.ryukadev.taczspray.Finish.defaultBandUppers()).encode(buf);
        return CatalogMessage.decode(buf).custom();
    }

    /** A pick through the wire: [nonce, index] as the server reads them. */
    public static java.util.List<Object> choose(long nonce, int index) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        new ChooseMessage(nonce, index).encode(buf);
        ChooseMessage read = ChooseMessage.decode(buf);
        return java.util.List.of(read.nonce(), read.index());
    }
}
