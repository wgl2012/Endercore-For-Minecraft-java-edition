package com.endercore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.block.BlockState;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 娓告垙鏁版嵁閲囬泦鍣?+ 鍔ㄤ綔鎵ц鍣ㄣ€? * 鎵€鏈夊澶栨柟娉曢兘蹇呴』鍥炲埌涓荤嚎绋嬫墽琛岋紙HTTP 绾跨▼涓嶈兘鐩存帴璇绘父鎴忓璞★級銆? */
public class GameDataCollector {

    private static final long TIMEOUT_MS = 2000L;
    private static final double SCAN_RANGE = 32.0;
    private static final double ATTACK_REACH = 5.0;

    /** 鍊煎緱鏍囨敞鐨勯檮杩戞柟鍧楋紙LLM 鍙洿鎺ヨ蛋杩囧幓鎸?鐢級 */
    private static final Set<String> BLOCK_WATCH = Set.of(
            "minecraft:coal_ore", "minecraft:iron_ore", "minecraft:copper_ore",
            "minecraft:gold_ore", "minecraft:redstone_ore", "minecraft:diamond_ore",
            "minecraft:emerald_ore",
            "minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log",
            "minecraft:dark_oak_log", "minecraft:acacia_log", "minecraft:jungle_log",
            "minecraft:crafting_table", "minecraft:furnace", "minecraft:chest",
            "minecraft:blast_furnace", "minecraft:smoker", "minecraft:villager"
    );

    // ---------- 绾跨▼瀹夊叏鍏ュ彛 ----------

    public static JsonObject getGameStateBlocking() {
        return onMainThread(GameDataCollector::getGameState);
    }

    public static JsonObject scanBlocksBlocking() {
        return onMainThread(GameDataCollector::scanBlocks);
    }

