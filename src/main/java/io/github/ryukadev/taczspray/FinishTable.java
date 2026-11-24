package io.github.ryukadev.taczspray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.DoubleSupplier;

/**
 * Which finishes each gun can get and how one is rolled, from {@code data/taczspray/finish_table.json}. Server only.
 *
 * <p>A roll: the grade by its weight among the grades this gun has finishes in (the others' weight shared out), one of
 * that grade's finishes evenly; then a wear band by its odds, a number evenly inside the band, scaled into the
 * finish's own wear range; last a pattern number and a wear number, which place the pattern and the scratches so that
 * no two guns look alike.
 */
public final class FinishTable {
    /** Pattern and wear numbers run 1..SEEDS. */
    public static final int SEEDS = 1000;
    public static final FinishTable EMPTY = new FinishTable(new double[Finish.GRADES], List.of(), List.of());

    /** One finish a gun can get. {@code gun} is TaCZ's gun id, {@code look} the pattern. */
    public record Entry(String id, String gun, String look, int grade, double wearMin, double wearMax) {
    }

    /** A wear band: up to {@code upper} (the last up to 1.0), rolled with {@code weight}. */
    public record Band(double upper, double weight) {
    }

    /** The reel the reveal scrolls past: finishes drawn by the true odds, the rolled one at {@code winAt}. */
    public record Reel(List<Entry> cells, int winAt, double land) {
        public static final int CELLS = 44;
        public static final int WIN_AT = 38;
        /** How often the cell after the winner is a rarer one, when the gun has a rarer finish. */
        static final double NEAR_MISS = 0.6;
        /** The marker stops within this much of the cell's middle, either way, as a share of a cell. */
        static final double LAND = 0.35;
    }

    private final double[] gradeWeights;
    private final List<Band> bands;
    private final List<Entry> entries;

    FinishTable(double[] gradeWeights, List<Band> bands, List<Entry> entries) {
        this.gradeWeights = gradeWeights.clone();
        this.bands = List.copyOf(bands);
        this.entries = List.copyOf(entries);
    }

    public boolean ready() {
        return !bands.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** Every finish in the table, in the file's order. */
    public List<Entry> entries() {
        return entries;
    }

    public boolean paints(String gun) {
        return ready() && entries.stream().anyMatch(e -> e.gun().equals(gun));
    }

    /** The finishes a gun may get, by grade. */
    Map<Integer, List<Entry>> forGun(String gun) {
        Map<Integer, List<Entry>> out = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (e.gun().equals(gun)) {
                out.computeIfAbsent(e.grade(), g -> new ArrayList<>()).add(e);
            }
        }
        return out;
    }

    /** A finish for this gun, or empty when the table has not loaded or the gun has none. */
    public Optional<Finish> roll(String gun, DoubleSupplier random) {
        return pick(gun, random).map(entry -> finishOf(entry, random));
    }

    Finish finishOf(Entry entry, DoubleSupplier random) {
        int bandIndex = rollBand(random);
        double lower = bandIndex == 0 ? 0 : bands.get(bandIndex - 1).upper();
        double x = lower + random.getAsDouble() * (bands.get(bandIndex).upper() - lower);
        double wear = clamp(entry.wearMin() + x * (entry.wearMax() - entry.wearMin()), entry.wearMin(), entry.wearMax());
        int pattern = 1 + Math.min(SEEDS - 1, (int) (random.getAsDouble() * SEEDS));
        int wearNo = 1 + Math.min(SEEDS - 1, (int) (random.getAsDouble() * SEEDS));
        return new Finish(entry.id(), entry.look(), entry.grade(), bandOf(wear) + 1, wear, pattern, wearNo);
    }

    /** One of a gun's finishes by the grades' odds: the rolled one, and the reveal's other cells. */
    Optional<Entry> pick(String gun, DoubleSupplier random) {
        if (!ready()) {
            return Optional.empty();
        }
        Map<Integer, List<Entry>> mine = forGun(gun);
        if (mine.isEmpty()) {
            return Optional.empty();
        }
        double total = 0;
        for (int g : mine.keySet()) {
            total += gradeWeights[g - 1];
        }
        double pick = random.getAsDouble() * total;
        int grade = -1;
        for (int g = 1; g <= Finish.GRADES; g++) {
            if (!mine.containsKey(g)) {
                continue;
            }
            grade = g;
            pick -= gradeWeights[g - 1];
            if (pick < 0) {
                break;
            }
        }
        return Optional.of(evenly(mine.get(grade), random));
    }

