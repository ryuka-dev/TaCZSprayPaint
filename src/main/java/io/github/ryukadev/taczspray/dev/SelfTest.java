package io.github.ryukadev.taczspray.dev;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.FinishTables;
import io.github.ryukadev.taczspray.TaczSpray;
import io.github.ryukadev.taczspray.client.ClientCatalog;
import io.github.ryukadev.taczspray.item.CanLabel;
import io.github.ryukadev.taczspray.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.function.Consumer;

/**
 * Development check, off unless the game is started with {@code -Dtaczspray.selftest=true}. In a singleplayer world,
 * with no input: audits every mixin; puts a spray can in the main hand and an AK-47 in the off hand and finishes a
 * spray on the server (the real roll, the real reveal); screenshots the reveal, then the painted gun in hand next to
 * two more painted ones on the ground; then takes the paint off with the remover and checks it is gone; quits. Along
 * the way it checks that a can and a remover are used up only when they do their work, and not in creative mode.
 * The gun is {@code -Dtaczspray.selftest.gun=<TaCZ gun id>} (default tacz:ak47). Not part of the mod's behaviour.
 */
public final class SelfTest {
    public static final String PROPERTY = "taczspray.selftest";
    private static final String NO_FINISHES = "tacz:glock_17";
    private static final String SERIES = "selftest_metal";
    /** The self-test script's own finishes: one for a can, one kept out of plain cans. */
    private static final String CUSTOM = "custom_selftest_stripes";
    private static final String KEPT = "custom_selftest_kept";

    private int ticks = -1;

    private SelfTest() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void start() {
        MinecraftForge.EVENT_BUS.addListener(new SelfTest()::tick);
    }

    public static void audit() {
        try {
            MixinEnvironment.getCurrentEnvironment().audit();
            TaczSpray.LOG.info("[selftest] mixin audit passed");
        } catch (Throwable t) {
            TaczSpray.LOG.error("[selftest] mixin audit failed", t);
        }
    }

