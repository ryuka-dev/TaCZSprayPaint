package io.github.ryukadev.taczspray;

import io.github.ryukadev.taczspray.net.NetworkTestAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenPullTest {
    private static List<Finish> ten() {
        List<Finish> finishes = new ArrayList<>();
        for (int i = 0; i < TenPull.SIZE; i++) {
            finishes.add(new Finish("ak47_rust", "rust", 1 + i % 5, 1 + i % 5, i / 10.0, i, i * 7));
        }
        return finishes;
    }

    @Test
    void theTenAndTheirNonceSurviveTheGunsTag() {
        TenPull pull = new TenPull(-1234567890123L, ten());
        assertEquals(pull, TenPull.fromTag(pull.toTag()));
    }

    @Test
    void aTagThatIsNotTenGoodFinishesIsNotAPull() {
        CompoundTag t = new TenPull(1, ten()).toTag();
        ((ListTag) t.get("finishes")).remove(0);
        assertNull(TenPull.fromTag(t), "nine");
        CompoundTag bad = new TenPull(1, ten()).toTag();
        ((ListTag) bad.get("finishes")).getCompound(3).putInt("grade", 9);
        assertNull(TenPull.fromTag(bad), "a finish out of range");
        CompoundTag later = new TenPull(1, ten()).toTag();
        later.putInt("v", 2);
        assertNull(TenPull.fromTag(later), "a later version");
        assertThrows(IllegalArgumentException.class, () -> new TenPull(1, ten().subList(0, 9)));
    }

    @Test
    void aPickNamesOneOfTheTen() {
        assertEquals(List.of(42L, 9), NetworkTestAccess.choose(42, 9));
        assertThrows(IllegalArgumentException.class, () -> NetworkTestAccess.choose(42, 10));
        assertThrows(IllegalArgumentException.class, () -> NetworkTestAccess.choose(42, -1));
    }
}
