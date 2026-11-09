package com.endercore;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * 持续控制层。
 *
 * 为什么需要它：直接写 mc.player.input.movementForward 只在单个 tick 有效，
 * 下一个 tick 会被 KeyboardInput.tick() 按真实按键状态重算并清零。
 * 所以这里维护"移动目标"和"挖掘目标"，每个 tick 由 Fabric 事件持续施加：
 *   1) 把玩家朝向目标点；
 *   2) sprintKey 疾跑 + forwardKey 持续前进；
 *   3) 被 1 格障碍卡住时自动按跳；
 *   4) 每 tick 持续挖掘（第一 tick attackBlock，后续 updateBlockBreakingProgress）；
 *   5) 视线被树叶/方块挡住时自动先挖挡路方块。
 */
public final class PlayerController {
    private static Double targetX = null;
    private static Double targetZ = null;
    private static boolean roughMode = false;
    private static boolean swimUp = false;
    private static boolean pillarMode = false;
    private static boolean bridgeMode = false;
    private static int pillarTargetY = -1;
    private static float bridgeYaw = 0;
    private static double pillarBaseY = 0;
    private static BlockPos pillarSupport = null;
    private static int pillarTicks = 0;
    private static Integer followEntityId = null;
    private static BlockPos miningTarget = null;
    private static BlockPos miningStartedPos = null;

    /** 卡住检测：记录上一 tick 位置，连续多 tick 没横向位移就视为被挡住 */
    private static double lastX = 0;
    private static double lastZ = 0;
    private static int stuckTicks = 0;

    private static final double REACH = 5.0;

