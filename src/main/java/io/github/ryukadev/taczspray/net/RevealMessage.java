package io.github.ryukadev.taczspray.net;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.FinishTable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client after a spray: the gun as it now is (to show it), the finish it got and the reel to scroll past
 * before showing it. The reel is drawn on the server from the real odds; the client only plays it.
 */
public record RevealMessage(ItemStack gun, Finish won, List<Cell> reel, int winAt, double land) {
    static final int MAX_CELLS = 64;
    private static final int MAX_NAME = 64;

    /** One cell of the reel: which finish, its pattern and grade. */
    public record Cell(String id, String look, int grade) {
    }

    public RevealMessage(ItemStack gun, Finish won, FinishTable.Reel reel) {
        this(gun, won, reel.cells().stream().map(e -> new Cell(e.id(), e.look(), e.grade())).toList(), reel.winAt(),
                reel.land());
    }

    public RevealMessage {
        gun = gun.copy();
        reel = List.copyOf(reel);
        if (reel.isEmpty() || reel.size() > MAX_CELLS || winAt < 0 || winAt >= reel.size() || !(Math.abs(land) <= 0.5)) {
            throw new IllegalArgumentException("bad reel");
        }
        for (Cell c : reel) {
            if (c.grade() < 1 || c.grade() > Finish.GRADES) {
                throw new IllegalArgumentException("bad grade");
            }
        }
    }

    void encode(FriendlyByteBuf buf) {
        buf.writeItem(gun);
        writeFinish(buf, won);
        buf.writeVarInt(reel.size());
        for (Cell c : reel) {
            buf.writeUtf(c.id(), MAX_NAME);
            buf.writeUtf(c.look(), MAX_NAME);
            buf.writeVarInt(c.grade());
        }
        buf.writeVarInt(winAt);
        buf.writeDouble(land);
    }

    static RevealMessage decode(FriendlyByteBuf buf) {
        ItemStack gun = buf.readItem();
        Finish won = readFinish(buf);
        int n = buf.readVarInt();
        if (n < 1 || n > MAX_CELLS) {
            throw new IllegalArgumentException("bad reel length " + n);
        }
        List<Cell> cells = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            cells.add(new Cell(buf.readUtf(MAX_NAME), buf.readUtf(MAX_NAME), buf.readVarInt()));
        }
        return new RevealMessage(gun, won, cells, buf.readVarInt(), buf.readDouble());
    }

    static void writeFinish(FriendlyByteBuf buf, Finish f) {
        buf.writeUtf(f.id(), MAX_NAME);
        buf.writeUtf(f.look(), MAX_NAME);
        buf.writeVarInt(f.grade());
        buf.writeVarInt(f.band());
        buf.writeDouble(f.wear());
        buf.writeVarInt(f.patternNo());
        buf.writeVarInt(f.wearNo());
    }

    /** A finish as {@link #writeFinish} wrote it; one out of range throws (the record checks it). */
    static Finish readFinish(FriendlyByteBuf buf) {
        return new Finish(buf.readUtf(MAX_NAME), buf.readUtf(MAX_NAME), buf.readVarInt(), buf.readVarInt(),
                buf.readDouble(), buf.readVarInt(), buf.readVarInt());
    }

    public Cell wonCell() {
        return reel.get(winAt);
    }
}
