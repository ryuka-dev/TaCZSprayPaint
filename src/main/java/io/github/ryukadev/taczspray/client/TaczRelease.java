package io.github.ryukadev.taczspray.client;

import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Which TaCZ release family is installed ("1.1", "1.2"): the default gun pack's models differ between them, so each
 * family has its own painted texels where they differ.
 */
public final class TaczRelease {
    private static String family;

    private TaczRelease() {
    }

    public static String family() {
        if (family == null) {
            family = ModList.get().getModContainerById("tacz")
                    .map(c -> {
                        ArtifactVersion v = c.getModInfo().getVersion();
                        return v.getMajorVersion() + "." + v.getMinorVersion();
                    })
                    .orElse("0.0");
        }
        return family;
    }

    /** The installed family if offered, else the newest offered one older than it, else null. */
    @Nullable
    public static String choose(Collection<String> offered) {
        String mine = family();
        if (offered.contains(mine)) {
            return mine;
        }
        String best = null;
        for (String f : offered) {
            if (compare(f, mine) < 0 && (best == null || compare(f, best) > 0)) {
                best = f;
            }
        }
        return best;
    }

    static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parse(x[i]) : 0;
            int q = i < y.length ? parse(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
