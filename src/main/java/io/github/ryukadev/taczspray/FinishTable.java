package io.github.ryukadev.taczspray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.DoubleSupplier;

/**
 * Which finishes each gun can get and how one is rolled, from {@code data/taczspray/finish_table.json}. Server only.
 *
 * <p>A roll: the grade by its weight among the grades this gun has finishes in (the others' weight shared out), one of
 * that grade's finishes evenly; then a wear band by its odds, a number evenly inside the band, scaled into the
 * finish's own wear range; last a pattern number and a wear number, which place the pattern and the scratches so that
 * no two guns look alike.
 *
 * <p>A roll draws from a {@link Pool}: every finish (a plain can), a series' patterns with the series' grade weights,
 * or one pattern (a can for one finish). Only the finishes the gun has in the pool take part.
 */
public final class FinishTable {
    /** Pattern and wear numbers run 1..SEEDS. */
    public static final int SEEDS = 1000;
    public static final FinishTable EMPTY = new FinishTable(new double[Finish.GRADES], List.of(), List.of());

    /** One finish a gun can get. {@code gun} is TaCZ's gun id, {@code look} the pattern. */
    public record Entry(String id, String gun, String look, int grade, double wearMin, double wearMax) {
    }

    /**
     * Which finishes a roll may give: those whose pattern is in {@code looks} (every pattern when null), by
     * {@code grades} (five weights, commonest first; the table's when null).
     */
    public record Pool(@Nullable Set<String> looks, @Nullable double[] grades) {
        public static final Pool ALL = new Pool(null, null);

        public Pool {
            looks = looks == null ? null : Set.copyOf(looks);
            if (grades != null) {
                if (grades.length != Finish.GRADES) {
                    throw new IllegalArgumentException("need " + Finish.GRADES + " grade weights");
                }
                for (double g : grades) {
                    if (!(g >= 0)) {
                        throw new IllegalArgumentException("grade weights must not be negative");
                    }
                }
                grades = grades.clone();
            }
        }

        /** Every grade weighs the same: a can for one finish gives it whatever the owner's grade odds say. */
        private static final double[] ANY_GRADE = {1, 1, 1, 1, 1};

        public static Pool of(String look) {
            return new Pool(Set.of(look), ANY_GRADE);
        }

        boolean has(Entry e) {
            return looks == null || looks.contains(e.look());
        }
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
        return paints(gun, Pool.ALL);
    }

    /** Whether the gun has a finish in the pool (one whose grade weighs something). */
    public boolean paints(String gun, Pool pool) {
        return ready() && !forGun(gun, pool).isEmpty();
    }

    /** Every gun that has a finish. */
    public Set<String> guns() {
        Set<String> out = new TreeSet<>();
        entries.forEach(e -> out.add(e.gun()));
        return out;
    }

    /**
     * This table with the server owner's odds: grade and wear band weights (null keeps the table's). A weight may be
     * 0, so long as one of the five is not.
     */
    public FinishTable withOdds(@Nullable double[] grades, @Nullable double[] bandWeights) {
        double[] g = grades == null ? gradeWeights : checkWeights(grades, "grade");
        List<Band> b = bands;
        if (bandWeights != null) {
            checkWeights(bandWeights, "wear band");
            if (bandWeights.length != bands.size()) {
                throw new IllegalArgumentException("need " + bands.size() + " wear band weights");
            }
            b = new ArrayList<>();
            for (int i = 0; i < bands.size(); i++) {
                b.add(new Band(bands.get(i).upper(), bandWeights[i]));
            }
        }
        return new FinishTable(g, b, entries);
    }

    private static double[] checkWeights(double[] w, String what) {
        double total = 0;
        for (double x : w) {
            if (!(x >= 0 && x < 1e9)) {
                throw new IllegalArgumentException(what + " weights must be 0 or more");
            }
            total += x;
        }
        if (w.length != Finish.GRADES || !(total > 0)) {
            throw new IllegalArgumentException("need " + Finish.GRADES + " " + what + " weights, one over 0");
        }
        return w;
    }

