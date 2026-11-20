package io.github.ryukadev.taczspray;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * The finish painted on one gun, stored on the gun's own tag so that it travels with the gun. Every number is rolled
 * once, on the server, when the gun is sprayed; clients only draw it.
 *
 * @param id      which finish ({@code finishes/<id>/} in the resources)
 * @param wear    0 (factory new) to 1 (battle-scarred)
 * @param pattern where the pattern sits on this gun
 * @param wearNo  where the scratches and dirt fall on this gun
 */
public record Finish(String id, double wear, int pattern, int wearNo) {
    public static final String TAG = "TaczSprayFinish";
    static final int VERSION = 1;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");

    public Finish {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("bad finish id " + id);
        }
        if (!(wear >= 0 && wear <= 1)) {
            throw new IllegalArgumentException("wear out of range: " + wear);
        }
    }

    /** The gun's finish, or null when it has none or the tag is not one this version reads. */
    @Nullable
    public static Finish of(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag t = tag.getCompound(TAG);
        if (t.getInt("v") != VERSION) {
            return null;
        }
        String id = t.getString("id");
        double wear = t.getDouble("wear");
        if (!ID.matcher(id).matches() || !(wear >= 0 && wear <= 1)) {
            return null;
        }
        return new Finish(id, wear, t.getInt("pattern"), t.getInt("wear_no"));
    }

    public void writeTo(ItemStack gun) {
        CompoundTag t = new CompoundTag();
        t.putInt("v", VERSION);
        t.putString("id", id);
        t.putDouble("wear", wear);
        t.putInt("pattern", pattern);
        t.putInt("wear_no", wearNo);
        gun.getOrCreateTag().put(TAG, t);
    }

    /** Takes the finish off; the gun is exactly as it was before it was sprayed. */
    public static void remove(ItemStack gun) {
        CompoundTag tag = gun.getTag();
        if (tag != null) {
            tag.remove(TAG);
        }
    }
}
