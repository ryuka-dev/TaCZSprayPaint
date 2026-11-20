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
 * sound and coloured mist at the gun; letting go early does nothing. The item is never used up. Whether the gun can
 * be worked on is the server's call when the time is up; the client only decides whether to start the motion.
 */
public abstract class PaintingItem extends Item {
    public static final int USE_TICKS = 30;

    protected PaintingItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Client and server: whether to start on this gun at all. */
    protected abstract boolean canStart(ItemStack gun, Level level);

    /** Server only, when the time is up: change the gun, tell the player. */
    protected abstract void apply(ServerPlayer player, ItemStack gun);

    /** The message when {@link #canStart} says no. */
    protected abstract Component refusal(ItemStack gun);

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
        if (!canStart(gun, level)) {
            if (!level.isClientSide) {
                player.displayClientMessage(refusal(gun), true);
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
            if (FinishTables.gunId(gun) == null) {
                player.displayClientMessage(Component.translatable("taczspray.message.gun_in_off_hand"), true);
            } else if (!canStart(gun, level)) {
                player.displayClientMessage(refusal(gun), true);
            } else {
                apply(player, gun);
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
