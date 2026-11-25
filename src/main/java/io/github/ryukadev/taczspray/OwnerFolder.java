package io.github.ryukadev.taczspray;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The first time the mod runs ({@code config/taczspray/} not there yet), writes the server owners' guide and an example
 * series and two example finishes into {@code config/taczspray/examples/}, where nothing loads them: an owner copies
 * one a level up to use it. Never writes over anything; a folder deleted is not written again.
 */
final class OwnerFolder {
    private static final String FROM = "/taczspray-examples/";
    private static final List<String> FILES = List.of(
            "server-owners.md",
            "server-owners.zh_cn.md",
            "series/metal.json",
            "finishes/example_stripes/finish.json",
            "finishes/example_stripes/pattern.png",
            "finishes/example_copper/finish.json",
            "finishes/example_copper/pattern.png");

    private OwnerFolder() {
    }

    static void writeExamplesIfNew() {
        Path folder = FinishTables.folder();
        if (Files.exists(folder)) {
            return;
        }
        Path to = folder.resolve("examples");
        try {
            for (String file : FILES) {
                Path out = to.resolve(file);
                Files.createDirectories(out.getParent());
                try (InputStream in = OwnerFolder.class.getResourceAsStream(FROM + file)) {
                    if (in == null) {
                        throw new IOException("missing from the jar: " + file);
                    }
                    Files.copy(in, out);
                }
            }
            TaczSpray.LOG.info("Wrote the server owners' guide and examples to {}", to);
        } catch (IOException e) {
            TaczSpray.LOG.warn("Could not write the examples to {}: {}", to, e.toString());
        }
    }
}
