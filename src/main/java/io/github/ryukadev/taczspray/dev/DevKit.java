package io.github.ryukadev.taczspray.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.FinishTable;
import io.github.ryukadev.taczspray.FinishTables;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.item.ModItems;
import io.github.ryukadev.taczspray.item.PaintingItem;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * Development help, off unless the game is started with {@code -Dtaczspray.devkit=true}: a player who joins gets a
 * stack of spray cans, one of paint remover and a few guns to try them on, one of them a gun with no finishes (to see
 * the refusal). For filming a reveal, {@code /taczspray_dev rig <pattern>} makes every single spray from then on land
 * on that pattern (when the gun has it), the reel still drawn from the can's own pool so it scrolls as usual;
 * {@code /taczspray_dev rig off} ends it. Not part of the mod's behaviour.
 */
public final class DevKit {
    public static final String PROPERTY = "taczspray.devkit";
    private static final String[] GUNS = {"tacz:ak47", "tacz:m4a1", "tacz:ai_awp", "tacz:deagle", "tacz:glock_17"};

    /** The pattern every single spray lands on while filming, or null. Server side, dev kit only. */
    private static volatile String rigged;

    private DevKit() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void start() {
        MinecraftForge.EVENT_BUS.addListener(DevKit::joined);
        MinecraftForge.EVENT_BUS.addListener(DevKit::commands);
    }

    /** While rigged and the gun has the pattern: a finish of it, rolled as a can for that pattern would. */
    public static Optional<Finish> rigged(String gun, FinishTable table, DoubleSupplier random) {
        String look = enabled() ? rigged : null;
        if (look == null) {
            return Optional.empty();
        }
        FinishTable.Pool pool = FinishTable.Pool.of(look);
        return table.paints(gun, pool) ? table.roll(gun, pool, random) : Optional.empty();
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("taczspray_dev").requires(s -> s.hasPermission(2))
                .then(Commands.literal("rig")
                        .then(Commands.literal("off").executes(c -> {
                            rigged = null;
                            c.getSource().sendSuccess(() -> Component.literal("[devkit] sprays roll as usual"), false);
                            return 1;
                        }))
                        .then(Commands.argument("pattern", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(FinishTables.current().looks(), b))
                                .executes(c -> {
                                    String look = StringArgumentType.getString(c, "pattern");
                                    if (!FinishTables.current().looks().contains(look)) {
                                        c.getSource().sendFailure(Component.literal("[devkit] no pattern " + look));
                                        return 0;
                                    }
                                    rigged = look;
                                    c.getSource().sendSuccess(() -> Component.literal(
                                            "[devkit] every single spray now lands on " + look), false);
                                    return 1;
                                }))));
    }

    private static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.getInventory().add(new ItemStack(ModItems.SPRAY_CAN.get(), PaintingItem.STACK));
        player.getInventory().add(new ItemStack(ModItems.PAINT_REMOVER.get(), PaintingItem.STACK));
        for (String id : GUNS) {
            player.getInventory().add(DevGuns.make(id));
        }
        TaczSpray.LOG.info("[devkit] gave {} the spray cans, the removers and {} guns", player.getName().getString(),
                GUNS.length);
    }
}