    /**
     * This table with the server owner's own finishes added, each on its guns (every gun in the table when it names
     * none; guns the table lacks are skipped and returned in {@code skipped}, by finish). The finish on a gun is
     * {@code <gun's path>_<look>}, as ours are.
     */
    public FinishTable withCustom(List<CustomFinish> custom, Map<String, Set<String>> skipped) {
        Set<String> guns = guns();
        List<Entry> out = new ArrayList<>(entries);
        for (CustomFinish c : custom) {
            for (String gun : c.guns() == null ? guns : c.guns()) {
                String id = gun.substring(gun.indexOf(':') + 1) + "_" + c.look().look();
                if (!guns.contains(gun) || !Finish.NAME.matcher(id).matches()) {
                    skipped.computeIfAbsent(c.look().look(), k -> new TreeSet<>()).add(gun);
                    continue;
                }
                out.add(new Entry(id, gun, c.look().look(), c.grade(), c.wearMin(), c.wearMax()));
            }
        }
        return new FinishTable(gradeWeights, bands, out);
    }

    /** Every pattern in the table. */
    public Set<String> looks() {
        Set<String> out = new TreeSet<>();
        entries.forEach(e -> out.add(e.look()));
        return out;
    }

    /** The finishes a gun may get from the pool, by grade; grades that weigh nothing are left out. */
    Map<Integer, List<Entry>> forGun(String gun, Pool pool) {
        double[] weights = weights(pool);
        Map<Integer, List<Entry>> out = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (e.gun().equals(gun) && pool.has(e) && weights[e.grade() - 1] > 0) {
                out.computeIfAbsent(e.grade(), g -> new ArrayList<>()).add(e);
            }
        }
        return out;
    }

    private double[] weights(Pool pool) {
        return pool.grades() == null ? gradeWeights : pool.grades();
    }

    /** A finish for this gun, or empty when the table has not loaded or the gun has none. */
    public Optional<Finish> roll(String gun, DoubleSupplier random) {
        return roll(gun, Pool.ALL, random);
    }

    /** A finish for this gun from the pool, or empty when the table has not loaded or the gun has none in it. */
    public Optional<Finish> roll(String gun, Pool pool, DoubleSupplier random) {
        return pick(gun, pool, random).map(entry -> finishOf(entry, random));
    }

    Finish finishOf(Entry entry, DoubleSupplier random) {
        double wear = clamp(rollWear(entry.wearMin(), entry.wearMax(), random), entry.wearMin(), entry.wearMax());
        int pattern = 1 + Math.min(SEEDS - 1, (int) (random.getAsDouble() * SEEDS));
        int wearNo = 1 + Math.min(SEEDS - 1, (int) (random.getAsDouble() * SEEDS));
        return new Finish(entry.id(), entry.look(), entry.grade(), bandOf(wear) + 1, wear, pattern, wearNo);
    }

    /** One of a gun's finishes in the pool by the grades' odds: the rolled one, and the reveal's other cells. */
    Optional<Entry> pick(String gun, Pool pool, DoubleSupplier random) {
        if (!ready()) {
            return Optional.empty();
        }
        Map<Integer, List<Entry>> mine = forGun(gun, pool);
        if (mine.isEmpty()) {
            return Optional.empty();
        }
        double[] weights = weights(pool);
        double total = 0;
        for (int g : mine.keySet()) {
            total += weights[g - 1];
        }
        double pick = random.getAsDouble() * total;
        int grade = -1;
        for (int g = 1; g <= Finish.GRADES; g++) {
            if (!mine.containsKey(g)) {
                continue;
            }
            grade = g;
            pick -= weights[g - 1];
            if (pick < 0) {
                break;
            }
        }
        return Optional.of(evenly(mine.get(grade), random));
    }

    /** The reveal's reel for a gun that got {@code won}. */
    public Reel reel(String gun, Finish won, DoubleSupplier random) {
        return reel(gun, Pool.ALL, won, random);
    }

    /**
     * The reveal's reel for a gun that got {@code won} from the pool, drawn from the same pool. When the gun has one
     * finish in it there is nothing to scroll past: the reel is that one cell.
     */
    public Reel reel(String gun, Pool pool, Finish won, DoubleSupplier random) {
        Entry winner = entries.stream().filter(e -> e.id().equals(won.id())).findFirst().orElseThrow();
        Map<Integer, List<Entry>> mine = forGun(gun, pool);
        if (mine.values().stream().mapToInt(List::size).sum() <= 1) {
            return new Reel(List.of(winner), 0, 0);
        }
        List<Entry> cells = new ArrayList<>(Reel.CELLS);
        for (int i = 0; i < Reel.CELLS; i++) {
            cells.add(i == Reel.WIN_AT ? winner : pick(gun, pool, random).orElse(winner));
        }
        // the near miss: one or two grades up, among those the gun has
        TreeSet<Integer> rarer = new TreeSet<>(mine.keySet().stream().filter(g -> g > winner.grade()).toList());
        if (!rarer.isEmpty() && random.getAsDouble() < Reel.NEAR_MISS) {
            List<Integer> up = new ArrayList<>(rarer);
            int grade = up.get(Math.min(up.size() - 1, (int) (random.getAsDouble() * 2)));
            cells.set(Reel.WIN_AT + 1, evenly(mine.get(grade), random));
        }
        double land = (random.getAsDouble() * 2 - 1) * Reel.LAND;
        return new Reel(cells, Reel.WIN_AT, land);
    }

    /**
     * A wear in {@code [lo, hi]} by the bands' odds, as if rolled over 0..1 and kept only when it falls in the range:
     * each band weighs its odds times the share of it inside the range, and the wear is even within its part. Over
     * the whole range (all our finishes) that is the bands' odds as they are; a band weighted 0 never comes out. When
     * the range lies wholly in bands weighted 0, the range wins: the bands' odds are laid over it instead.
     */
    private double rollWear(double lo, double hi, DoubleSupplier random) {
        double[] w = new double[bands.size()];
        for (int i = 0; i < bands.size(); i++) {
            double a = lower(i);
            double b = bands.get(i).upper();
            double inside = Math.min(b, hi) - Math.max(a, lo);
            w[i] = inside > 0 && b > a ? bands.get(i).weight() * inside / (b - a) : 0;
        }
        if (!(sum(w) > 0)) {
            if (!(hi > lo)) {
                return lo;
            }
            for (int i = 0; i < bands.size(); i++) {
                w[i] = bands.get(i).weight();
            }
            int band = pickBand(w, random);
            double x = lower(band) + random.getAsDouble() * (bands.get(band).upper() - lower(band));
            return lo + x * (hi - lo);
        }
        int band = pickBand(w, random);
        double a = Math.max(lower(band), lo);
        double b = Math.min(bands.get(band).upper(), hi);
        return a + random.getAsDouble() * (b - a);
    }

    private double lower(int band) {
        return band == 0 ? 0 : bands.get(band - 1).upper();
    }

    private static double sum(double[] w) {
        double total = 0;
        for (double x : w) {
            total += x;
        }
        return total;
    }

    private static int pickBand(double[] w, DoubleSupplier random) {
        double pick = random.getAsDouble() * sum(w);
        for (int i = 0; i < w.length; i++) {
            pick -= w[i];
            if (pick < 0) {
                return i;
            }
        }
        // rounding left a little over: the last band that can come out at all
        for (int i = w.length - 1; i > 0; i--) {
            if (w[i] > 0) {
                return i;
            }
        }
        return 0;
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
            if (CustomLook.isCustom(entry.look())) {
                throw new IllegalArgumentException("finish " + entry.id() + ": " + CustomLook.PREFIX
                        + "* is kept for server owners' own finishes");
            }
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
