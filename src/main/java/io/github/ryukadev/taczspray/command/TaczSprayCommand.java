package io.github.ryukadev.taczspray.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.ryukadev.taczspray.FinishTables;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.item.CanLabel;
import io.github.ryukadev.taczspray.item.ModItems;
import io.github.ryukadev.taczspray.item.PaintingItem;
import io.github.ryukadev.taczspray.item.TenPullChoices;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.function.Function;

/**
 * The mod's commands.
 * <ul>
 * <li>{@code /taczspray give <players> [series <id> | look <pattern>] [count]}: plain or labelled spray cans, for server
 * owners and for anything that runs commands (shops, crates, vote rewards). Needs permission level 2. What does not
 * fit in a player's inventory is dropped at their feet, as with {@code /give}.</li>
 * <li>{@code /taczspray choose}: any player; opens the ten-pull of the gun in either hand again, to pick from (the
 * chat link after a ten-pull screen was closed runs it).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TaczSpray.MOD_ID)
public final class TaczSprayCommand {
    static final int MAX_COUNT = 1024;
    private static final DynamicCommandExceptionType NO_SERIES = new DynamicCommandExceptionType(
            id -> Component.translatable("taczspray.command.no_series", id));
    private static final DynamicCommandExceptionType NO_LOOK = new DynamicCommandExceptionType(
            look -> Component.translatable("taczspray.command.no_look", look));

    private TaczSprayCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(TaczSpray.MOD_ID)
                .then(Commands.literal("choose")
                        .executes(c -> TenPullChoices.reopen(c.getSource().getPlayerOrException())))
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> give(c, CanLabel.PLAIN, 1))
                                .then(count(c -> CanLabel.PLAIN))
                                .then(Commands.literal("series")
                                        .then(withCount(Commands.argument("series", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        FinishTables.series().keySet(), b)), TaczSprayCommand::series)))
                                .then(Commands.literal("look")
                                        .then(withCount(Commands.argument("look", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        FinishTables.current().looks(), b)), TaczSprayCommand::look))))));
    }

    private interface Label {
        CanLabel of(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withCount(T node, Label label) {
        return node.executes(c -> give(c, label.of(c), 1)).then(count(label));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> count(Label label) {
        return Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                .executes(c -> give(c, label.of(c), IntegerArgumentType.getInteger(c, "count")));
    }

    private static CanLabel series(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "series");
        if (!FinishTables.series().containsKey(id)) {
            throw NO_SERIES.create(id);
        }
        return CanLabel.ofSeries(id);
    }

    private static CanLabel look(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String look = StringArgumentType.getString(c, "look");
        if (!FinishTables.current().looks().contains(look)) {
            throw NO_LOOK.create(look);
        }
        return CanLabel.ofLook(look);
    }

    private static int give(CommandContext<CommandSourceStack> c, CanLabel label, int count)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        for (ServerPlayer player : players) {
            int left = count;
            while (left > 0) {
                ItemStack stack = new ItemStack(ModItems.SPRAY_CAN.get(), Math.min(left, PaintingItem.STACK));
                label.writeTo(stack);
                left -= stack.getCount();
                if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                    ItemEntity dropped = player.drop(stack, false);
                    if (dropped != null) {
                        dropped.setNoPickUpDelay();
                        dropped.setTarget(player.getUUID());
                    }
                }
            }
        }
        Function<Integer, Component> said = players.size() == 1
                ? n -> Component.translatable("taczspray.command.gave", n, players.iterator().next().getDisplayName())
                : n -> Component.translatable("taczspray.command.gave_many", n, players.size());
        c.getSource().sendSuccess(() -> said.apply(count), true);
        return count * players.size();
    }
}
