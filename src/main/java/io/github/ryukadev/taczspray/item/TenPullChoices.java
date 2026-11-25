package io.github.ryukadev.taczspray.item;

import io.github.ryukadev.taczspray.TenPull;
import io.github.ryukadev.taczspray.net.ChoiceMessage;
import io.github.ryukadev.taczspray.net.Network;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The server's side of picking from a ten-pull ({@link TenPull}). */
public final class TenPullChoices {
    private TenPullChoices() {
    }

    /**
     * The player picked one: the gun is the one carrying this nonce, wherever it now is in the player's inventory; it
     * gets that finish and the ten are gone. A nonce no carried gun has (the gun was dropped, or already picked) is
     * told to the player and changes nothing.
     */
    public static void choose(ServerPlayer player, long nonce, int index) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            TenPull pull = TenPull.of(stack);
            if (pull != null && pull.nonce() == nonce) {
                pull.finishes().get(index).writeTo(stack);
                TenPull.remove(stack);
                inventory.setChanged();
                return;
            }
        }
        player.displayClientMessage(Component.translatable("taczspray.message.choice_gone"), true);
    }

    /** {@code /taczspray choose}: the ten of the gun in the main hand, else the off hand, shown again. */
    public static int reopen(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            TenPull pull = TenPull.of(stack);
            if (pull != null) {
                Network.sendTo(player, new ChoiceMessage(stack, pull, false));
                return 1;
            }
        }
        player.displayClientMessage(Component.translatable("taczspray.command.no_pending"), false);
        return 0;
    }
}
