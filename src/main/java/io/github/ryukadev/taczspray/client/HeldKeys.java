package io.github.ryukadev.taczspray.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * The keys already down when a screen opened (sneak, a movement key): a key held down keeps sending presses, so those
 * would count as pressed in the screen at once. They count only after being let go.
 */
final class HeldKeys {
    /** GLFW's key codes, as ranges: asking GLFW about a code it does not define reports an error. */
    private static final int[][] KEYS = {{32, 32}, {39, 39}, {44, 57}, {59, 59}, {61, 61}, {65, 93}, {96, 96},
            {161, 162}, {256, 269}, {280, 284}, {290, 314}, {320, 336}, {340, 348}};
    private final Set<Integer> held = new HashSet<>();

    static HeldKeys now() {
        HeldKeys keys = new HeldKeys();
        long window = Minecraft.getInstance().getWindow().getWindow();
        for (int[] range : KEYS) {
            for (int k = range[0]; k <= range[1]; k++) {
                if (GLFW.glfwGetKey(window, k) == GLFW.GLFW_PRESS) {
                    keys.held.add(k);
                }
            }
        }
        return keys;
    }

    /** A press in the screen that counts: not a key still held from before it opened. */
    boolean fresh(int key) {
        return !held.contains(key);
    }

    void released(int key) {
        held.remove(key);
    }
}