    /** The reveal's reel for a gun that got {@code won}. */
    public Reel reel(String gun, Finish won, DoubleSupplier random) {
        Entry winner = entries.stream().filter(e -> e.id().equals(won.id())).findFirst().orElseThrow();
        List<Entry> cells = new ArrayList<>(Reel.CELLS);
        for (int i = 0; i < Reel.CELLS; i++) {
            cells.add(i == Reel.WIN_AT ? winner : pick(gun, random).orElse(winner));
        }
        // the near miss: one or two grades up, among those the gun has
        Map<Integer, List<Entry>> mine = forGun(gun);
        TreeSet<Integer> rarer = new TreeSet<>(mine.keySet().stream().filter(g -> g > winner.grade()).toList());
        if (!rarer.isEmpty() && random.getAsDouble() < Reel.NEAR_MISS) {
            List<Integer> up = new ArrayList<>(rarer);
            int grade = up.get(Math.min(up.size() - 1, (int) (random.getAsDouble() * 2)));
            cells.set(Reel.WIN_AT + 1, evenly(mine.get(grade), random));
        }
        double land = (random.getAsDouble() * 2 - 1) * Reel.LAND;
        return new Reel(cells, Reel.WIN_AT, land);
    }

    private int rollBand(DoubleSupplier random) {
        double total = bands.stream().mapToDouble(Band::weight).sum();
        double pick = random.getAsDouble() * total;
        for (int i = 0; i < bands.size(); i++) {
            pick -= bands.get(i).weight();
            if (pick < 0) {
                return i;
            }
        }
        return bands.size() - 1;
    }

    /** Which band a wear falls in, from 0 for the least worn. */
    int bandOf(double wear) {
        for (int i = 0; i < bands.size(); i++) {
            if (wear < bands.get(i).upper()) {
                return i;
            }
        }
        return bands.size() - 1;
    }

    static Entry evenly(List<Entry> pool, DoubleSupplier random) {
        return pool.get(Math.min(pool.size() - 1, (int) (random.getAsDouble() * pool.size())));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * {"v": 1, "grades": [5 weights, commonest first], "bands": [{"upper", "weight"}, ... up to 1.0],
     * "finishes": [{"id", "gun", "look", "grade", "wear_min", "wear_max"}]}. Anything malformed refuses the file.
     */
    public static FinishTable parse(JsonObject json) {
        if (json.get("v").getAsInt() != 1) {
            throw new IllegalArgumentException("finish_table.json version " + json.get("v"));
        }
        JsonArray grades = json.getAsJsonArray("grades");
        if (grades == null || grades.size() != Finish.GRADES) {
            throw new IllegalArgumentException("grades: need " + Finish.GRADES + " weights");
        }
        double[] weights = new double[Finish.GRADES];
        for (int i = 0; i < Finish.GRADES; i++) {
            weights[i] = positive(grades.get(i).getAsDouble(), "grade " + (i + 1));
        }
        List<Band> bands = new ArrayList<>();
        double last = 0;
        for (JsonElement e : json.getAsJsonArray("bands")) {
            JsonObject o = e.getAsJsonObject();
            double upper = o.get("upper").getAsDouble();
            if (!(upper > last && upper <= 1)) {
                throw new IllegalArgumentException("bands must rise to 1.0");
            }
            bands.add(new Band(upper, positive(o.get("weight").getAsDouble(), "band weight")));
            last = upper;
        }
        if (bands.size() != Finish.BANDS || last != 1.0) {
            throw new IllegalArgumentException("need " + Finish.BANDS + " bands ending at 1.0");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("finishes")) {
            JsonObject o = e.getAsJsonObject();
            double min = o.get("wear_min").getAsDouble();
            double max = o.get("wear_max").getAsDouble();
            int grade = o.get("grade").getAsInt();
            if (!(min >= 0 && min <= max && max <= 1) || grade < 1 || grade > Finish.GRADES) {
                throw new IllegalArgumentException("finish " + o.get("id") + " out of range");
            }
            Entry entry = new Entry(o.get("id").getAsString(), o.get("gun").getAsString(), o.get("look").getAsString(),
                    grade, min, max);
            // the same checks a finish on a gun gets
            new Finish(entry.id(), entry.look(), grade, 1, min, 0, 0);
            entries.add(entry);
        }
        return new FinishTable(weights, bands, entries);
    }

    private static double positive(double v, String what) {
        if (!(v > 0)) {
            throw new IllegalArgumentException(what + " must be positive");
        }
        return v;
    }
}
