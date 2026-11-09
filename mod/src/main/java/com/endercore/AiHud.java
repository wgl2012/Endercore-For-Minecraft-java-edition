package com.endercore;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class AiHud {
    public static String thought = "";
    public static String action = "";
    public static String status = "待机";
    public static String goal = "";
    public static long lastUpdate = 0;

    public static void init() {
        HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            if (mc.player == null) return;

            TextRenderer tr = mc.textRenderer;
            int x = 4, y = 4, w = 220, p = 3;

            ctx.fill(x, y, x + w, y + 52, 0xC01a1a2e);
            int cy = y + p;
            ctx.drawText(tr, "EnderCore AI", x + p, cy, 0x9d5cff, true);
            cy += 12;
            ctx.drawText(tr, "Status: " + status, x + p, cy, 0x00ff00, true);
            cy += 11;
            String t = thought; if (t.length() > 30) t = t.substring(0, 30) + "...";
            ctx.drawText(tr, "Think: " + t, x + p, cy, 0xaaaaaa, true);
            cy += 11;
            String a = action; if (a.length() > 30) a = a.substring(0, 30) + "...";
            ctx.drawText(tr, "Action: " + a, x + p, cy, 0xffcc00, true);
        });
    }

    public static void setThought(String t) { thought = t; lastUpdate = System.currentTimeMillis(); }
    public static void setAction(String a) { action = a; lastUpdate = System.currentTimeMillis(); }
    public static void setStatus(String s) { status = s; lastUpdate = System.currentTimeMillis(); }
}