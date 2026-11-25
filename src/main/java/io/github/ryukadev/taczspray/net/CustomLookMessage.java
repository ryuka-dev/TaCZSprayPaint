package io.github.ryukadev.taczspray.net;

import io.github.ryukadev.taczspray.Anim;
import io.github.ryukadev.taczspray.CustomLook;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server to client, after a {@link CatalogMessage} that lists it: one of the server owner's own finishes, its pattern's
 * pixels with it, so that players need no resource pack. Everything read back is checked as the server checked it
 * ({@link CustomLook}); at most 128 by 128 pixels, about 55 kB a message at most.
 */
public record CustomLookMessage(CustomLook look) {
    private static final int MAX_ID = 64;
    private static final int MAX_LANGUAGE = 8;
    private static final int MAX_NAME = 64;
    private static final int MAX_TYPE = 8;

    void encode(FriendlyByteBuf buf) {
        CustomLook l = look;
        buf.writeUtf(l.look(), MAX_ID);
        buf.writeVarInt(l.names().size());
        l.names().forEach((language, name) -> {
            buf.writeUtf(language, MAX_LANGUAGE);
            buf.writeUtf(name, MAX_NAME);
        });
        buf.writeBoolean(l.full());
        buf.writeBoolean(l.side());
        buf.writeDouble(l.pixel());
        buf.writeVarInt(l.anim().size());
        for (Anim a : l.anim()) {
            buf.writeUtf(a.type(), MAX_TYPE);
            buf.writeDouble(a.speed());
            buf.writeDouble(a.period());
            buf.writeDouble(a.amount());
            buf.writeDouble(a.degrees());
            buf.writeDouble(a.width());
            buf.writeDouble(a.strength());
        }
        buf.writeBoolean(l.material() != null);
        if (l.material() != null) {
            buf.writeDouble(l.material().smoothness());
            buf.writeBoolean(l.material().metal());
        }
        buf.writeVarInt(l.size());
        for (int y = 0; y < l.size(); y++) {
            for (int x = 0; x < l.size(); x++) {
                buf.writeMedium(l.at(x, y));
            }
        }
    }

    static CustomLookMessage decode(FriendlyByteBuf buf) {
        String look = buf.readUtf(MAX_ID);
        int languages = buf.readVarInt();
        if (languages < 0 || languages > CatalogMessage.MAX_LANGUAGES) {
            throw new IllegalArgumentException("bad language count " + languages);
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (int i = 0; i < languages; i++) {
            names.put(buf.readUtf(MAX_LANGUAGE), buf.readUtf(MAX_NAME));
        }
        boolean full = buf.readBoolean();
        boolean side = buf.readBoolean();
        double pixel = buf.readDouble();
        int effects = buf.readVarInt();
        if (effects < 0 || effects > Anim.MAX) {
            throw new IllegalArgumentException("bad effect count " + effects);
        }
        List<Anim> anim = new ArrayList<>();
        for (int i = 0; i < effects; i++) {
            anim.add(new Anim(buf.readUtf(MAX_TYPE), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        CustomLook.Material material = buf.readBoolean()
                ? new CustomLook.Material(buf.readDouble(), buf.readBoolean()) : null;
        int size = buf.readVarInt();
        if (size < CustomLook.MIN_SIZE || size > CustomLook.MAX_SIZE) {
            throw new IllegalArgumentException("bad pattern size " + size);
        }
        int[] rgb = new int[size * size];
        for (int i = 0; i < rgb.length; i++) {
            rgb[i] = buf.readUnsignedMedium();
        }
        return new CustomLookMessage(new CustomLook(look, names, full, side, pixel, anim, material, size, rgb));
    }
}
