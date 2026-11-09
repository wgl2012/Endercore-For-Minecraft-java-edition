package com.endercore;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EnderCore Mod 客户端主类
 */
public class EnderCoreMod implements ClientModInitializer {
    public static final String MOD_ID = "endercore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ModApiServer apiServer;

    private static KeyBinding goalKey;
    private static KeyBinding freeMouseKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("EnderCore Mod 正在启动...");

        Config.init();
        ActionRegistry.reload();
        EnderCoreCommands.init();
        PlayerController.init();
        AiHud.init();

        // 注册按键
        goalKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.endercore.set_goal", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.endercore"));
        freeMouseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.endercore.free_mouse", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.endercore"));

        // 每 tick 检查按键
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (goalKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new GoalScreen());
                }
            }
            while (freeMouseKey.wasPressed()) {
                if (client.player == null) return;
                if (client.currentScreen instanceof FreeMouseScreen) {
                    client.setScreen(null);
                } else if (client.currentScreen == null) {
                    client.setScreen(new FreeMouseScreen());
                }
            }
        });

        // 启动 HTTP 服务器
        Config.Data cfg = Config.get();
        apiServer = new ModApiServer(cfg.http_host, cfg.http_port);
        try {
            apiServer.start();
            LOGGER.info("EnderCore HTTP 服务器已启动: http://{}:{}", cfg.http_host, cfg.http_port);
        } catch (Exception e) {
            LOGGER.error("HTTP 服务器启动失败", e);
        }

        LOGGER.info("EnderCore Mod 加载完成！");
    }
}
