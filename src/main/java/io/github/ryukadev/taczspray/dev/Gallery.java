package io.github.ryukadev.taczspray.dev;

import io.github.ryukadev.taczspray.Finish;
import io.github.ryukadev.taczspray.FinishTable;
import io.github.ryukadev.taczspray.FinishTables;
import io.github.ryukadev.taczspray.TaczSpray;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Development check, off unless the game is started with {@code -Dtaczspray.gallery=true}: in a singleplayer world,
 * with no input, photographs every gun the finish table paints. For each gun, the commonest and the rarest grade it
 * has, each at its least wear, hung in an item frame in front of a stone wall; then the rarest one in hand. On TaCZ
 * releases with the gun display case, one painted gun in a case last (finishes are not drawn there yet). Screenshots
 * are {@code taczspray-gallery-<n>-<gun>-<shot>.png}; then it quits. {@code -Dtaczspray.gallery.guns=<id>,<id>} shoots only
 * those guns; {@code -Dtaczspray.gallery.plain=true} leaves them unpainted, to tell the mod's doing from TaCZ's own;
 * {@code -Dtaczspray.gallery.looks=<a>,<b>} hangs pattern a and pattern b instead (guns without both are skipped).
 * Not part of the mod's behaviour.
 */
public final class Gallery {
    public static final String PROPERTY = "taczspray.gallery";
    private static final int SETTLE = 60;
    private static final int PER_GUN = 90;
    private static final ResourceLocation DISPLAY_CASE = ResourceLocation.tryBuild("tacz", "gun_display_case");

    private final List<Gun> guns = new ArrayList<>();
    private int ticks = -1;
    /** Set last by the server-side set-up, so a client tick that sees it also sees the guns and the facing. */
    private volatile BlockPos stand;
    private Direction facing;

    private record Gun(String id, FinishTable.Entry low, FinishTable.Entry high) {
    }

    private Gallery() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void start() {
        MinecraftForge.EVENT_BUS.addListener(new Gallery()::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.level == null) {
            return;
        }
        ticks++;
        if (ticks == SETTLE) {
            onServer(mc, this::setUp);
            return;
        }
        if (ticks < SETTLE + 20 || stand == null) {
            return;
        }
        int step = ticks - SETTLE - 20;
        int index = step / PER_GUN;
        int at = step % PER_GUN;
        if (index < guns.size()) {
            Gun gun = guns.get(index);
            String name = String.format("%02d-%s", index + 1, gun.id().replace("tacz:", ""));
            switch (at) {
                case 0 -> {
                    mc.options.hideGui = true;
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    onServer(mc, player -> show(player, gun, gun.low()));
                }
                case 25 -> shot(mc, name + "-frame-low");
                case 30 -> onServer(mc, player -> show(player, gun, gun.high()));
                case 55 -> shot(mc, name + "-frame-high");
                case 56 -> {
                    onServer(mc, player -> clearFrames(player.serverLevel()));
                    mc.options.hideGui = false;
                }
                case 85 -> shot(mc, name + "-hand");
                default -> {
                }
            }
            return;
        }
        int after = step - guns.size() * PER_GUN;
        if (after == 0) {
            onServer(mc, this::displayCase);
        } else if (after == 40) {
            mc.options.hideGui = true;
            shot(mc, "99-display-case");
        } else if (after == 45) {
            TaczSpray.LOG.info("[gallery] done, {} guns", guns.size());
            mc.options.hideGui = false;
            mc.stop();
        }
    }

