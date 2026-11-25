package io.github.ryukadev.taczspray;

import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The server owner's switches, {@code config/taczspray/settings.toml} (Forge writes it, with these comments, on the
 * first start). Read when the server's data loads: on start and on {@code /reload}.
 */
public final class Settings {
    public static final String FILE = TaczSpray.MOD_ID + "/settings.toml";
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue CRAFT_SPRAY_CAN;
    private static final ForgeConfigSpec.BooleanValue CRAFT_SPRAY_CASE;
    private static final ForgeConfigSpec.BooleanValue CRAFT_PAINT_REMOVER;
    private static final ForgeConfigSpec.ConfigValue<List<?>> GRADES;
    private static final ForgeConfigSpec.ConfigValue<List<?>> WEAR_BANDS;
    private static final ForgeConfigSpec.ConfigValue<String> PLAIN_SERIES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("TaCZ Spray Paint: the server owner's switches. Run /reload (or restart) after editing.",
                "Series and your own finishes go in the series/ and finishes/ folders next to this file;",
                "see examples/ and the server owners' guide.").push("crafting");
        CRAFT_SPRAY_CAN = b.comment("Whether players can craft spray cans at a crafting table.",
                "Off: the recipe is gone; cans only come from /taczspray give (or whatever runs it).")
                .define("spray_can", true);
        CRAFT_SPRAY_CASE = b.comment("Whether players can craft spray can cases (ten finishes at once, one to keep;",
                "eight spray cans and a diamond).").define("spray_case", true);
        CRAFT_PAINT_REMOVER = b.comment("Whether players can craft paint remover.").define("paint_remover", true);
        b.pop().push("odds");
        GRADES = b.comment("Five weights for the grades, standard to unique, e.g. [79.92, 15.98, 3.2, 0.64, 0.26]",
                "(the default). A weight may be 0 (that grade never comes out); they need not add up to 100.",
                "Leave it empty, [], for the default. Series without their own \"grades\" use these too.")
                .<Object>defineList("grades", List.of(), Settings::anything);
        WEAR_BANDS = b.comment("Five weights for how worn a finish comes out: factory new, minimal wear, field-tested,",
                "well-worn, battle-scarred. The default is [3, 24, 33, 24, 16]. [3, 24, 33, 0, 0] never gives the",
                "two most worn. Leave it empty, [], for the default.")
                .<Object>defineList("wear_bands", List.of(), Settings::anything);
        b.pop().push("plain_can");
        PLAIN_SERIES = b.comment("A series (the name of a file in series/, without .json) that plain spray cans draw",
                "from: crafted ones and those given without a series or a finish. Empty: every finish, except",
                "your own finishes marked \"plain_cans\": false.")
                .define("series", "", o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private Settings() {
    }

    /**
     * Forge's own check would quietly rewrite a bad value (drop a list's bad numbers, empty a bad name) in the file;
     * every value is let through to ours instead, which logs what is wrong and keeps the file as the owner wrote it.
     */
    private static boolean anything(Object o) {
        return true;
    }

    /** Whether {@code item} ("spray_can", "spray_case", "paint_remover") may be crafted; anything else may. */
    public static boolean craftable(String item) {
        return switch (item) {
            case "spray_can" -> loaded() ? CRAFT_SPRAY_CAN.get() : true;
            case "spray_case" -> loaded() ? CRAFT_SPRAY_CASE.get() : true;
            case "paint_remover" -> loaded() ? CRAFT_PAINT_REMOVER.get() : true;
            default -> {
                TaczSpray.LOG.warn("A recipe asks whether \"{}\" may be crafted; only spray_can, spray_case and "
                        + "paint_remover "
                        + "have a switch", item);
                yield true;
            }
        };
    }

    /** The owner's grade weights, or null for the table's. */
    @Nullable
    public static double[] grades() {
        return loaded() ? weights(GRADES.get(), "grades") : null;
    }

    /** The owner's wear band weights, or null for the table's. */
    @Nullable
    public static double[] wearBands() {
        return loaded() ? weights(WEAR_BANDS.get(), "wear_bands") : null;
    }

    /**
     * The series plain cans draw from, or null for every finish. A name no series can have is still returned: plain
     * cans then refuse, as for a series that is not there, rather than quietly giving everything.
     */
    @Nullable
    public static String plainSeries() {
        String s = loaded() ? PLAIN_SERIES.get() : "";
        return s.isEmpty() ? null : s;
    }

    private static boolean loaded() {
        return SPEC.isLoaded();
    }

    @Nullable
    static double[] weights(List<?> list, String what) {
        if (list.isEmpty()) {
            return null;
        }
        if (list.size() != 5) {
            TaczSpray.LOG.error("settings.toml {}: needs five weights, has {}; using the default", what, list.size());
            return null;
        }
        double[] out = new double[5];
        double total = 0;
        for (int i = 0; i < 5; i++) {
            if (!(list.get(i) instanceof Number n) || !(n.doubleValue() >= 0 && n.doubleValue() < 1e9)) {
                TaczSpray.LOG.error("settings.toml {}: {} is not a weight (a number, 0 or more); using the default",
                        what, list.get(i));
                return null;
            }
            out[i] = n.doubleValue();
            total += out[i];
        }
        if (!(total > 0)) {
            TaczSpray.LOG.error("settings.toml {}: at least one weight must be over 0; using the default", what);
            return null;
        }
        return out;
    }
}
