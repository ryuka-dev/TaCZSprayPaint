package io.github.ryukadev.taczspray.net;

import io.github.ryukadev.taczspray.TaczSpray;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** The mod's one channel. Server and client must run the same protocol version, or the client cannot join. */
public final class Network {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(TaczSpray.id("main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Network() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, RevealMessage.class, RevealMessage::encode, RevealMessage::decode,
                Network::onReveal, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendTo(ServerPlayer player, RevealMessage message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    // DistExecutor keeps the client-only screen class off a dedicated server
    @SuppressWarnings("removal")
    private static void onReveal(RevealMessage message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.github.ryukadev.taczspray.client.RevealScreen.open(message)));
        context.get().setPacketHandled(true);
    }
}
