package io.github.ryukadev.taczspray.item;

import io.github.ryukadev.taczspray.FinishTables;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An item held in the main hand and worked on the TaCZ gun in the off hand: hold use for {@link #USE_TICKS}, with
 * sound and coloured mist at the gun; letting go early does nothing. One is used up each time the work is done (not in
 * creative mode); a gun can be worked on again and again, one item each time. Whether the gun can be worked on is the
 * server's call when the time is up; the client only decides whether to start the motion.
 */
public abstract class PaintingItem extends Item {
    public static final int USE_TICKS = 30;
    public static final int STACK = 16;

    protected PaintingItem(Properties properties) {
        super(properties.stacksTo(STACK));
    }

    /**
     * Client and server: why this item cannot work on this TaCZ gun, or null when it can. The client only decides
     * whether to start the motion and may know less; the server's answer stands.
     */
    @Nullable
    protected abstract Component refusal(Player player, ItemStack self, ItemStack gun);

    /**
     * Server only, when the time is up and {@link #refusal} said yes: change the gun, tell the player. False when
     * nothing changed.
     */
    protected abstract boolean apply(ServerPlayer player, ItemStack self, ItemStack gun);

    protected abstract SoundEvent sound();

    /** The mist's colour this tick, 0xRRGGBB. */
    protected abstract int mistColour(Level level);

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack self = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(self);
        }
        ItemStack gun = player.getOffhandItem();
        if (FinishTables.gunId(gun) == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("taczspray.message.gun_in_off_hand"), true);
            }
            return InteractionResultHolder.fail(self);
        }
        Component no = refusal(player, self, gun);
        if (no != null) {
            if (!level.isClientSide) {
                player.displayClientMessage(no, true);
            }
            return InteractionResultHolder.fail(self);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(self);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        int used = getUseDuration(stack) - remaining;
        if (used % 6 == 0) {
            level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), sound(), SoundSource.PLAYERS,
                    0.35f, 1.4f + level.random.nextFloat() * 0.3f, false);
        }
        // a puff of mist a little in front of the player, low and to the off-hand side, where the gun is held
        Vec3 look = player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? -0.35 : 0.35);
        Vec3 at = player.getEyePosition().add(look.scale(0.7)).add(side).add(0, -0.35, 0);
        int rgb = mistColour(level);
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f), 0.8f);
        for (int i = 0; i < 2; i++) {
            level.addParticle(dust, at.x + (level.random.nextDouble() - 0.5) * 0.25,
                    at.y + (level.random.nextDouble() - 0.5) * 0.15, at.z + (level.random.nextDouble() - 0.5) * 0.25,
                    0, 0, 0);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            ItemStack gun = player.getOffhandItem();
            // checked again: the gun may have been swapped while spraying
            Component no = FinishTables.gunId(gun) == null
                    ? Component.translatable("taczspray.message.gun_in_off_hand") : refusal(player, stack, gun);
            if (no != null) {
                player.displayClientMessage(no, true);
            } else if (apply(player, stack, gun) && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }
}