    private void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.level == null) {
            return;
        }
        ticks++;
        switch (ticks) {
            case 60 -> onServer(mc, SelfTest::setUp);
            case 80 -> onServer(mc, SelfTest::spray);
            case 250 -> {
                TaczSpray.LOG.info("[selftest] reveal screen open: {}", mc.screen != null);
                shot(mc, "reveal");
            }
            case 270 -> {
                mc.setScreen(null);
                onServer(mc, player -> {
                    ItemStack gun = player.getOffhandItem().copy();
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    player.getInventory().setItem(0, gun);
                });
                mc.player.getInventory().selected = 0;
                mc.player.setXRot(55f);
            }
            case 340 -> {
                TaczSpray.LOG.info("[selftest] painted gun in hand: {}", Finish.of(mc.player.getMainHandItem()));
                shot(mc, "painted");
            }
            case 350 -> onServer(mc, player -> {
                ItemStack gun = player.getMainHandItem().copy();
                player.setItemInHand(InteractionHand.OFF_HAND, gun);
                ItemStack remover = new ItemStack(ModItems.PAINT_REMOVER.get());
                player.getInventory().setItem(0, remover);
                GameType mode = player.gameMode.getGameModeForPlayer();
                player.setGameMode(GameType.SURVIVAL);
                remover.getItem().finishUsingItem(remover, player.level(), player);
                player.setGameMode(mode);
                TaczSpray.LOG.info("[selftest] after the remover: {}; removers left {} (expect 0)",
                        Finish.of(player.getOffhandItem()), player.getMainHandItem().getCount());
            });
            case 370 -> onServer(mc, SelfTest::labels);
            case 400 -> {
                ItemStack can = new ItemStack(ModItems.SPRAY_CAN.get());
                CanLabel.ofSeries(SERIES).writeTo(can);
                TaczSpray.LOG.info("[selftest] client: series {} is called \"{}\"; a can of it says {}", SERIES,
                        ClientCatalog.seriesName(SERIES), can.getTooltipLines(mc.player, TooltipFlag.NORMAL).stream()
                                .map(Component::getString).toList());
                TaczSpray.LOG.info("[selftest] reveal open for the one-finish can: {}", mc.screen != null);
                shot(mc, "reveal-one");
            }
            case 420 -> {
                mc.setScreen(null);
                onServer(mc, SelfTest::owner);
            }
            case 470 -> {
                // JEI shows the recipes the server sent to the client; a recipe switched off never gets here
                var recipes = mc.level.getRecipeManager();
                TaczSpray.LOG.info("[selftest] client recipes (what JEI can show): spray can {}, paint remover {} "
                                + "(expect false, true); JEI loaded {}",
                        recipes.byKey(TaczSpray.id("spray_can")).isPresent(),
                        recipes.byKey(TaczSpray.id("paint_remover")).isPresent(),
                        ModList.get().isLoaded("jei"));
                TaczSpray.LOG.info("[selftest] reveal open for the custom finish: {}", mc.screen != null);
                shot(mc, "reveal-custom");
            }
            case 480 -> {
                mc.setScreen(null);
                mc.player.getInventory().selected = 0;
                mc.player.setXRot(55f);
            }
            case 540 -> {
                ItemStack held = mc.player.getMainHandItem();
                TaczSpray.LOG.info("[selftest] client: holds {}; the server sent {}: {}; tooltip {}", Finish.of(held),
                        CUSTOM, ClientCatalog.custom(CUSTOM) != null, held.getTooltipLines(mc.player,
                                TooltipFlag.NORMAL).stream().map(Component::getString).limit(3).toList());
                shot(mc, "custom");
            }
            case 545 -> {
                // the tooltips as the game draws them, in whatever language the game is in
                ItemStack can = new ItemStack(ModItems.SPRAY_CAN.get());
                TaczSpray.LOG.info("[selftest] {}: can tooltip {}", mc.getLanguageManager().getSelected(),
                        can.getTooltipLines(mc.player, TooltipFlag.NORMAL).stream()
                                .map(c -> c.getString() + " (" + mc.font.width(c) + " px)").toList());
                showTooltip(mc, can);
            }
            case 550 -> shot(mc, "tooltip-can");
            case 551 -> showTooltip(mc, mc.player.getMainHandItem().copy());
            case 556 -> shot(mc, "tooltip-gun");
            case 560 -> {
                TaczSpray.LOG.info("[selftest] done");
                mc.stop();
            }
            default -> {
            }
        }
    }

    /**
     * In survival: a gun with no finishes takes no can; a spray takes one; then in creative a spray takes none. The
     * player's game mode is put back after.
     */
    private static void spray(ServerPlayer player) {
        ItemStack can = player.getMainHandItem();
        ItemStack gun = player.getOffhandItem();
        GameType mode = player.gameMode.getGameModeForPlayer();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.OFF_HAND, gun(NO_FINISHES));
        can.getItem().finishUsingItem(can, player.level(), player);
        int refused = can.getCount();
        player.setItemInHand(InteractionHand.OFF_HAND, gun);
        can.getItem().finishUsingItem(can, player.level(), player);
        int sprayed = can.getCount();
        player.setGameMode(GameType.CREATIVE);
        can.getItem().finishUsingItem(can, player.level(), player);
        int creative = can.getCount();
        player.setGameMode(mode);
        TaczSpray.LOG.info("[selftest] sprayed: {}", Finish.of(player.getOffhandItem()));
        TaczSpray.LOG.info("[selftest] cans 2 -> {} on a gun with no finishes, {} after a spray, {} after one in "
                + "creative (expect 2, 1, 1)", refused, sprayed, creative);
    }

    /**
     * Labelled cans, in survival: one of the series sprays one of its patterns; one for a single finish sprays that
     * (and its reveal has nothing to scroll past); a series the server lacks, a finish the gun lacks and a damaged label
     * spray nothing and take no can. Then {@code /taczspray give}. The series comes from the test game's
     * {@code config/taczspray/series/}, which the self-test script writes.
     */
    private static void labels(ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        player.setGameMode(GameType.SURVIVAL);
        TaczSpray.LOG.info("[selftest] series loaded: {}", FinishTables.series().keySet());
        TaczSpray.LOG.info("[selftest] series can: {}", sprayWith(player, CanLabel.ofSeries(SERIES), "tacz:ak47"));
        TaczSpray.LOG.info("[selftest] missing series: {}", sprayWith(player, CanLabel.ofSeries("no_such_series"),
                "tacz:ak47"));
        TaczSpray.LOG.info("[selftest] finish the gun lacks: {}", sprayWith(player, CanLabel.ofLook("gold"),
                "tacz:m4a1"));
        ItemStack damaged = new ItemStack(ModItems.SPRAY_CAN.get());
        damaged.getOrCreateTag().putString(CanLabel.TAG, "not a label");
        TaczSpray.LOG.info("[selftest] damaged label: {}", sprayWith(player, damaged, "tacz:ak47"));
        TaczSpray.LOG.info("[selftest] one-finish can: {}", sprayWith(player, CanLabel.ofLook("gold"), "tacz:ak47"));
        player.setGameMode(mode);
        player.getInventory().clearContent();
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),
                "taczspray give " + player.getGameProfile().getName() + " series " + SERIES + " 20");
        int given = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(ModItems.SPRAY_CAN.get()) && CanLabel.of(s).map(l -> SERIES.equals(l.series())).orElse(false)) {
                given += s.getCount();
            }
        }
        TaczSpray.LOG.info("[selftest] /taczspray give ... 20: {} labelled cans in the inventory (expect 20)", given);
    }

    /**
     * The server owner's files, which the self-test script writes: settings.toml (spray cans not craftable, only the
     * two least worn bands), finishes/ (one kept out of plain cans, one broken). Ends holding an AK-47 sprayed with
     * a can of the owner's finish.
     */
    private static void owner(ServerPlayer player) {
        var recipes = player.server.getRecipeManager();
        TaczSpray.LOG.info("[selftest] recipes: spray can {}, paint remover {} (expect false, true)",
                recipes.byKey(TaczSpray.id("spray_can")).isPresent(),
                recipes.byKey(TaczSpray.id("paint_remover")).isPresent());
        TaczSpray.LOG.info("[selftest] custom finishes loaded: {}", FinishTables.custom().keySet());
        FinishTables.Plain plain = FinishTables.plain();
        int kept = 0;
        double worst = 0;
        for (int i = 0; i < 500; i++) {
            Finish f = FinishTables.current().roll("tacz:ak47", plain.pool(), player.getRandom()::nextDouble)
                    .orElseThrow();
            kept += f.look().equals(KEPT) ? 1 : 0;
            worst = Math.max(worst, f.wear());
        }
        TaczSpray.LOG.info("[selftest] 500 plain cans: {} gave {} (expect 0); most worn {} (expect under 0.15)", kept,
                KEPT, worst);
        GameType mode = player.gameMode.getGameModeForPlayer();
        player.setGameMode(GameType.SURVIVAL);
        TaczSpray.LOG.info("[selftest] custom finish can: {}", sprayWith(player, CanLabel.ofLook(CUSTOM), "tacz:ak47"));
        player.setGameMode(mode);
        ItemStack gun = player.getOffhandItem().copy();
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.getInventory().setItem(0, gun);
        player.getInventory().selected = 0;
    }

    private static String sprayWith(ServerPlayer player, CanLabel label, String gunId) {
        ItemStack can = new ItemStack(ModItems.SPRAY_CAN.get());
        label.writeTo(can);
        return sprayWith(player, can, gunId);
    }

    /** One spray with this can on a fresh gun: what the gun got, and whether the can was used up. */
    private static String sprayWith(ServerPlayer player, ItemStack can, String gunId) {
        player.setItemInHand(InteractionHand.MAIN_HAND, can);
        player.setItemInHand(InteractionHand.OFF_HAND, gun(gunId));
        can.getItem().finishUsingItem(can, player.level(), player);
        Finish f = Finish.of(player.getOffhandItem());
        return (f == null ? "nothing" : f.look() + " (" + f.id() + ")") + ", can " + (can.isEmpty() ? "used up" : "kept");
    }

    private static void setUp(ServerPlayer player) {
        // the test world is reused: clear the guns earlier runs left, and see in the dark
        player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32))
                .forEach(ItemEntity::discard);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60, 0, false, false));
        String gunId = System.getProperty("taczspray.selftest.gun", "tacz:ak47");
        player.getInventory().setItem(0, new ItemStack(ModItems.SPRAY_CAN.get(), 2));
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.OFF_HAND, gun(gunId));
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        for (int i = 0; i < 2; i++) {
            ItemStack lying = gun(gunId);
            FinishTables.current().roll(gunId, player.getRandom()::nextDouble).ifPresent(f -> f.writeTo(lying));
            double side = i == 0 ? 0.5 : -0.5;
            drop(player, lying, look.scale(1.2).add(look.z * side, 0, -look.x * side));
        }
        TaczSpray.LOG.info("[selftest] set up with {} finishes loaded", FinishTables.current().size());
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> work) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            TaczSpray.LOG.error("[selftest] needs a singleplayer world");
            return;
        }
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                work.accept(player);
            }
        });
    }

    /** A screen with nothing on it but this item's tooltip. */
    private static void showTooltip(Minecraft mc, ItemStack stack) {
        mc.setScreen(new Screen(Component.empty()) {
            @Override
            public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
                renderBackground(g);
                g.renderTooltip(font, stack, 4, 20);
            }
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "taczspray-selftest-" + name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private static ItemStack gun(String gunId) {
        return DevGuns.make(gunId);
    }

    private static void drop(ServerPlayer player, ItemStack stack, Vec3 offset) {
        ItemEntity item = new ItemEntity(player.level(), player.getX() + offset.x, player.getY() + 0.2,
                player.getZ() + offset.z, stack, 0, 0, 0);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        player.level().addFreshEntity(item);
    }
}
