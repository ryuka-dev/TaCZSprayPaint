package io.github.ryukadev.taczspray;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ryukadev.taczspray.net.NetworkTestAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomFinishTest {
    private static final String TABLE = """
            {"v": 1, "grades": [79.92, 15.98, 3.20, 0.64, 0.26],
             "bands": [{"upper": 0.07, "weight": 3}, {"upper": 0.15, "weight": 24}, {"upper": 0.38, "weight": 33},
                       {"upper": 0.45, "weight": 24}, {"upper": 1.0, "weight": 16}],
             "finishes": [
               {"id": "ak47_rust", "gun": "tacz:ak47", "look": "rust", "grade": 1, "wear_min": 0, "wear_max": 1},
               {"id": "m4a1_rust", "gun": "tacz:m4a1", "look": "rust", "grade": 1, "wear_min": 0, "wear_max": 1}
             ]}
            """;

    private static FinishTable table() {
        return FinishTable.parse(JsonParser.parseString(TABLE).getAsJsonObject());
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static int[] tile(int size) {
        int[] rgb = new int[size * size];
        for (int i = 0; i < rgb.length; i++) {
            rgb[i] = i * 0x010203 & 0xFFFFFF;
        }
        return rgb;
    }

    private static CustomFinish finish(String folder, String json) {
        return CustomFinish.parse(folder, json(json), 16, tile(16));
    }

    @Test
    void aFinishNeedsOnlyItsGradeTheRestHasDefaults() {
        CustomFinish f = finish("stripes", "{\"v\": 1, \"grade\": 2}");
        assertEquals("custom_stripes", f.look().look());
        assertEquals(2, f.grade());
        assertFalse(f.look().full());
        assertFalse(f.look().side());
        assertEquals(CustomLook.DEFAULT_PIXEL, f.look().pixel());
        assertEquals(0, f.wearMin());
        assertEquals(1, f.wearMax());
        assertNull(f.guns());
        assertTrue(f.plainCans());
        assertNull(f.look().material());
        assertEquals("stripes", f.look().name("en_us"), "no name: the folder stands in");
    }

    @Test
    void everyFieldIsRead() {
        CustomFinish f = finish("copper", """
                {"v": 1, "name": {"en_us": "Copper", "zh_cn": "紫铜"}, "grade": 3, "cover": "full",
                 "wear": [0.1, 0.5], "guns": ["tacz:ak47"], "projection": "side", "pixel": 0.5,
                 "anim": [{"type": "sweep", "period": 3, "width": 4, "strength": 0.5}],
                 "material": {"smoothness": 0.8, "metal": true}, "plain_cans": false}
                """);
        assertEquals("紫铜", f.look().name("zh_cn"));
        assertEquals("Copper", f.look().name("de_de"));
        assertTrue(f.look().full());
        assertTrue(f.look().side());
        assertEquals(0.5, f.look().pixel());
        assertEquals(0.1, f.wearMin());
        assertEquals(Set.of("tacz:ak47"), f.guns());
        assertEquals(List.of(new Anim("sweep", 0, 3, 0, 0, 4, 0.5)), f.look().anim());
        assertEquals(new CustomLook.Material(0.8, true), f.look().material());
        assertFalse(f.plainCans());
    }

    @Test
    void malformedFinishesAreRefused() {
        assertThrows(RuntimeException.class, () -> finish("x", "{\"grade\": 1}"), "no v");
        assertThrows(RuntimeException.class, () -> finish("x", "{\"v\": 1}"), "no grade");
        assertThrows(RuntimeException.class, () -> finish("x", "{\"v\": 1, \"grade\": 6}"));
        assertThrows(RuntimeException.class, () -> finish("Big", "{\"v\": 1, \"grade\": 1}"), "folder name");
        assertThrows(RuntimeException.class, () -> finish("x", "{\"v\": 1, \"grade\": 1, \"cover\": \"barrel\"}"));
        assertThrows(RuntimeException.class, () -> finish("x", "{\"v\": 1, \"grade\": 1, \"pixel\": 9}"));
        assertThrows(RuntimeException.class, () -> finish("x", "{\"v\": 1, \"grade\": 1, \"wear\": [0.6, 0.2]}"));
        assertThrows(RuntimeException.class, () -> finish("x", "{\"v\": 1, \"grade\": 1, \"guns\": [\"ak47\"]}"));
        assertThrows(RuntimeException.class, () -> finish("x",
                "{\"v\": 1, \"grade\": 1, \"anim\": [{\"type\": \"spin\"}]}"));
        assertThrows(RuntimeException.class, () -> finish("x",
                "{\"v\": 1, \"grade\": 1, \"anim\": [{\"type\": \"sweep\", \"period\": 3}]}"), "a sweep needs a width");
        assertThrows(RuntimeException.class, () -> CustomFinish.parse("x", json("{\"v\": 1, \"grade\": 1}"), 4,
                tile(4)), "too small");
        assertThrows(RuntimeException.class, () -> CustomFinish.parse("x", json("{\"v\": 1, \"grade\": 1}"), 16,
                tile(8)), "not square");
    }

    @Test
    void aFinishGoesOnItsGunsOrOnEveryGunThatHasFinishes() {
        CustomFinish all = finish("stripes", "{\"v\": 1, \"grade\": 2}");
        CustomFinish some = finish("copper", "{\"v\": 1, \"grade\": 3, \"guns\": [\"tacz:m4a1\", \"tacz:glock_17\"]}");
        Map<String, Set<String>> skipped = new TreeMap<>();
        FinishTable t = table().withCustom(List.of(all, some), skipped);
        assertEquals(Set.of("ak47_rust", "m4a1_rust", "ak47_custom_stripes", "m4a1_custom_stripes",
                "m4a1_custom_copper"), Set.copyOf(t.entries().stream().map(FinishTable.Entry::id).toList()));
        assertEquals(Map.of("custom_copper", Set.of("tacz:glock_17")), skipped, "a gun without finishes of ours");
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            Finish f = t.roll("tacz:m4a1", FinishTable.Pool.of("custom_copper"), random::nextDouble).orElseThrow();
            assertEquals("m4a1_custom_copper", f.id());
            assertEquals(3, f.grade());
        }
    }

    @Test
    void plainCansLeaveOutFinishesKeptFromThemAndCanDrawFromASeries() {
        CustomFinish kept = finish("copper", "{\"v\": 1, \"grade\": 1, \"plain_cans\": false}");
        FinishTable t = table().withCustom(List.of(kept), new HashMap<>());
        FinishTables.Plain plain = FinishTables.plainOf(null, Map.of(), List.of(kept), t);
        Random random = new Random(4);
        for (int i = 0; i < 500; i++) {
            assertEquals("ak47_rust", t.roll("tacz:ak47", plain.pool(), random::nextDouble).orElseThrow().id());
        }
        Series metal = new Series("metal", Map.of(), Set.of("custom_copper"), null);
        FinishTables.Plain fromSeries = FinishTables.plainOf("metal", Map.of("metal", metal), List.of(kept), t);
        assertEquals("ak47_custom_copper",
                t.roll("tacz:ak47", fromSeries.pool(), random::nextDouble).orElseThrow().id());
        FinishTables.Plain missing = FinishTables.plainOf("gone", Map.of(), List.of(kept), t);
        assertNull(missing.pool());
        assertEquals("gone", missing.missingSeries());
    }

    @Test
    void theOwnersOddsReplaceTheTablesAndAZeroNeverComesOut() {
        FinishTable t = table().withOdds(null, new double[]{1, 1, 0, 0, 0});
        Random random = new Random(5);
        for (int i = 0; i < 20_000; i++) {
            Finish f = t.roll("tacz:ak47", random::nextDouble).orElseThrow();
            assertTrue(f.wear() < 0.15, "only the two least worn bands: " + f.wear());
        }
        assertThrows(IllegalArgumentException.class, () -> table().withOdds(new double[]{0, 0, 0, 0, 0}, null));
        assertThrows(IllegalArgumentException.class, () -> table().withOdds(null, new double[]{1, -1, 0, 0, 0}));
    }

    @Test
    void aCanForOneFinishGivesItWhateverTheGradeOdds() {
        CustomFinish copper = finish("copper", "{\"v\": 1, \"grade\": 3}");
        FinishTable t = table().withOdds(new double[]{80, 20, 0, 0, 0}, null)
                .withCustom(List.of(copper), new HashMap<>());
        Random random = new Random(6);
        assertEquals("ak47_custom_copper",
                t.roll("tacz:ak47", FinishTable.Pool.of("custom_copper"), random::nextDouble).orElseThrow().id());
        for (int i = 0; i < 2000; i++) {
            assertEquals(1, t.roll("tacz:ak47", random::nextDouble).orElseThrow().grade(), "plain: grade 3 weighs 0");
        }
    }

    @Test
    void aFinishsOwnWearRangeTakesTheBandOddsInsideIt() {
        CustomFinish low = finish("low", "{\"v\": 1, \"grade\": 1, \"wear\": [0, 0.5]}");
        CustomFinish high = finish("high", "{\"v\": 1, \"grade\": 1, \"wear\": [0.5, 1]}");
        FinishTable t = table().withOdds(null, new double[]{1, 1, 0, 0, 0})
                .withCustom(List.of(low, high), new HashMap<>());
        Random random = new Random(7);
        for (int i = 0; i < 5000; i++) {
            double w = t.roll("tacz:ak47", FinishTable.Pool.of("custom_low"), random::nextDouble).orElseThrow().wear();
            assertTrue(w < 0.15, "inside the range, only the bands that weigh something: " + w);
            double h = t.roll("tacz:ak47", FinishTable.Pool.of("custom_high"), random::nextDouble).orElseThrow().wear();
            assertTrue(h >= 0.5 && h <= 1, "the range wins when it lies only in bands weighed 0: " + h);
        }
        // over the whole range the default odds stay as they were: about 3% factory new
        FinishTable d = table();
        int fresh = 0;
        for (int i = 0; i < 100_000; i++) {
            fresh += d.roll("tacz:ak47", random::nextDouble).orElseThrow().band() == 1 ? 1 : 0;
        }
        assertEquals(0.03, fresh / 100_000.0, 0.003);
    }

    @Test
    void aPatternOfTheWrongSizeIsLeftOutBeforeItIsDecoded(@TempDir Path dir) throws Exception {
        for (int size : new int[]{4, 16, 200}) {
            Path f = Files.createDirectories(dir.resolve("p" + size));
            Files.writeString(f.resolve("finish.json"), "{\"v\": 1, \"grade\": 1}");
            ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB), "png",
                    f.resolve("pattern.png").toFile());
        }
        assertEquals(List.of("custom_p16"), CustomFinish.readAll(dir).stream().map(c -> c.look().look()).toList());
    }

    @Test
    void aDataPackTableCannotUseTheOwnersNames() {
        String bad = TABLE.replaceFirst("\"look\": \"rust\"", "\"look\": \"custom_x\"");
        assertTrue(bad.contains("custom_x"));
        assertThrows(IllegalArgumentException.class,
                () -> FinishTable.parse(JsonParser.parseString(bad).getAsJsonObject()));
    }

    @Test
    void aFinishIsSentAndReadBackAsItWas() {
        CustomFinish f = finish("copper", """
                {"v": 1, "name": {"en_us": "Copper"}, "grade": 3, "cover": "full", "projection": "side",
                 "anim": [{"type": "hue", "period": 4, "degrees": 30}], "material": {"smoothness": 0.8, "metal": true}}
                """);
        CustomLook back = NetworkTestAccess.roundTrip(f.look());
        assertEquals(f.look().look(), back.look());
        assertEquals(f.look().names(), back.names());
        assertEquals(f.look().anim(), back.anim());
        assertEquals(f.look().material(), back.material());
        assertTrue(back.full() && back.side());
        assertArrayEquals(f.look().rgb(), back.rgb());
        assertEquals(Set.of("custom_copper"), NetworkTestAccess.roundTripCatalog(Set.of("custom_copper")));
    }

    @Test
    void theExamplesThatShipRead() throws Exception {
        Path dir = Path.of("src/main/resources/taczspray-examples/finishes");
        List<CustomFinish> read = CustomFinish.readAll(dir);
        assertEquals(List.of("custom_example_copper", "custom_example_stripes"),
                read.stream().map(c -> c.look().look()).toList());
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/taczspray-examples/series/metal.json")));
    }
}