    private PlayerController() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(PlayerController::onEndTick);
    }

    private static void onEndTick(MinecraftClient mc) {
        if (mc.player == null || mc.options == null) {
            return;
        }

        // ---- 移动：疾跑 + 自动跳障 ----
        boolean moving = false;
        if (targetX != null) {
            double dx = targetX - mc.player.getX();
            double dz = targetZ - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            mc.player.setYaw((float) yaw);
            double arriveDist = roughMode ? 1.0 : 0.2; boolean arrived = dist <= arriveDist;
            moving = !arrived;
            mc.options.forwardKey.setPressed(moving);
            mc.options.sprintKey.setPressed(moving && (roughMode || dist > 2.0));
            if (arrived) {
                targetX = null;
                targetZ = null;
                mc.options.forwardKey.setPressed(false);
                mc.options.sprintKey.setPressed(false);
                bridgeMode = false;
        }

        }
        // ---- 追踪生物：每 tick 更新目标为生物当前位置 ----
        if (followEntityId != null) {
            net.minecraft.entity.Entity target = null;
            for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
                if (e.getId() == followEntityId) { target = e; break; }
            }
            if (target == null) {
                followEntityId = null;
            } else {
                double fdx = target.getX() - mc.player.getX();
                double fdz = target.getZ() - mc.player.getZ();
                double fdist = Math.sqrt(fdx * fdx + fdz * fdz);
                if (fdist > 2.5) {
                    targetX = target.getX();
                    targetZ = target.getZ();
                    roughMode = true;
                } else {
                    followEntityId = null;
                    targetX = null;
                    targetZ = null;
                    mc.options.forwardKey.setPressed(false);
                    mc.options.sprintKey.setPressed(false);
                }
            }
        }
        // 卡住检测：横向位移 < 0.06 格/ tick 就累计
        if (moving) {
            double moved = Math.hypot(mc.player.getX() - lastX, mc.player.getZ() - lastZ);
            if (moved < 0.06) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
            // 被 1 格障碍卡住时自动跳
            if (stuckTicks >= 6) {
                mc.options.jumpKey.setPressed(true);
            }
        } else {
            stuckTicks = 0;
        }
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();

        // ---- 溺水自救：持续按跳跃键上浮，出水面后自动停止 ----
        if (swimUp) {
            mc.options.jumpKey.setPressed(mc.player.isSubmergedInWater());
            if (!mc.player.isSubmergedInWater()) {
                swimUp = false;
                mc.options.jumpKey.setPressed(false);
            }
        }


        // ---- 连续搭柱向上：按住跳，看脚下，用游戏自带 crosshairTarget 右键放方块 ----
        // ---- 连续搭柱向上：按住跳，记住脚下方块，持续点它的顶面 ----
        if (pillarMode) {
            pillarTicks++;
            mc.player.setPitch(90f);
            mc.options.jumpKey.setPressed(true);
            if (pillarTargetY > 0 && mc.player.getY() >= pillarTargetY) {
                pillarMode = false; mc.options.jumpKey.setPressed(false);
            } else {
                if (pillarSupport == null) pillarSupport = mc.player.getBlockPos().down();
                BlockPos above = pillarSupport.up();
                if (!mc.world.getBlockState(above).isAir()) {
                    pillarSupport = above;
                } else if (!mc.world.getBlockState(pillarSupport).isAir()) {
                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pillarSupport).add(0, 0.5, 0), Direction.UP, pillarSupport, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
                if (pillarTicks > 200) { pillarMode = false; mc.options.jumpKey.setPressed(false); }
            }
        }
        // ---- 连续搭桥：潜行，直接构造脚下方块侧面的点击，往前走 ----
        // ---- 连续搭桥：直接构造脚下方块侧面的点击，不移动 ----
        if (bridgeMode) {
            BlockPos below = mc.player.getBlockPos().down();
            float yaw = mc.player.getYaw();
            double rad = Math.toRadians(yaw);
            int dx = (int)Math.round(-Math.sin(rad));
            int dz = (int)Math.round(Math.cos(rad));
            BlockPos placeTarget = below.add(dx, 0, dz);
            if (mc.world.getBlockState(placeTarget).isAir()) {
                Direction side = Direction.getFacing(dx, 0, dz);
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(below).add(side.getOffsetX()*0.5, side.getOffsetY()*0.5, side.getOffsetZ()*0.5), side, below, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
        if (miningTarget != null) {
            if (mc.world == null) {
            } else if (mc.world.getBlockState(miningTarget).isAir()) {
                stopMining(mc);
            } else {
                boolean creative = mc.player.getAbilities().creativeMode;
                BlockPos effective = resolveMiningTarget(mc, miningTarget);
                if (effective != null) {
                    if (mc.world.getBlockState(effective).isAir()) {
                        miningStartedPos = null;
                    } else if (creative) {
                        mc.interactionManager.attackBlock(effective, Direction.UP);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    } else if (miningStartedPos == null || !miningStartedPos.equals(effective)) {
                        mc.interactionManager.attackBlock(effective, Direction.UP);
                        miningStartedPos = effective;
                    } else {
                        mc.interactionManager.updateBlockBreakingProgress(effective, Direction.UP);
                    }
                }
            }
        }
    }

    /**
     * 从玩家眼睛向目标方块中心做射线：
     *   - 太远（>REACH）返回 null，等待走近；
     *   - 射线第一个命中的就是目标，返回目标本身；
     *   - 否则返回挡路的方块（树叶等），先挖它。
     */
    private static BlockPos resolveMiningTarget(MinecraftClient mc, BlockPos target) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(target);
        if (eye.distanceTo(center) > REACH) {
            return null;
        }
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, center,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        if (hit.getType() == HitResult.Type.MISS) {
            return target;
        }
        if (hit.getBlockPos().equals(target)) {
            return target;
        }
        return hit.getBlockPos();
    }

    private static void stopMining(MinecraftClient mc) {
        miningTarget = null;
        miningStartedPos = null;
        if (mc.interactionManager != null) {
            try {
                mc.interactionManager.cancelBlockBreaking();
            } catch (Exception ignored) {
            }
        }
    }

    public static void setMiningTarget(BlockPos pos) {
        miningTarget = pos;
    }

    public static void swimUp() {
        swimUp = true;
    }

    public static void startBridge() { bridgeMode = true; targetX = null; targetZ = null; MinecraftClient m = MinecraftClient.getInstance(); if (m.player != null) bridgeYaw = m.player.getYaw() + 180f; }
    public static void startFollow(int entityId) { followEntityId = entityId; roughMode = true; }
    public static void startPillar(Integer targetY) { pillarMode = true; pillarTicks = 0; pillarTargetY = targetY != null ? targetY : -1; pillarSupport = null; MinecraftClient mc = MinecraftClient.getInstance(); if (mc.player != null) pillarBaseY = mc.player.getY(); }
    public static void moveTowards(double x, double z) { moveTowards(x, z, false); }
    public static void moveTowards(double x, double z, boolean rough) {
        targetX = x;
        targetZ = z;
        roughMode = rough;
    }

    public static void clearMovement() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            clearMovement(mc);
        }
    }

    private static void clearMovement(MinecraftClient mc) {
        targetX = null;
        targetZ = null;
        swimUp = false;
        stopMining(mc);
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
        }
    }
}
