package io.github.ryukadev.taczspray;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesTest {
    private static Series parse(String id, String json) {
        return Series.parse(id, JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void aSeriesHasNamesPatternsAndMaybeWeights() {
        Series s = parse("metal", """
                {"v": 1, "name": {"zh_cn": "金属系列", "en_us": "Metal Series"},
                 "looks": ["chrome", "gold"], "grades": [0, 0, 1, 1, 0]}""");
        assertEquals(Set.of("chrome", "gold"), s.looks());
        assertArrayEquals(new double[]{0, 0, 1, 1, 0}, s.grades());
        assertEquals("金属系列", s.name("zh_cn"));
        assertEquals("Metal Series", s.name("ja_jp"), "English when the language has none");
        Series plain = parse("summer", """
                {"v": 1, "name": "Summer", "looks": ["sakura"]}""");
        assertEquals("Summer", plain.name("zh_cn"));
        assertNull(plain.grades());
        assertEquals("nameless", parse("nameless", "{\"v\": 1, \"looks\": [\"rust\"]}").name("en_us"));
    }

    @Test
    void malformedSeriesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> parse("Bad-Id", "{\"v\": 1, \"looks\": [\"rust\"]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("x", "{\"v\": 2, \"looks\": [\"rust\"]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("x", "{\"v\": 1, \"looks\": []}"));
        assertThrows(IllegalArgumentException.class, () -> parse("x", "{\"v\": 1}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("x", "{\"v\": 1, \"looks\": [\"rust\"], \"grades\": [1, 1]}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("x", "{\"v\": 1, \"looks\": [\"rust\"], \"grades\": [0, 0, 0, 0, 0]}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("x", "{\"v\": 1, \"looks\": [\"rust\"], \"name\": \"\"}"));
    }

    @Test
    void patternsTheTableDoesNotKnowAreDropped() {
        FinishTable table = FinishTable.parse(JsonParser.parseString("""
                {"v": 1, "grades": [1, 1, 1, 1, 1],
                 "bands": [{"upper": 0.2, "weight": 1}, {"upper": 0.4, "weight": 1}, {"upper": 0.6, "weight": 1},
                           {"upper": 0.8, "weight": 1}, {"upper": 1.0, "weight": 1}],
                 "finishes": [{"id": "ak47_rust", "gun": "tacz:ak47", "look": "rust", "grade": 1,
                               "wear_min": 0, "wear_max": 1}]}""").getAsJsonObject());
        Map<String, Series> checked = FinishTables.check(List.of(
                new Series("some", Map.of(), Set.of("rust", "nope"), null),
                new Series("none", Map.of(), Set.of("nope"), null)), table);
        assertEquals(Set.of("some"), checked.keySet(), "a series left with no patterns is left out");
        assertEquals(Set.of("rust"), checked.get("some").looks());
        assertTrue(table.paints("tacz:ak47", checked.get("some").pool()));
    }
}
