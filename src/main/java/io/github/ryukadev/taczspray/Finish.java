package io.github.ryukadev.taczspray;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * The finish painted on one gun, stored on the gun's own tag so that it travels with the gun. Everything is rolled
 * once, on the server, when the gun is sprayed; clients only draw and name it.
 *
 * @param id        which finish ({@code finishes/<id>/} in the resources), {@code <gun>_<look>}
 * @param look      the pattern, which names the finish ({@code taczspray.pattern.<look>})
 * @param grade     1 (standard, commonest) to 5 (unique, rarest)
 * @param band      the wear band, 1 (factory new) to 5 (battle-scarred), as the server's odds placed {@code wear}
 * @param wear      0 to 1
 * @param patternNo where the pattern sits on this gun
 * @param wearNo    where the scratches and dirt fall on this gun
 */
public record Finish(String id, String look, int grade, int band, double wear, int patternNo, int wearNo) {
    public static final String TAG = "TaczSprayFinish";
    public static final int GRADES = 5;
    public static final int BANDS = 5;
    /** Where each wear band ends by default (Counter-Strike's), factory new first. */
    private static final double[] BAND_UPPERS = {0.07, 0.15, 0.38, 0.45, 1.0};
    static final int VERSION = 1;
    static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");

    public Finish {
        if (!NAME.matcher(id).matches() || !NAME.matcher(look).matches()) {
            throw new IllegalArgumentException("bad finish " + id + " / " + look);
        }
        if (!(wear >= 0 && wear <= 1) || grade < 1 || grade > GRADES || band < 1 || band > BANDS) {
            throw new IllegalArgumentException("finish " + id + " out of range");
        }
    }

    public static double[] defaultBandUppers() {
        return BAND_UPPERS.clone();
    }

    /** Five band ends rising to 1, as a finish table has. */
    public static boolean validBandUppers(double[] uppers) {
        if (uppers.length != BANDS || uppers[BANDS - 1] != 1.0) {
            return false;
        }
        double last = 0;
        for (double u : uppers) {
            if (!(u > last && u <= 1)) {
                return false;
            }
            last = u;
        }
        return true;
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
        try {
            return new Finish(t.getString("id"), t.getString("look"), t.getInt("grade"), t.getInt("band"),
                    t.getDouble("wear"), t.getInt("pattern_no"), t.getInt("wear_no"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void writeTo(ItemStack gun) {
        CompoundTag t = new CompoundTag();
        t.putInt("v", VERSION);
        t.putString("id", id);
        t.putString("look", look);
        t.putInt("grade", grade);
        t.putInt("band", band);
        t.putDouble("wear", wear);
        t.putInt("pattern_no", patternNo);
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
