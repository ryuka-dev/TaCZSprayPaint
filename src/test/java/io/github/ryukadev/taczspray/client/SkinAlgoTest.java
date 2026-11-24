package io.github.ryukadev.taczspray.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The client's composition against the reference, {@code tools/skins/skinalgo.py}: {@code skingen.py --fixture} writes
 * a made-up gun (a grey base, a painted strip with every kind of texel) and what the reference composed for three guns.
 * The Java must give the same pixels; if it does not, one of the two changed without the other.
 */
class SkinAlgoTest {
    private static InputStream resource(String name) {
        InputStream in = SkinAlgoTest.class.getResourceAsStream("/finish/" + name);
        assertNotNull(in, "missing test resource skin/" + name + " (python tools/skins/skingen.py --fixture)");
        return in;
    }

    private static BufferedImage image(String name) throws IOException {
        try (InputStream in = resource(name)) {
            return ImageIO.read(in);
        }
    }

    private static int[] argb(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    private static double num(JsonObject o, String name) {
        return o.has(name) ? o.get(name).getAsDouble() : 0;
    }

    private static boolean close(int a, int b, int slack) {
        for (int shift = 0; shift < 32; shift += 8) {
            if (Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)) > slack) {
                return false;
            }
        }
        return true;
    }

    @Test
    void theHashesMatchTheReference() {
        // values printed by skinalgo.py: str_hash("fixture"), h32(1, 2, 3), h32(-5)
        assertEquals(0x49451132, SkinAlgo.strHash("fixture"));
        assertEquals("1859609174", Integer.toUnsignedString(SkinAlgo.h32(1, 2, 3)));
        assertEquals("3637604506", Integer.toUnsignedString(SkinAlgo.h32(-5)));
    }

    @Test
    void composesTheSamePixelsAsTheReference() throws IOException {
        JsonObject meta;
        try (InputStream in = resource("fixture.json")) {
            meta = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        BufferedImage baseImage = image("base.png");
        int width = baseImage.getWidth();
        int[] base = argb(baseImage);
        BufferedImage tileImage = image("pattern.png");
        int[] tile = argb(tileImage);
        SkinTexels texels;
        try (InputStream in = resource("texels.bin")) {
            texels = SkinTexels.read(in);
        }
        int skin = SkinAlgo.strHash(meta.get("skin").getAsString());
        double pixel = meta.get("pixel").getAsDouble();
        for (JsonElement e : meta.getAsJsonArray("cases")) {
            JsonObject c = e.getAsJsonObject();
            List<SkinAlgo.Effect> anim = new ArrayList<>();
            if (c.has("anim")) {
                for (JsonElement a : c.getAsJsonArray("anim")) {
                    JsonObject o = a.getAsJsonObject();
                    anim.add(new SkinAlgo.Effect(o.get("type").getAsString(), num(o, "speed"), num(o, "period"),
                            num(o, "amount"), num(o, "degrees"), num(o, "width"), num(o, "strength")));
                }
            }
            int[] got = SkinAlgo.compose(base, width, texels, tile, tileImage.getWidth(), pixel, skin,
                    c.get("f").getAsDouble(), c.get("pattern").getAsInt(), c.get("wear").getAsInt(), anim,
                    c.has("t") ? c.get("t").getAsDouble() : 0,
                    c.has("projection") && "side".equals(c.get("projection").getAsString()));
            int[] want = argb(image(c.get("image").getAsString()));
            // a moving skin's sine is StrictMath here and the C library's in Python: a channel may land one off
            int slack = anim.isEmpty() ? 0 : 1;
            int differ = 0;
            for (int i = 0; i < want.length; i++) {
                if (!close(got[i], want[i], slack)) {
                    differ++;
                }
            }
            assertEquals(0, differ, "pixels that differ from the reference in " + c);
        }
    }

    /** The sweep: a bright band that runs along the gun (model z) over its period, brightest at its middle. */
    @Test
    void theSweepRunsAlongTheGun() {
        List<SkinAlgo.Effect> sweep = List.of(new SkinAlgo.Effect("sweep", 0, 4.0, 0, 0, 5, 0.5));
        // over z 0..40 the band's middle runs from -5 to 45 in 4 seconds: at 7.5 after 1 s, at 20 after 2 s
        double[] early = {100, 100, 100};
        new SkinAlgo.Frame(sweep, 1.0, 0, 40).apply(early, 20);
        assertEquals(100, early[0], 1e-9, "the band has not reached z 20 yet");
        double[] there = {100, 100, 100};
        new SkinAlgo.Frame(sweep, 2.0, 0, 40).apply(there, 20);
        assertEquals(100 + 155 * 0.5, there[0], 1e-9, "the middle of the band lightens by its strength");
        double[] edge = {100, 100, 100};
        new SkinAlgo.Frame(sweep, 2.0, 0, 40).apply(edge, 22.5);
        assertEquals(100 + 155 * 0.25, edge[0], 1e-9, "half as much halfway to its edge");
    }
}