    private void setUp(ServerPlayer player) {
        Map<String, List<FinishTable.Entry>> byGun = new LinkedHashMap<>();
        for (FinishTable.Entry e : FinishTables.current().entries()) {
            byGun.computeIfAbsent(e.gun(), g -> new ArrayList<>()).add(e);
        }
        String only = System.getProperty("taczspray.gallery.guns", "");
        if (!only.isBlank()) {
            byGun.keySet().retainAll(List.of(only.split(",")));
        }
        String[] looks = System.getProperty("taczspray.gallery.looks", "").split(",");
        byGun.forEach((gun, entries) -> {
            if (looks.length == 2) {
                look(entries, looks[0]).ifPresent(a -> look(entries, looks[1]).ifPresent(b -> guns.add(new Gun(gun, a, b))));
            } else {
                guns.add(new Gun(gun,
                        entries.stream().min(Comparator.comparingInt(FinishTable.Entry::grade)).orElseThrow(),
                        entries.stream().max(Comparator.comparingInt(FinishTable.Entry::grade)).orElseThrow()));
            }
        });

        ServerLevel level = player.serverLevel();
        level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32)).forEach(Entity::discard);
        clearFrames(level);
        level.setDayTime(6000);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 30, 0, false, false));
        player.getInventory().clearContent();

        // a 3x3 stone wall two blocks ahead, the air in front of it cleared; the camera looks straight at its middle
        facing = player.getDirection();
        BlockPos stand = player.blockPosition();
        Direction side = facing.getClockWise();
        for (int up = 0; up <= 2; up++) {
            for (int s = -1; s <= 1; s++) {
                BlockPos row = stand.above(up).relative(side, s);
                level.setBlockAndUpdate(row.relative(facing, 2), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(row.relative(facing, 1), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(row, Blocks.AIR.defaultBlockState());
            }
        }
        this.stand = stand;
        lookAtWall(player);
        TaczSpray.LOG.info("[gallery] {} guns, wall facing {}", guns.size(), facing);
    }

    private void show(ServerPlayer player, Gun gun, FinishTable.Entry entry) {
        ServerLevel level = player.serverLevel();
        clearFrames(level);
        ItemStack painted = painted(gun.id(), entry);
        ItemFrame frame = new ItemFrame(level, stand.above(1).relative(facing, 1), facing.getOpposite());
        frame.setInvisible(true);
        frame.setItem(painted.copy(), false);
        level.addFreshEntity(frame);
        player.setItemInHand(InteractionHand.MAIN_HAND, painted(gun.id(), gun.high()));
        lookAtWall(player);
        TaczSpray.LOG.info("[gallery] {}: {} (grade {})", gun.id(), entry.id(), entry.grade());
    }

    private static Optional<FinishTable.Entry> look(List<FinishTable.Entry> entries, String look) {
        return entries.stream().filter(e -> e.look().equals(look)).findFirst();
    }

    private void lookAtWall(ServerPlayer player) {
        Vec3 at = Vec3.atBottomCenterOf(stand).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.45));
        player.connection.teleport(at.x, at.y, at.z, facing.toYRot(), 0f);
    }

    private void clearFrames(ServerLevel level) {
        if (stand != null) {
            level.getEntitiesOfClass(ItemFrame.class, new AABB(stand).inflate(4)).forEach(Entity::discard);
        }
    }

    /** One painted gun in a TaCZ gun display case, where the release has one. */
    private void displayCase(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        clearFrames(level);
        player.getInventory().clearContent();
        Block block = ForgeRegistries.BLOCKS.getValue(DISPLAY_CASE);
        if (block == null || block == Blocks.AIR || guns.isEmpty()) {
            TaczSpray.LOG.info("[gallery] no display case in this TaCZ release");
            return;
        }
        BlockPos at = stand.relative(facing, 1);
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getOpposite());
        }
        level.setBlockAndUpdate(at, state);
        BlockEntity entity = level.getBlockEntity(at);
        Gun gun = guns.get(0);
        try {
            Object placed = entity.getClass().getMethod("setItem", int.class, ItemStack.class)
                    .invoke(entity, 0, painted(gun.id(), gun.high()));
            TaczSpray.LOG.info("[gallery] display case holds {}: {}", gun.id(), placed);
        } catch (ReflectiveOperationException | RuntimeException e) {
            TaczSpray.LOG.error("[gallery] could not fill the display case", e);
        }
        player.connection.teleport(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, facing.toYRot(), 50f);
    }

    private static ItemStack painted(String gunId, FinishTable.Entry entry) {
        ItemStack stack = DevGuns.make(gunId);
        if (!Boolean.getBoolean("taczspray.gallery.plain")) {
            new Finish(entry.id(), entry.look(), entry.grade(), 1, entry.wearMin(), 500, 500).writeTo(stack);
        }
        return stack;
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> work) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            TaczSpray.LOG.error("[gallery] needs a singleplayer world");
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

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "taczspray-gallery-" + name + ".png", mc.getMainRenderTarget(), c -> {});
    }
}
