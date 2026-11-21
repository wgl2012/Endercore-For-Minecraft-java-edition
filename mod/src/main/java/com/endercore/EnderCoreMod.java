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
    public static ModWsServer wsServer;

    private static KeyBinding goalKey;
    private static KeyBinding freeMouseKey;
    private static KeyBinding configKey;
    private static int wsTickCounter = 0;

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
                "key.endercore.free_mouse", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.endercore"));
        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.endercore.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.endercore"));

        // 每 tick 检查按键 + 定时通过 WS 广播状态
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (goalKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new GoalScreen());
                }
            }
            while (configKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new ConfigScreen(null));
                }
            }
            while (freeMouseKey.wasPressed()) {
                if (client.player == null) return;
                HostMode.toggle(client);
            }

            // 托管模式保活：维持光标解锁状态
            HostMode.tick(client);

            // 每 10 tick（约 0.5 秒）向 WS 客户端推送一次状态；无客户端时直接跳过
            if (wsServer != null && wsServer.hasClients()) {
                wsTickCounter++;
                if (wsTickCounter >= 10) {
                    wsTickCounter = 0;
                    wsServer.broadcastState(GameDataCollector.getGameState());
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

        // 启动 WebSocket 服务端（主传输，HTTP 作为兜底）
        try {
            wsServer = new ModWsServer(cfg.ws_port);
            wsServer.start();
            LOGGER.info("EnderCore WebSocket 服务端已启动, 端口 {}", cfg.ws_port);
        } catch (Exception e) {
            LOGGER.error("WebSocket 服务端启动失败", e);
        }

        LOGGER.info("EnderCore Mod 加载完成！");
    }
}