    private static JsonObject onMainThread(java.util.concurrent.Callable<JsonObject> task) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "璇锋眰瓒呮椂: " + e.getClass().getSimpleName());
            return err;
        }
    }

    public static boolean executeActionBlocking(String action, JsonObject params) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(executeAction(action, params));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 鏁版嵁閲囬泦锛堜富绾跨▼锛?----------

    private static String gameModeName(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return "unknown";
        try {
            net.minecraft.world.GameMode mode = mc.interactionManager.getCurrentGameMode();
            return mode.asString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static JsonObject getGameState() {
        JsonObject state = new JsonObject();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null) {
            state.addProperty("error", "游戏未加载世界");
            return state;
        }

        PlayerEntity player = mc.player;
        JsonObject playerJson = new JsonObject();
        playerJson.addProperty("name", player.getName().getString());
        playerJson.addProperty("health", Math.round(player.getHealth() * 10) / 10.0);
        playerJson.addProperty("max_health", Math.round(player.getMaxHealth() * 10) / 10.0);
        playerJson.addProperty("x", player.getX());
        playerJson.addProperty("y", player.getY());
        playerJson.addProperty("z", player.getZ());
        playerJson.addProperty("yaw", player.getYaw());
        playerJson.addProperty("pitch", player.getPitch());
        playerJson.addProperty("food", player.getHungerManager().getFoodLevel());
        playerJson.addProperty("air", player.getAir());
        playerJson.addProperty("max_air", player.getMaxAir());
        playerJson.addProperty("in_water", player.isSubmergedInWater());
        playerJson.addProperty("game_mode", gameModeName(mc));
        state.add("player", playerJson);

        // 游戏内输入的 AI 目标
        if (AiHud.goal != null && !AiHud.goal.isEmpty()) state.addProperty("goal", AiHud.goal);
        // 闄勮繎鐢熺墿
        JsonArray mobs = new JsonArray();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == player) continue;
            if (!(entity instanceof LivingEntity)) continue;

            double dist = entity.distanceTo(player);
            // 生物数据始终同步，不限制距离（只遍历已加载实体）

            JsonObject mob = new JsonObject();
            mob.addProperty("id", entity.getId());
            mob.addProperty("name", entity.getType().toString());
            mob.addProperty("x", entity.getX());
            mob.addProperty("y", entity.getY());
            mob.addProperty("z", entity.getZ());
            mob.addProperty("distance", Math.round(dist * 100) / 100.0);
            mob.addProperty("hostile", entity instanceof HostileEntity);
            mob.addProperty("health", Math.round(((LivingEntity) entity).getHealth() * 10) / 10.0);
            mobs.add(mob);
        }
        state.add("nearby_mobs", mobs);

        // 交互范围内的方块（5格），自动同步给 LLM
        JsonArray reachBlocks = new JsonArray();
        BlockPos center = player.getBlockPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos bp = center.add(dx, dy, dz);
                    BlockState bs = mc.world.getBlockState(bp);
                    if (bs.isAir()) continue;
                    double d = Math.sqrt(dx*dx + dy*dy + dz*dz);
                    if (d > 5.0) continue;
                    JsonObject b = new JsonObject();
                    b.addProperty("x", bp.getX());
                    b.addProperty("y", bp.getY());
                    b.addProperty("z", bp.getZ());
                    b.addProperty("name", Registries.BLOCK.getId(bs.getBlock()).toString());
                    reachBlocks.add(b);
                }
            }
        }
        state.add("reachable_blocks", reachBlocks);

        // 鑳屽寘
        state.add("inventory", readInventory(mc));
        state.addProperty("selected_slot", mc.player.getInventory().selectedSlot);

        state.addProperty("time_of_day", mc.world.getTimeOfDay());
        state.addProperty("is_day", mc.world.isDay());

        return state;
    }

    private static JsonArray readInventory(MinecraftClient mc) {
        JsonArray arr = new JsonArray();
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            JsonObject it = new JsonObject();
            it.addProperty("slot", i);
            it.addProperty("id", Registries.ITEM.getId(st.getItem()).toString());
            it.addProperty("count", st.getCount());
            arr.add(it);
        }
        return arr;
    }

    /**
     * 鎵弿鐜╁鍛ㄥ洿 9x6x9 鑼冨洿鐨勬柟鍧楋細
     *   counts  姣忕鏂瑰潡鏁伴噺锛堝墠 30锛?     *   nearby  鍏虫敞鍒楄〃锛堢熆鐭?鏈ㄥご/宸ヤ綔鍙?鐔旂倝/绠卞瓙鈥︼級鐨勫潗鏍囷紝鍙洿鎺ヨ蛋杩囧幓鎸?     */
    public static JsonObject scanBlocks() {
        JsonObject out = new JsonObject();
        JsonArray nearby = new JsonArray();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            out.add("counts", new JsonObject());
            out.add("nearby", nearby);
            return out;
        }

        BlockPos center = mc.player.getBlockPos();
        Map<String, Integer> counts = new HashMap<>();

        for (BlockPos pos : BlockPos.iterate(center.add(-4, -2, -4), center.add(4, 3, 4))) {
            BlockState bs = mc.world.getBlockState(pos);
            if (bs.isAir()) continue;
            String id = Registries.BLOCK.getId(bs.getBlock()).toString();
            counts.merge(id, 1, Integer::sum);
            if (BLOCK_WATCH.contains(id)) {
                JsonObject b = new JsonObject();
                b.addProperty("x", pos.getX());
                b.addProperty("y", pos.getY());
                b.addProperty("z", pos.getZ());
                b.addProperty("id", id);
                nearby.add(b);
            }
        }

        JsonObject cnt = new JsonObject();
        counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(30)
                .forEach(e -> cnt.addProperty(e.getKey(), e.getValue()));
        out.add("counts", cnt);
        out.add("nearby", nearby);
        return out;
    }

    // ---------- 鍔ㄤ綔鎵ц锛堜富绾跨▼锛?----------

    public static boolean executeAction(String action, JsonObject params) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return false;
        }

        switch (action) {
            case "swing":
                mc.player.swingHand(mc.player.getActiveHand());
                return true;

            case "stop_move":
                PlayerController.clearMovement();
                return true;

            case "swim_up":
                PlayerController.swimUp();
                return true;

            case "move_to": {
                double tx = params.get("x").getAsDouble();
                double tz = params.get("z").getAsDouble();
                PlayerController.moveTowards(tx, tz, false);
                return true;
            }
            case "move_rough": {
                double tx2 = params.get("x").getAsDouble();
                double tz2 = params.get("z").getAsDouble();
                PlayerController.moveTowards(tx2, tz2, true);
                return true;
            }

            case "look_at": {
                double lx = params.get("x").getAsDouble();
                double ly = params.get("y").getAsDouble();
                double lz = params.get("z").getAsDouble();
                double dx = lx - mc.player.getX();
                double dy = ly - (mc.player.getY() + mc.player.getStandingEyeHeight());
                double dz = lz - mc.player.getZ();
                mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
                mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
                return true;
            }

            case "attack_nearest":
                return attackNearest(mc);

            case "place_block":
                return placeAtCrosshair(mc);

            case "break_block": {
                double bx = params.get("x").getAsDouble();
                double by = params.get("y").getAsDouble();
                double bz = params.get("z").getAsDouble();
                PlayerController.setMiningTarget(new BlockPos((int) Math.floor(bx), (int) Math.floor(by), (int) Math.floor(bz)));
                return true;
            }

            case "select_slot":
                mc.player.getInventory().selectedSlot = params.get("slot").getAsInt();
                return true;

            case "drop_item":
                mc.player.dropSelectedItem(false);
                return true;

            case "use_item":
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                return true;

            case "interact_entity": {
                int eid = params.get("id").getAsInt();
                for (Entity e : mc.world.getEntities()) {
                    if (e.getId() == eid) {
                        double dx = e.getX() - mc.player.getX();
                        double dz = e.getZ() - mc.player.getZ();
                        double dy = e.getY() - mc.player.getEyeY();
                        mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
                        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz))));
                        mc.interactionManager.interactEntityAtLocation(mc.player, e, new net.minecraft.util.hit.EntityHitResult(e, e.getPos()), Hand.MAIN_HAND);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        return true;
                    }
                }
                return false;
            }
            case "craft": {
                String rid = params.get("recipe").getAsString();
                try {
                    Identifier id = Identifier.of("minecraft", rid);
                    java.util.Optional<net.minecraft.recipe.RecipeEntry<?>> recipe =
                            mc.world.getRecipeManager().get(id);
                    if (recipe.isEmpty()) {
                        return false;
                    }
                    int syncId = mc.player.currentScreenHandler.syncId;
                    mc.interactionManager.clickRecipe(syncId, recipe.get(), false);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }

            case "pillar_up": {
                Integer targetY = null; if (params != null && params.has("target_y")) targetY = params.get("target_y").getAsInt(); PlayerController.startPillar(targetY);
            }

            case "bridge_forward": {
                PlayerController.startBridge(); return true;
            }
            case "place_block_at": {
                double bx = params.get("x").getAsDouble(); double by = params.get("y").getAsDouble(); double bz = params.get("z").getAsDouble();
                BlockPos targetPos = new BlockPos((int) bx, (int) by, (int) bz);
                for (Direction d : Direction.values()) {
                    BlockPos neighbor = targetPos.offset(d);
                    BlockState state = mc.world.getBlockState(neighbor);
                    if (state.isAir()) continue;
                    Direction side = d.getOpposite();
                    double hitX = neighbor.getX() + 0.5 + side.getOffsetX() * 0.5;
                    double hitY = neighbor.getY() + 0.5 + side.getOffsetY() * 0.5;
                    double hitZ = neighbor.getZ() + 0.5 + side.getOffsetZ() * 0.5;
                    BlockHitResult hit = new BlockHitResult(new Vec3d(hitX, hitY, hitZ), side, neighbor, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    return true;
                }
                return false;
            }
            case "right_click": {
                if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, (BlockHitResult) mc.crosshairTarget);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    return true;
                }
                return false;
            }
            // ===== 视角控制 =====
            case "look_yaw_pitch": {
                float yaw = params.get("yaw").getAsFloat();
                float pitch = params.has("pitch") ? params.get("pitch").getAsFloat() : mc.player.getPitch();
                mc.player.setYaw(yaw);
                mc.player.setPitch(Math.max(-90, Math.min(90, pitch)));
                return true;
            }
            case "look_delta": {
                float dy = params.get("yaw_delta").getAsFloat();
                float dp = params.has("pitch_delta") ? params.get("pitch_delta").getAsFloat() : 0;
                mc.player.setYaw(mc.player.getYaw() + dy);
                mc.player.setPitch(Math.max(-90, Math.min(90, mc.player.getPitch() + dp)));
                return true;
            }
            case "camera_perspective": {
                int mode = params.get("mode").getAsInt();
                mc.options.setPerspective(net.minecraft.client.option.Perspective.values()[mode]);
                return true;
            }

            // ===== 移动控制 =====
            case "jump": {
                mc.options.jumpKey.setPressed(true); new Thread(() -> { try { Thread.sleep(150); } catch (Exception e) {} mc.options.jumpKey.setPressed(false); }).start();
                return true;
            }
            case "sneak": {
                boolean on = params.get("on").getAsBoolean();
                mc.player.setSneaking(on);
                mc.options.sneakKey.setPressed(on);
                return true;
            }
            case "sprint": {
                boolean on = params.get("on").getAsBoolean();
                mc.options.sprintKey.setPressed(on);
                return true;
            }
            case "forward": {
                boolean on = params.get("on").getAsBoolean();
                mc.options.forwardKey.setPressed(on);
                return true;
            }
            case "back": {
                boolean on = params.get("on").getAsBoolean();
                mc.options.backKey.setPressed(on);
                return true;
            }
            case "left": {
                boolean on = params.get("on").getAsBoolean();
                mc.options.leftKey.setPressed(on);
                return true;
            }
            case "right": {
                boolean on = params.get("on").getAsBoolean();
                mc.options.rightKey.setPressed(on);
                return true;
            }
            case "fly": {
                mc.player.getAbilities().flying = !mc.player.getAbilities().flying;
                return true;
            }
            case "fly_speed": {
                float s = params.get("speed").getAsFloat();
                mc.player.getAbilities().setFlySpeed(s);
                return true;
            }

            // ===== 物品操作 =====
            case "drop_stack": {
                mc.player.dropSelectedItem(true);
                return true;
            }
            case "swap_hands": {
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, mc.player.getBlockPos(), Direction.DOWN));
                return true;
            }
            case "use_offhand": {
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                return true;
            }
            case "hotbar": {
                int slot = params.get("slot").getAsInt();
                mc.player.getInventory().selectedSlot = Math.max(0, Math.min(8, slot));
                return true;
            }

            // ===== 方块交互 =====
            case "right_click_block": {
                double rx = params.get("x").getAsDouble();
                double ry = params.get("y").getAsDouble();
                double rz = params.get("z").getAsDouble();
                BlockPos bp = new BlockPos((int) Math.floor(rx), (int) Math.floor(ry), (int) Math.floor(rz));
                BlockHitResult hit = new BlockHitResult(new Vec3d(rx+0.5, ry+0.5, rz+0.5), Direction.UP, bp, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                return true;
            }

            // ===== 挖掘控制 =====
            case "start_breaking": {
                double bx2 = params.get("x").getAsDouble();
                double by2 = params.get("y").getAsDouble();
                double bz2 = params.get("z").getAsDouble();
                PlayerController.setMiningTarget(new BlockPos((int) Math.floor(bx2), (int) Math.floor(by2), (int) Math.floor(bz2)));
                return true;
            }
            case "stop_breaking": {
                PlayerController.setMiningTarget(null);
                mc.interactionManager.cancelBlockBreaking();
                return true;
            }

            // ===== 游戏内交互 =====
            case "close_screen": {
                mc.player.closeScreen();
                return true;
            }
            case "chat": {
                String msg = params.get("message").getAsString();
                mc.player.networkHandler.sendChatMessage(msg);
                return true;
            }
            // screenshot removed

            // ===== 战斗 =====
            case "attack_entity": {
                int eid2 = params.get("id").getAsInt();
                for (Entity e : mc.world.getEntities()) {
                    if (e.getId() == eid2 && e instanceof LivingEntity) {
                        mc.interactionManager.attackEntity(mc.player, e);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        return true;
                    }
                }
                return false;
            }
            case "attack": {
                mc.player.swingHand(Hand.MAIN_HAND);
                return true;
            }
            // ===== 功能方块交互（打开工作台/箱子/熔炉等界面）=====
            case "open_block": {
                double ox = params.get("x").getAsDouble();
                double oy = params.get("y").getAsDouble();
                double oz = params.get("z").getAsDouble();
                BlockPos bp = new BlockPos((int) Math.floor(ox), (int) Math.floor(oy), (int) Math.floor(oz));
                BlockHitResult hit = new BlockHitResult(new Vec3d(ox+0.5, oy+0.5, oz+0.5), Direction.UP, bp, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                return true;
            }

            // ===== 容器界面操作 =====
            case "click_slot": {
                int slot = params.get("slot").getAsInt();
                int button = params.has("button") ? params.get("button").getAsInt() : 0;
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, button, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                return true;
            }
            case "shift_click_slot": {
                int slot = params.get("slot").getAsInt();
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
            case "click_slot_hotbar": {
                int slot = params.get("slot").getAsInt();
                int hotbar = params.get("hotbar").getAsInt();
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, hotbar, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                return true;
            }
            case "drop_cursor": {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, -999, 0, net.minecraft.screen.slot.SlotActionType.THROW, mc.player);
                return true;
            }

            // ===== 生物交互 =====
            case "interact_entity_at": {
                double ex = params.get("x").getAsDouble();
                double ey = params.get("y").getAsDouble();
                double ez = params.get("z").getAsDouble();
                for (Entity e : mc.world.getEntities()) {
                    if (e.squaredDistanceTo(ex, ey, ez) < 2.0) {
                        mc.interactionManager.interactEntity(mc.player, e, Hand.MAIN_HAND);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        return true;
                    }
                }
                return false;
            }
            case "ride_entity": {
                int eid4 = params.get("id").getAsInt();
                for (Entity e : mc.world.getEntities()) {
                    if (e.getId() == eid4) {
                        e.startRiding(mc.player, true);
                        return true;
                    }
                }
                return false;
            }
            case "dismount": {
                mc.player.stopRiding();
                return true;
            }

            // ===== 游戏模式 =====
            case "set_gamemode": {
                int mode = params.get("mode").getAsInt();
                mc.player.networkHandler.sendChatMessage("/gamemode " + mode);
                return true;
            }
            case "time_set": {
                long t = params.get("time").getAsLong();
                mc.player.networkHandler.sendChatMessage("/time set " + t);
                return true;
            }
            case "weather": {
                String w = params.get("type").getAsString();
                mc.player.networkHandler.sendChatMessage("/weather " + w);
                return true;
            }
            // ===== 追踪生物 =====
            case "go_to_entity": {
                int eid = params.get("id").getAsInt();
                PlayerController.startFollow(eid);
                return true;
            }
            // ===== 交易选择 =====
            case "select_trade": {
                int idx = params.get("index").getAsInt();
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket(idx));
                return true;
            }
            default:
                if (ActionRegistry.has(action)) {
                    return ActionRegistry.execute(action, params);
                }
                return false;
        }

    }
    private static boolean attackNearest(MinecraftClient mc) {
        Box area = mc.player.getBoundingBox().expand(SCAN_RANGE);
        List<HostileEntity> mobs = mc.world.getEntitiesByClass(HostileEntity.class, area, HostileEntity::isAlive);

        HostileEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (HostileEntity e : mobs) {
            double d = e.distanceTo(mc.player);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        if (nearest == null) {
            return false;
        }

        double dx = nearest.getX() - mc.player.getX();
        double dy = nearest.getEyeY() - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double dz = nearest.getZ() - mc.player.getZ();
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        if (best <= ATTACK_REACH) {
            mc.interactionManager.attackEntity(mc.player, nearest);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }

        PlayerController.moveTowards(nearest.getX(), nearest.getZ());
        return true;
    }

    private static boolean placeAtCrosshair(MinecraftClient mc) {
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) mc.crosshairTarget;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }
}
