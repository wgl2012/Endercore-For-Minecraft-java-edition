package com.endercore;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.mojang.brigadier.context.CommandContext;

/**
 * /endercore 命令
 */
public class EnderCoreCommands {
    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("endercore")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("gui")
                    .executes(EnderCoreCommands::openGui))
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("reload")
                    .executes(EnderCoreCommands::reload))
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("status")
                    .executes(EnderCoreCommands::status))
            );
        });
    }

    private static int openGui(CommandContext<FabricClientCommandSource> ctx) {
        net.minecraft.client.MinecraftClient.getInstance().send(() -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new ConfigScreen(null));
        });
        return 1;
    }

    private static int reload(CommandContext<FabricClientCommandSource> ctx) {
        Config.load();
        ActionRegistry.reload();
        int count = 0;
        // 统计加载了多少个动作
        try {
            java.lang.reflect.Field f = ActionRegistry.class.getDeclaredField("customActions");
            f.setAccessible(true);
            count = ((java.util.Map<?,?>) f.get(null)).size();
        } catch (Exception ignored) {}
        ctx.getSource().sendFeedback(net.minecraft.text.Text.literal(
            "§a[EnderCore] 配置+动作模块已重载 (加载了 " + count + " 个自定义动作)"));
        return 1;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        Config.Data c = Config.get();
        ctx.getSource().sendFeedback(net.minecraft.text.Text.literal(
            "§a[EnderCore] HTTP: " + c.http_host + ":" + c.http_port + "  动作目录: " + Config.getModelsDir().getAbsolutePath()));
        return 1;
    }
}
