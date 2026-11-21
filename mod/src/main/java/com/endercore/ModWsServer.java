package com.endercore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EnderCore WebSocket 服务端。
 * 全双工长连接：mod 主动推送游戏状态；Python 下行动作 / HUD 帧。
 * 动作执行仍通过 GameDataCollector.executeActionBlocking 切回主线程。
 *
 * 帧格式（JSON 文本）：
 *   mod -> python: {"type":"state","data":{...}}
 *                  {"type":"action_result","action":"...","success":true}
 *                  {"type":"pong"}
 *   python -> mod: {"type":"action","action":"...","params":{...}}
 *                  {"type":"hud","thought":"...","action":"...","status":"...","balance":"..."}
 *                  {"type":"chat","message":"..."}
 *                  {"type":"ping"}
 */
public class ModWsServer extends WebSocketServer {

    private final Set<WebSocket> conns = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ModWsServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        conns.add(conn);
        EnderCoreMod.LOGGER.info("WS 客户端已连接: {}", conn.getRemoteSocketAddress());
        // 上线即推一次当前状态
        JsonObject state = GameDataCollector.getGameStateBlocking();
        conn.send(wrap("state", state));
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        conns.remove(conn);
        EnderCoreMod.LOGGER.info("WS 客户端断开: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonObject req = JsonParser.parseString(message).getAsJsonObject();
            String type = req.has("type") ? req.get("type").getAsString() : "";

            switch (type) {
                case "action": {
                    String action = req.get("action").getAsString();
                    JsonObject params = req.has("params") && req.get("params").isJsonObject()
                            ? req.getAsJsonObject("params") : new JsonObject();
                    boolean ok = GameDataCollector.executeActionBlocking(action, params);
                    JsonObject res = new JsonObject();
                    res.addProperty("type", "action_result");
                    res.addProperty("action", action);
                    res.addProperty("success", ok);
                    conn.send(res.toString());
                    break;
                }
                case "hud": {
                    if (req.has("thought")) {
                        String t = req.get("thought").getAsString();
                        if (!t.isEmpty()) AiHud.sendChat("§b[EnderCore]§7 思考: " + t);
                    }
                    if (req.has("action")) AiHud.setAction(req.get("action").getAsString());
                    if (req.has("status")) AiHud.setStatus(req.get("status").getAsString());
                    if (req.has("balance")) AiHud.setBalance(req.get("balance").getAsString());
                    break;
                }
                case "chat": {
                    if (req.has("message")) AiHud.sendChat(req.get("message").getAsString());
                    break;
                }
                case "ping": {
                    JsonObject pong = new JsonObject();
                    pong.addProperty("type", "pong");
                    conn.send(pong.toString());
                    break;
                }
                default:
                    break;
            }
        } catch (Exception e) {
            EnderCoreMod.LOGGER.warn("WS 消息处理失败: {}", e.toString());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        EnderCoreMod.LOGGER.warn("WS 错误: {}", ex.toString());
    }

    @Override
    public void onStart() {
        EnderCoreMod.LOGGER.info("WebSocket 服务端已启动, 端口 {}", getPort());
    }

    /** 向所有已连接客户端广播状态（在主线程调用，data 必须是主线程采集的结果）。 */
    public void broadcastState(JsonObject state) {
        if (conns.isEmpty()) return;
        String msg = wrap("state", state);
        for (WebSocket c : conns) {
            try {
                c.send(msg);
            } catch (Exception ignored) {
            }
        }
    }

    public boolean hasClients() {
        return !conns.isEmpty();
    }

    private static String wrap(String type, JsonObject data) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.add("data", data);
        return o.toString();
    }
}
