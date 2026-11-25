package io.github.ryukadev.taczspray;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Ten finishes rolled at once for one gun, waiting on the gun's own tag for its owner to pick one. Until then the gun
 * keeps the finish it had and cannot be sprayed or stripped; once one is picked the rest are gone. Kept on the gun, so
 * it outlasts a closed screen, a restart or a move to another slot.
 *
 * @param nonce    tells this ten from any other, so that a pick reaches the gun it was made for
 * @param finishes the ten, in the order they were shown
 */
public record TenPull(long nonce, List<Finish> finishes) {
    public static final int SIZE = 10;
    public static final String TAG = "TaczSprayTenPull";
    private static final int VERSION = 1;

    public TenPull {
        finishes = List.copyOf(finishes);
        if (finishes.size() != SIZE) {
            throw new IllegalArgumentException("a ten-pull has " + SIZE + " finishes");
        }
    }

    /** The gun's ten waiting to be picked from, or null when there are none (or none this version reads). */
    @Nullable
    public static TenPull of(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        return tag == null || !tag.contains(TAG, Tag.TAG_COMPOUND) ? null : fromTag(tag.getCompound(TAG));
    }

    public void writeTo(ItemStack gun) {
        gun.getOrCreateTag().put(TAG, toTag());
    }

    @Nullable
    static TenPull fromTag(CompoundTag t) {
        if (t.getInt("v") != VERSION) {
            return null;
        }
        List<Finish> finishes = new ArrayList<>();
        ListTag list = t.getList("finishes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Finish f = Finish.fromTag(list.getCompound(i));
            if (f == null) {
                return null;
            }
            finishes.add(f);
        }
        return finishes.size() == SIZE ? new TenPull(t.getLong("nonce"), finishes) : null;
    }

    CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("v", VERSION);
        t.putLong("nonce", nonce);
        ListTag list = new ListTag();
        finishes.forEach(f -> list.add(f.toTag()));
        t.put("finishes", list);
        return t;
    }

    public static void remove(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        if (tag != null) {
            tag.remove(TAG);
        }
    }
}
