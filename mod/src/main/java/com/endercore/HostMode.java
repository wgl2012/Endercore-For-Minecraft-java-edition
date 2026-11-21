package com.endercore;

import net.minecraft.client.MinecraftClient;

/**
 * 后台托管模式（方案 A + B 的状态持有者）。
 *
 * 方案 A：不打开任何 Screen，直接解锁鼠标光标。
 *         原版只在"界面打开期间"清零移动输入，因此不挂界面后，
 *         AI 的走路 / 跟随 / 搭桥搭柱不会被挡住。
 * 方案 B：配合 {@code MinecraftClientMixin}，托管期间强制 {@code isPaused()=false}，
 *         防止后台切窗或暂停界面把世界 / 客户端 tick 卡住。
 */
public final class HostMode {
    /** 是否处于后台托管状态。Mixin 读取该字段决定是否强制不暂停。 */
    public static boolean active = false;

    /** 进入托管前保存的原版「失焦自动暂停」开关，退出时还原。 */
    private static boolean savedPauseOnLostFocus = false;

    private HostMode() {
    }

    public static void toggle(MinecraftClient mc) {
        if (active) {
            exit(mc);
        } else {
            enter(mc);
        }
    }

    public static void enter(MinecraftClient mc) {
        active = true;
        AiHud.hostActive = true;
        if (mc.mouse != null) {
            mc.mouse.unlockCursor();
        }
        // 关闭原版「窗口失焦超过 500ms 自动弹出暂停菜单」行为
        if (mc.options != null) {
            savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;
        }
        AiHud.sendChat("[EnderCore] 后台托管已开启：鼠标已释放，切窗不再自动暂停。按 N 退出托管。");
    }

    public static void exit(MinecraftClient mc) {
        active = false;
        AiHud.hostActive = false;
        if (mc.mouse != null) {
            mc.mouse.lockCursor();
        }
        if (mc.options != null) {
            mc.options.pauseOnLostFocus = savedPauseOnLostFocus;
        }
        AiHud.sendChat("[EnderCore] 已退出后台托管，鼠标已锁定。");
    }

    /**
     * 每 tick 保活：托管中且未打开任何界面时，若光标被点击等操作重新锁定，则再次解锁；
     * 同时持续压制「失焦自动暂停」开关，避免被其他逻辑改回。
     */
    public static void tick(MinecraftClient mc) {
        if (!active || mc.mouse == null) {
            return;
        }
        if (mc.currentScreen == null && mc.mouse.isCursorLocked()) {
            mc.mouse.unlockCursor();
        }
        if (mc.options != null && mc.options.pauseOnLostFocus) {
            mc.options.pauseOnLostFocus = false;
        }
    }
}
