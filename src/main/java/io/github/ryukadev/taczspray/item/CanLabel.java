package io.github.ryukadev.taczspray.item;

import io.github.ryukadev.taczspray.Series;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * What a spray can gives, from its tag {@code TaczSprayCan}: {@code {series: "<id>"}} for a server owner's series,
 * {@code {look: "<pattern>"}} for one finish; no tag for a plain can (every finish, the table's odds). Anything else is
 * a damaged label, which sprays nothing. Server owners hand labelled cans out with {@code /taczspray give} or
 * {@code /give}.
 */
public record CanLabel(@Nullable String series, @Nullable String look) {
    public static final String TAG = "TaczSprayCan";
    public static final CanLabel PLAIN = new CanLabel(null, null);

    public CanLabel {
        if (series != null && look != null) {
            throw new IllegalArgumentException("a can is of a series or of one finish, not both");
        }
        if (series != null && !Series.ID.matcher(series).matches() || look != null && !Series.ID.matcher(look).matches()) {
            throw new IllegalArgumentException("lower-case letters, digits and _ only");
        }
    }

    public static CanLabel ofSeries(String id) {
        return new CanLabel(id, null);
    }

    public static CanLabel ofLook(String look) {
        return new CanLabel(null, look);
    }

    /** The can's label; empty when the tag is there but not one this mod reads. */
    public static Optional<CanLabel> of(ItemStack can) {
        CompoundTag tag = can.getTag();
        if (tag == null || !tag.contains(TAG)) {
            return Optional.of(PLAIN);
        }
        if (!tag.contains(TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag t = tag.getCompound(TAG);
        try {
            return Optional.of(new CanLabel(t.contains("series", Tag.TAG_STRING) ? t.getString("series") : null,
                    t.contains("look", Tag.TAG_STRING) ? t.getString("look") : null))
                    .filter(l -> l.series() != null || l.look() != null);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public void writeTo(ItemStack can) {
        if (series == null && look == null) {
            CompoundTag tag = can.getTag();
            if (tag != null) {
                tag.remove(TAG);
            }
            return;
        }
        CompoundTag t = new CompoundTag();
        if (series != null) {
            t.putString("series", series);
        }
        if (look != null) {
            t.putString("look", look);
        }
        can.getOrCreateTag().put(TAG, t);
    }
}
