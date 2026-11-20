package io.github.ryukadev.taczspray;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinishTableTest {
    private static final String TABLE = """
            {"v": 1, "grades": [79.92, 15.98, 3.20, 0.64, 0.26],
             "bands": [{"upper": 0.07, "weight": 3}, {"upper": 0.15, "weight": 24}, {"upper": 0.38, "weight": 33},
                       {"upper": 0.45, "weight": 24}, {"upper": 1.0, "weight": 16}],
             "finishes": [
               {"id": "ak47_rust", "gun": "tacz:ak47", "look": "rust", "grade": 1, "wear_min": 0, "wear_max": 1},
               {"id": "ak47_brass", "gun": "tacz:ak47", "look": "brass", "grade": 1, "wear_min": 0, "wear_max": 1},
               {"id": "ak47_sakura", "gun": "tacz:ak47", "look": "sakura", "grade": 2, "wear_min": 0, "wear_max": 1},
               {"id": "ak47_lava", "gun": "tacz:ak47", "look": "lava", "grade": 4, "wear_min": 0, "wear_max": 0.5},
               {"id": "m4a1_rust", "gun": "tacz:m4a1", "look": "rust", "grade": 1, "wear_min": 0, "wear_max": 1}
             ]}
            """;

    private static FinishTable table() {
        return FinishTable.parse(JsonParser.parseString(TABLE).getAsJsonObject());
    }

    @Test
    void gradesFollowTheWeightsOfTheGradesTheGunHas() {
        FinishTable t = table();
        Random random = new Random(1);
        int[] count = new int[6];
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            Finish f = t.roll("tacz:ak47", random::nextDouble).orElseThrow();
            count[f.grade()]++;
            assertTrue(f.id().startsWith("ak47_"));
            assertTrue(f.patternNo() >= 1 && f.patternNo() <= FinishTable.SEEDS);
            if (f.id().equals("ak47_lava")) {
                assertTrue(f.wear() <= 0.5, "wear stays in the finish's own range");
            }
        }
        // the AK has grades 1, 2 and 4: their weights shared out over the three
        double total = 79.92 + 15.98 + 0.64;
        assertEquals(79.92 / total, count[1] / (double) n, 0.005);
        assertEquals(15.98 / total, count[2] / (double) n, 0.005);
        assertEquals(0.64 / total, count[4] / (double) n, 0.001);
        assertEquals(0, count[3] + count[5]);
    }

    @Test
    void theBandMatchesTheWear() {
        FinishTable t = table();
        Random random = new Random(2);
        double[] upper = {0.07, 0.15, 0.38, 0.45, 1.0};
        for (int i = 0; i < 20_000; i++) {
            Finish f = t.roll("tacz:m4a1", random::nextDouble).orElseThrow();
            assertTrue(f.wear() < upper[f.band() - 1] || f.band() == 5);
            assertTrue(f.band() == 1 || f.wear() >= upper[f.band() - 2]);
        }
    }

    @Test
    void aGunWithoutFinishesGetsNone() {
        FinishTable t = table();
        assertFalse(t.paints("tacz:glock_17"));
        assertTrue(t.roll("tacz:glock_17", Math::random).isEmpty());
        assertTrue(FinishTable.EMPTY.roll("tacz:ak47", Math::random).isEmpty());
    }

    @Test
    void theReelHasTheWinnerWhereItSays() {
        FinishTable t = table();
        Random random = new Random(3);
        Finish won = t.roll("tacz:ak47", random::nextDouble).orElseThrow();
        FinishTable.Reel reel = t.reel("tacz:ak47", won, random::nextDouble);
        assertEquals(FinishTable.Reel.CELLS, reel.cells().size());
        assertEquals(won.id(), reel.cells().get(reel.winAt()).id());
        assertTrue(Math.abs(reel.land()) <= 0.5);
    }

    @Test
    void malformedTablesAreRefused() {
        JsonObject bad = JsonParser.parseString(TABLE.replace("\"upper\": 1.0", "\"upper\": 0.9")).getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> FinishTable.parse(bad));
        JsonObject badId = JsonParser.parseString(TABLE.replace("ak47_rust", "AK-47 rust")).getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> FinishTable.parse(badId));
    }
}
