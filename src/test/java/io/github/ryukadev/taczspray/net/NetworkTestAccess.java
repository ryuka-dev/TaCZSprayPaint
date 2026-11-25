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
        new CatalogMessage(Map.of("metal", Map.of("en_us", "Metal")), custom).encode(buf);
        return CatalogMessage.decode(buf).custom();
    }
}
