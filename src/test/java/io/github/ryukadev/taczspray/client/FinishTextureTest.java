package io.github.ryukadev.taczspray.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinishTextureTest {
    private static final int OWN_A = 0xFF11E622;
    private static final int OWN_B = 0xFF22E633;

    @Test
    void theGunPacksMapIsDrawnUpAndThePaintTakesItsPlaceWhereItShows() {
        // a 2x1 map drawn up twice: 4x2; the paint shows at (1, 0) and (2, 1)
        int[] own = {OWN_A, OWN_B};
        BitSet paint = new BitSet();
        paint.set(1);
        paint.set(4 + 2);
        int[] out = FinishTexture.specular(own, 2, 1, 4, 2, paint, FinishTexture.Material.PAINT);
        int matte = 0xFF000000 | 89 << 16 | FinishTexture.Material.DIELECTRIC_F0 << 8;
        assertEquals(OWN_A, out[0]);
        assertEquals(matte, out[1], "paint: smoothness 0.35, a dielectric, no emission");
        assertEquals(OWN_B, out[2]);
        assertEquals(OWN_B, out[3]);
        assertEquals(OWN_A, out[4]);
        assertEquals(OWN_A, out[5]);
        assertEquals(matte, out[6]);
        assertEquals(OWN_B, out[7]);
    }

    @Test
    void aMetalFinishReflectsInItsOwnColour() {
        int[] out = FinishTexture.specular(new int[]{OWN_A}, 1, 1, 1, 1, bit(0), new FinishTexture.Material(1, true));
        assertEquals(0xFFFFFF00, out[0]);
    }

    @Test
    void theMapsAreFoundBesideTheTexture() {
        ResourceLocation gun = ResourceLocation.tryBuild("tacz", "textures/gun/uv/ak47.png");
        assertEquals("tacz:textures/gun/uv/ak47_s.png", FinishTexture.suffixed(gun, "_s").toString());
        assertEquals("tacz:textures/gun/uv/ak47_n.png", FinishTexture.suffixed(gun, "_n").toString());
    }

    private static BitSet bit(int at) {
        BitSet b = new BitSet();
        b.set(at);
        return b;
    }
}
