package com.endercore;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * EnderCore 游戏内悬浮窗。
 * 悬浮窗只展示：状态 / 当前动作 / 剩余 Token。
 * 深度思考文本不再上屏，改由聊天框输出（见 sendChat）。
 */
public class AiHud {
    public static String action = "";
    public static String status = "待机";
    public static String goal = "";
    public static String balance = "—";
    public static boolean hostActive = false;
    public static long lastUpdate = 0;

    public static void init() {
        HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            if (mc.player == null) return;

            TextRenderer tr = mc.textRenderer;
            int x = 4, y = 4, w = 220, p = 3;

            ctx.fill(x, y, x + w, y + 59, 0xC01a1a2e);
            int cy = y + p;
            ctx.drawText(tr, "EnderCore AI", x + p, cy, 0x9d5cff, true);
            cy += 12;
            ctx.drawText(tr, "Host: " + (hostActive ? "后台托管中" : "手动"), x + p, cy,
                    hostActive ? 0x00ff00 : 0xaaaaaa, true);
            cy += 11;
            ctx.drawText(tr, "Status: " + status, x + p, cy, 0x00ff00, true);
            cy += 11;
            String a = action; if (a.length() > 30) a = a.substring(0, 30) + "...";
            ctx.drawText(tr, "Action: " + a, x + p, cy, 0xffcc00, true);
            cy += 11;
            ctx.drawText(tr, "Token 剩余: " + balance, x + p, cy, 0x55ccff, true);
        });
    }

    public static void setAction(String a) { action = a; lastUpdate = System.currentTimeMillis(); }
    public static void setStatus(String s) { status = s; lastUpdate = System.currentTimeMillis(); }
    public static void setBalance(String b) { balance = b == null || b.isEmpty() ? "—" : b; lastUpdate = System.currentTimeMillis(); }

    /** 把深度思考文本发送到游戏内聊天框（切回主线程执行）。 */
    public static void sendChat(String message) {
        if (message == null || message.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal(message), false);
            }
        });
    }
}
