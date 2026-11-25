package io.github.ryukadev.taczspray;

import com.google.gson.JsonObject;

/**
 * One effect of a moving finish, as {@code finish.json} lists it under {@code anim}: {@code flow} (speed, pattern
 * pixels a second), {@code pulse} (period in seconds, amount 0 to 2), {@code hue} (period, degrees up to 180 either
 * way), {@code sweep} (period, width in model units, strength 0 to 1). Unused fields are 0. The client draws it; this
 * is only what a server owner's file says, checked, so that it can be sent.
 */
public record Anim(String type, double speed, double period, double amount, double degrees, double width,
                   double strength) {
    public static final int MAX = 8;
    private static final double LONGEST = 3600;

    public Anim {
        boolean ok = switch (type) {
            case "flow" -> Math.abs(speed) <= 64;
            case "pulse" -> period > 0 && period <= LONGEST && amount >= 0 && amount <= 2;
            case "hue" -> period > 0 && period <= LONGEST && Math.abs(degrees) <= 180;
            case "sweep" -> period > 0 && period <= LONGEST && width > 0 && width <= 64 && strength >= 0 && strength <= 1;
            default -> throw new IllegalArgumentException("effect " + type + ": flow, pulse, hue or sweep");
        };
        if (!ok) {
            throw new IllegalArgumentException("effect " + type + " out of range (see the server owners' guide)");
        }
    }

    public static Anim parse(JsonObject o) {
        if (!o.has("type")) {
            throw new IllegalArgumentException("an effect needs a \"type\"");
        }
        return new Anim(o.get("type").getAsString(), num(o, "speed"), num(o, "period"), num(o, "amount"),
                num(o, "degrees"), num(o, "width"), num(o, "strength"));
    }

    private static double num(JsonObject o, String name) {
        return o.has(name) ? o.get(name).getAsDouble() : 0;
    }
}
