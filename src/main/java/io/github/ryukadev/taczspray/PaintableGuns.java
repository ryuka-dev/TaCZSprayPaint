package io.github.ryukadev.taczspray;

import java.util.Set;

/**
 * The guns this client has finish data for (the mod's {@code assets/taczspray/finishes/guns.json}). Only for the client
 * to decide whether to start the spraying motion; whether a gun really gets a finish is the server's table.
 */
public final class PaintableGuns {
    private static volatile Set<String> client = Set.of();

    private PaintableGuns() {
    }

    public static boolean clientKnows(String gunId) {
        return client.contains(gunId);
    }

    public static void setClient(Set<String> guns) {
        client = Set.copyOf(guns);
    }
}
