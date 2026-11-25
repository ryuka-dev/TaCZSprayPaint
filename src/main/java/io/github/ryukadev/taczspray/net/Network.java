package io.github.ryukadev.taczspray.net;

import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.item.TenPullChoices;
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
    // 2: the server owner's own finishes (CustomLookMessage, CatalogMessage's list of them)
    // 3: the gun in RevealMessage, wear bands in CatalogMessage, ten-pulls (ChoiceMessage, ChooseMessage)
    private static final String PROTOCOL = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(TaczSpray.id("main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Network() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, RevealMessage.class, RevealMessage::encode, RevealMessage::decode,
                Network::onReveal, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, CatalogMessage.class, CatalogMessage::encode, CatalogMessage::decode,
                Network::onCatalog, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, CustomLookMessage.class, CustomLookMessage::encode, CustomLookMessage::decode,
                Network::onCustomLook, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, ChoiceMessage.class, ChoiceMessage::encode, ChoiceMessage::decode,
                Network::onChoice, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, ChooseMessage.class, ChooseMessage::encode, ChooseMessage::decode,
                Network::onChoose, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendTo(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToAll(Object message) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), message);
    }

    // DistExecutor keeps the client-only screen class off a dedicated server
    @SuppressWarnings("removal")
    private static void onReveal(RevealMessage message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.github.ryukadev.taczspray.client.RevealScreen.open(message)));
        context.get().setPacketHandled(true);
    }

    @SuppressWarnings("removal")
    private static void onChoice(ChoiceMessage message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.github.ryukadev.taczspray.client.PickScreen.open(message)));
        context.get().setPacketHandled(true);
    }

    private static void onChoose(ChooseMessage message, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            context.get().enqueueWork(() -> TenPullChoices.choose(player, message.nonce(), message.index()));
        }
        context.get().setPacketHandled(true);
    }

    @SuppressWarnings("removal")
    private static void onCatalog(CatalogMessage message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.github.ryukadev.taczspray.client.ClientCatalog.accept(message)));
        context.get().setPacketHandled(true);
    }

    @SuppressWarnings("removal")
    private static void onCustomLook(CustomLookMessage message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.github.ryukadev.taczspray.client.ClientCatalog.accept(message.look())));
        context.get().setPacketHandled(true);
    }
}
